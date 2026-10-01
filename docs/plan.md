# Remaining work

Zalo **26.08.01** (`260801903`) roadmap. Keep APKs and evidence in ignored
`analysis/zalo/26.08.01/`. Cross-app engineering belongs in [maintenance](maintenance.md);
release execution and evidence requirements belong in [validation](validation.md).

## Feature feasibility investigations

These are **not implemented behavior or release expectations**. Before implementation,
record each candidate using the [target-evidence record](target-evidence-template.md)
in ignored local notes. Include exact smali gate, callers/consumers, local data flow,
server dependencies, narrow change, uniqueness rationale, and regression risks. Use
these statuses: **uninvestigated**, **ready to implement**, **needs runtime proof**,
**server-dependent**, or **rejected**. Every active investigation should name one
concrete next experiment and positive, negative/control, and regression checks. Use
patch-time fingerprints and app-specific extensions, not broad runtime plumbing or
remote symbol catalogs.

| Candidate | Evidence required / boundary |
| --- | --- |
| zBusiness catalog | Entry points, creation/editing, limits, storage/sync, backend authorization, restart persistence, and recipient-visible sharing. |
| Gold Business badge | Asset and entitlement/display path; local cosmetics are not server-visible verification. |
| Change username | Distinguish display name, unique handle, and business link; trace validation, cooldowns, requests, persistence, and visibility from another account. A local alias is not a server rename. |
| Username friend search / additional devices | Removed from shipped patches; require compatible fingerprints and independent runtime/server evidence before reconsidering. |
| Inactivity deletion | Verify policy and activity definition; server-managed deletion is not a local unlock. No silent generated activity; reminders/backups are separate mitigations. |
| Muted-chat count / asymmetric online-seen privacy | Server-dependent until a client gate is demonstrated. |

### Local backup/export

