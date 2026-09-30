package com.zeldrisho.patches.threads.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test

/** Opt-in real-APKM runner for the exact committed production fingerprints. */
class ThreadsFingerprintApkmHarnessTest {
    @get:Rule val temp = TemporaryFolder()

    private fun context(version: String, code: String): BytecodePatchContext {
        val config = PatcherConfig(apkFile = temp.newFile("input-$version.apk"), temporaryFilesPath = temp.newFolder())
        val metadata = PackageMetadata::class.java.constructors.single().newInstance(
            "com.instagram.barcelona", version, code, null,
        )
        return BytecodePatchContext::class.java
            .getConstructor(PatcherConfig::class.java, PackageMetadata::class.java)
            .newInstance(config, metadata)
    }

    private fun classes(apkm: File): List<ClassDef> {
        val result = mutableListOf<ClassDef>()
        ZipFile(apkm).use { bundle ->
            val entries = bundle.entries().asSequence().filter { it.name.endsWith(".apk") }.toList()
            for ((index, entry) in entries.withIndex()) {
                val apk = temp.newFile("split-${apkm.name.hashCode()}-$index.apk")
                bundle.getInputStream(entry).use { input -> apk.outputStream().use(input::copyTo) }
                val dex = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault())
                dex.dexEntryNames.forEach { dexName ->
                    result += dex.getEntry(dexName)!!.dexFile.classes
                }
            }
        }
        return result
    }

    private fun scan(classes: List<ClassDef>, fingerprint: Fingerprint, patchContext: BytecodePatchContext) = buildList {
        with(patchContext) {
            classes.forEach { owner ->
                fingerprint.clearMatch()
                addAll(fingerprint.matchAll(owner, 0..Int.MAX_VALUE))
            }
        }
    }

    private fun simpleThreadAccessor(anchor: MethodReference) = Fingerprint(
        definingClass = anchor.definingClass,
        returnType = "L",
        parameters = emptyList(),
        custom = { candidate, _ ->
            val method = candidate as Method
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            val callsAnchor = instructions.filterIsInstance<ReferenceInstruction>().any { instruction ->
                (instruction.reference as? MethodReference)?.let {
                    it.definingClass == anchor.definingClass && it.name == anchor.name &&
                        it.parameterTypes == anchor.parameterTypes && it.returnType == anchor.returnType
                } == true
            }
            val castsThreadIntf = instructions.any { it.opcode == Opcode.CHECK_CAST } &&
                instructions.filterIsInstance<ReferenceInstruction>().any { instruction ->
                    (instruction.reference as? FieldReference)?.type?.endsWith("/ThreadIntf;") == true
                }
            callsAnchor && castsThreadIntf
        },
    )

    private fun matchCounts(apkm: File, version: String, code: String): Map<String, Int> {
        val all = classes(apkm)
        val patchContext = context(version, code)
        with(patchContext) {
            FeedMergeMethod.clearMatch()
            MediaAdPredicateHelper.clearMatch()
            FeedContentAccessor.clearMatch()
            val helper = scan(all, MediaAdPredicateHelper, patchContext)
            val mediaPredicate = if (helper.size == 1) {
                val ref = helper.single().originalMethod
                scan(all, mediaAdPredicate(ref), patchContext)
            } else emptyList()
            if (version == "434.0.0.41.74") {
                println("434 Media predicate candidates: " + mediaPredicate.joinToString { "${it.originalMethod.definingClass}->${it.originalMethod.name}()${it.originalMethod.returnType}" })
            }
            val anchors = scan(all, FeedContentAccessor, patchContext)
            val mediaAccessors = if (anchors.size == 1) {
                scan(all, feedWrapperAccessor(anchors.single().originalMethod, "Lcom/instagram/feed/media/Media;"), patchContext)
            } else emptyList()
            val threadAccessors = if (anchors.size == 1) {
                scan(all, feedWrapperAccessor(anchors.single().originalMethod, "Lcom/instagram/api/schemas/ThreadIntf;"), patchContext)
            } else emptyList()
            val simpleThreadAccessors = if (anchors.size == 1) {
                scan(all, simpleThreadAccessor(anchors.single().originalMethod), patchContext)
            } else emptyList()
            return linkedMapOf(
                "helper" to helper.size,
                "Media predicate" to mediaPredicate.size,
                "feedContent anchor" to anchors.size,
                "Media accessor" to mediaAccessors.size,
                "thread accessor (committed)" to threadAccessors.size,
                "thread accessor (ThreadIntf role)" to simpleThreadAccessors.size,
            )
        }
    }

    @Test fun reportRealApkmFingerprintCounts() {
        val builds = listOf(
            Triple("434", "434.0.0.41.74", "510406926"),
            Triple("445", "445.0.0.46.83", "511507647"),
            Triple("449", "449.0.0.54.82", "511908382"),
        ).mapNotNull { (label, version, code) ->
            val path = System.getenv("THREADS_APKM_$label")
                ?: System.getProperty("THREADS_APKM_$label")
            if (path.isNullOrBlank() || !File(path).isFile) null else Triple(label, version, code to File(path))
        }
        assumeTrue("Set THREADS_APKM_434/445/449 or matching system properties to available original APKMs", builds.isNotEmpty())
        val results = builds.associate { (label, version, pair) ->
            label to matchCounts(pair.second, version, pair.first)
        }
        println("fingerprint\\build\t" + results.keys.joinToString("\t"))
        listOf("helper", "Media predicate", "feedContent anchor", "Media accessor", "thread accessor (committed)", "thread accessor (ThreadIntf role)").forEach { fingerprint ->
            println(fingerprint + "\t" + results.values.joinToString("\t") { it.getValue(fingerprint).toString() })
        }
    }
}
