package com.zeldrisho.patches.threads.ads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.zeldrisho.patches.threads.shared.Constants.COMPATIBILITY_THREADS

/**
 * Hides sponsored posts from the Threads feed.
 *
 * Approach (issue #5): the previous implementation forced `Media.DED() -> false`,
 * which only stripped the "Ad"/"Sponsored" chrome while leaving the ad post in the
 * feed (the reporter's exact symptom: "labels disappeared but ads still show").
 * On-device probing (434.0.0.41.74 / versionCode 510406926) showed the main feed
 * has no ad-specific construction hook — ads are ordinary feed units whose ONLY ad
 * signal is `Media.DED()` (434) / `Media.DGK()` (445, same inner constants:
 * wrapper 0x775627d1, E7l(-0x79965650), CC6(0x10e895f0) non-null — renames of
 * E3k/C7J), consulted thousands of times per feed scroll, and every
 * fetched list funnels through the BarcelonaFeedCache merge method (A0F on 434,
 * A0G on 445 — same param shape, .locals 37).
 *
 * So instead of relabeling, we drop ad-flagged units from the list BEFORE it merges
 * into the visible feed: `FeedAdFilter.filterAds()` (companion extension) inspects
 * each unit via reflection (media `A05()/DED()/DGK()`, or thread-carried items
 * `A02() -> Ckh()/Cnd() -> CDh()/CIV() -> DED()/DGK()`), and this patch replaces the feed-list
 * parameter with the filtered result at the top of the merge method.
 *
 * Notes:
 *  - Feed-scoped: sponsored units in other surfaces (clips/reels/stories) are
 *    unaffected.
 *  - `DED()/DGK()` is left natural so the filter can see real ads.
 *  - Any reflection mismatch degrades to a no-op (no crash), so an app update
 *    worst-case brings ads back instead of breaking the feed.
 */
@Suppress("unused")
val hideAdsPatch = bytecodePatch(
    name = "Hide ads",
    description = "Removes sponsored posts from the Threads feed by filtering ad feed units " +
        "(detected via Media.DED/DGK) out of the list merged into the feed cache, before they can " +
        "render. Feed-scoped; other surfaces (clips/reels) are not affected.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_THREADS)
    // Coupling note: this resource path must match extensionMpeResourcePath in
    // patches/build.gradle.kts (Kotlin patch code and the Gradle build script
    // cannot share a constant across that boundary — change both together).
    extendWith("extensions/extension.mpe")

    execute {
        validateFeedReflectionContract { classDefByOrNull(it) }
        val method = FeedMergeMethod.matchAll(1..1).single().method
        val helpers = MediaAdPredicateHelper.matchAll()
        check(helpers.size == 1) {
            "Threads Media ad-predicate helper fingerprint matched ${helpers.size} methods; expected exactly one"
        }
        val helper = helpers.single().originalMethod
        val predicates = mediaAdPredicate(helper).matchAll()
        check(predicates.size == 1) {
            "Threads Media ad-predicate fingerprint matched ${predicates.size} methods; expected exactly one"
        }
        val predicate = predicates.single().originalMethod
        val anchorMatches = FeedContentAccessor.matchAll()
        check(anchorMatches.size == 1) {
            "Threads feed-wrapper anchor fingerprint matched ${anchorMatches.size} methods; expected exactly one"
        }
        val anchor = anchorMatches.single().originalMethod
        val mediaAccessors = feedWrapperAccessor(anchor, "Lcom/instagram/feed/media/Media;").matchAll()
        check(mediaAccessors.size == 1) {
            "Threads feed media accessor fingerprint matched ${mediaAccessors.size} methods; expected exactly one"
        }
        val threadAccessors = feedWrapperAccessor(anchor, "Lcom/instagram/api/schemas/ThreadIntf;").matchAll()
        check(threadAccessors.size == 1) {
            "Threads feed thread accessor fingerprint matched ${threadAccessors.size} methods; expected exactly one"
        }
        injectFeedAdFilter(method, predicate.name, mediaAccessors.single().originalMethod.name, threadAccessors.single().originalMethod.name)
    }
}

/** Injects the production hook; the caller must first validate the target and reflection ABI. */
internal fun injectFeedAdFilter(
    method: MutableMethod,
    mediaPredicateName: String = "",
    mediaAccessorName: String = "",
    threadAccessorName: String = "",
) {
    val impl = method.implementation
        ?: error("BarcelonaFeedCache merge method has no implementation")
    // A0F (434) / A0G (445): (this, LX/obf, Integer, String, String, List, LX/obf, Function3, Z):
    // 9 params including `this`; the feed list is param index 5 (p5).
    val listReg = feedListRegister(impl.registerCount)
    val loadMove = feedListLoadMove(listReg)
    val storeMove = feedListStoreMove(listReg)
    method.addInstructions(
        0,
        """
            $loadMove
            const-string v1, "$mediaPredicateName"
            const-string v2, "$mediaAccessorName"
            const-string v3, "$threadAccessorName"
            invoke-static {v0, v1, v2, v3}, Lcom/zeldrisho/threads/extension/FeedAdFilter;->filterAds(Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/List;
            move-result-object v0
            $storeMove
        """,
    )
}