Trace native transfer/export first: messages, media associations, snapshots/WAL,
schema, keys, and restore ordering. Prove stock-to-patched migration, patched
reinstall, and cross-device restore separately; private-file access does not prove portability.
Export only to a user-selected external destination. Test unauthorized access,
unexpected destinations, and excessive URI grants (also for notification-history
and recording exports). Require integrity/version checks, bounded extraction,
recoverable staging, and corrupt-archive, low-space, and interrupted-transfer tests.
See [local-data investigation rules](reverse-engineering.md#learning-from-other-patch-projects).

## Remaining Zalo investigations

### P1: Configurable native backup interval — implemented, qualification pending

**Status:** needs runtime proof. **Next experiment:** qualify one complete scheduled
backup/restore round trip on a disposable test account, with stock and no-patch
controls. The opt-in patch overrides Zalo's native scheduling interval to 1, 3, 6,
or 12 hours while preserving native opt-in, account, network, and backup guards.
Prove restored messages and media (positive), disabled opt-in/no eligible network
(control), and battery, wakeups, network, and account-switching behavior (regression)
before treating behavior as qualified. Timer execution alone is insufficient.

### P2: Inbox and navigation controls

Re-hunt 26.08.01 anchors for chat/group/OA/stranger categories and initial filter;
preserve datasets, unread counts, refresh, pagination, search, and unknown categories.
For main-tab, Me, media-box, and banners, prove promotional-only scope; preserve
alerts and backup access. Prefer exact IDs/scoped binding changes and test localization,
accessibility, badges, deep links, and resume.

### P2: Open ordinary content links externally

**Status:** uninvestigated. **Next experiment:** trace the pinned version's URL
dispatch from a chat/feed link through its final handler and record the smali-backed
branch before proposing a change. Allow only validated HTTP(S) content links.
Positive check: ordinary content URL opens externally; negative/control checks:
malformed URLs and non-web/custom schemes remain on their native handlers.
Regression checks: mini-app, OA H5, authenticated, payment, OAuth, redirects,
missing browser, cancellation, and safe in-app fallback.

### P2: Analytics and privacy candidates

The existing telemetry patch suppresses selected Room analytics writes,
Crashlytics diagnostics, and native crash-handler registration. Static inspection
of the pinned base DEX found Firebase Analytics SDK classes and collection flags,
but no app `FirebaseAnalytics.logEvent` callers in the inspected base DEX; feature
splits and runtime behavior were not qualified. Do not add a Firebase service
interception patch based only on SDK presence. Reopen this only if split/runtime
evidence demonstrates active measurement. Framework-level measurement-service
blocking is not supported by the current patch mechanism. Advertising-ID
reduction needs all app/SDK consumers checked while preserving unrelated
attribution/advertising. Compare search keypress/focus
telemetry with existing coverage; patch only gaps, preserving suggestions and
searches. Notification history and call recording remain separate default-off
investigations requiring consent, privacy, storage, and second-account tests.

Investigate ad-reporting coverage beyond the existing offline-tracking suppression.
**Status:** needs runtime proof. **Next experiment:** trace callers and payload
construction for `AdsTrackingReceiver` actions `HitUrls` and `SubmitBatch`, then
confirm an ad-specific path without suppressing ordinary analytics. The pinned base DEX contains those actions, and Adtima code has explicit impression/click tracking paths.
The current patch set does not yet fingerprint these reporting paths. Positive
check: demonstrate the intended ad-reporting path; negative/control checks: ordinary
analytics and non-ad diagnostics still operate; regressions: app functionality and
network-dependent content remain intact. Trace callers, payload scope, and ordinary
analytics consumers; suppress only proven ad-specific events. Static anchors are
leads, not behavior validation.

### P2: Ad rendering and feed filtering

Investigate ZInstant placements across the Timeline, Messages tab, Story viewer,
and Zalo Video. The existing patches cover selected ad configuration, request,
and feed-binding paths, but do not establish complete rendering coverage. For
pre-parse feed filtering, prove the ad markers cannot occur on ordinary posts and
preserve pagination, item counts, and mixed media. Verify removing a Messages-tab
row does not corrupt unread counts, adapter positions, or normal chat navigation.
Treat video-request suppression as risky: prove playback continues or fails safely
before considering it enabled by default.

### P2: Content and playback controls

Trace content-WebView autoplay separately from native Timeline/Video players;
preserve tap-to-play, calls, and explicit previews. Establish local player support
before adding playback controls; preserve default speed, seeking, audio sync, and
lifecycle. Initially exclude calls and live playback.

### P3: Security and appearance

- **Passcode grace period:** trace preference/lifecycle; any default-off option needs
  a warning and must preserve authentication and expiry.
- **Capture:** establish restrictions and outbound events separately. Default-off,
  sensitive-content warning, second-account proof; never globally clear security flags.
- **Bubbles:** establish native support; preserve Android API, permission, and resource
  requirements. Never spoof unsupported-device eligibility.
- **True-black theme:** use verified resources/runtime colors, scoped to dark mode
  and default-off. Test light mode, contrast, dialogs, system bars, and switching.

## Boundaries

- zStyle and Video Original quality are excluded: the pinned APK has `VIDEO` and
  `VIDEO_HD`, not `VIDEO_ORIGINAL`.
- Forced-update suppression needs a traced trigger chain. The permission audit
  found no safe zero-usage removal candidates.
- Catalog, username changes, badge entitlement, local export, inactivity prevention,
  zCloud capacity, and business quotas remain unproven or server-dependent.
- No HTTP-header OAuth fixes, global paid-account spoofing, broad MicroG rewrites,
  unrelated app ports, or generic shared runtime infrastructure. Pairip/native/Hermes
  infrastructure requires a demonstrated consumer.

## Acceptance evidence

Follow [validation](validation.md): positive and control checks, remote visibility
or restore round trips where relevant, then repeat build/repatch/install/device
checks with the published `.mpp`. Keep hashes, patch selection, device/Android,
signing identity, and sanitized evidence outside Git. Unexecuted candidates are
not validated features.
