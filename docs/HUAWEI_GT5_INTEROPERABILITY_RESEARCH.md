# Huawei GT 5 interoperability research — 2026-11-27

Scope: native ALKE integration. Includes the v636 hardware observations and
the v637 pairing implementation. The v637 results need GT 5 verification.

## Reference hierarchy

- [Gadgetbridge Huawei device list](https://gadgetbridge.org/gadgets/wearables/huawei-honor/#huawei-watch-gt-5):
  GT 5 is mostly supported and listed as capable of initial pairing without
  the vendor app. Upstream device support PR #4155. Use its protocol
  behavior as the GT 5 reference; do not assume every sensor is supported.
- [Gadgetbridge Huawei pairing](https://gadgetbridge.org/basics/pairing/huawei-honor-pairing/):
  existing-vendor binding and initial pairing are different cases. Owner
  has approved a manual factory reset if necessary; never issue one from ALKE.
- [JoyoFit protocol observations](https://github.com/dzulfikar08/joyofit/blob/master/protocol/README.md):
  independent implementation environment but derived from Gadgetbridge.
  Observations on Watch Fit 4/4 Pro are comparison evidence, not GT 5 traces.
  Classic RFCOMM is preferred for first bind. A documented BLE first-bind
  attempt on Android 16 lost an oversized GATT notification. Do not assume
  that BLE is the safer fallback for first binding.
- [OpenHarmony device authentication](https://github.com/openharmony/security_device_auth):
  Apache-2.0 root license verified. Reference for trust establishment,
  mutual authentication, and key exchange; exact compatibility with the
  GT 5 HiChain wire protocol has not been established. Check individual
  file notices and dependencies before using code.
- [Huawei Watch Sideload iOS](https://github.com/Burak4Arslan/huawei-watch-sideload-ios)
  and [Vitaloop](https://github.com/monkey-wenjun/vitaloop-app):
  AGPL-derived alternative language implementations, useful for checking
  interpretation of protocol facts, not independent GT 5 validation.
- [Huawei official pairing help](https://consumer.huawei.com/sg/support/content/en-gb15907634/):
  user-facing confirmation and discoverability reference, not a native
  authentication protocol specification.

## Implementation checkpoints

1. Verify v637 on the GT 5. The v636 RFCOMM test reached LinkParams, DeviceStatus,
   security negotiation and PinCode with HiChain3 variant 5.
2. Verify first-time bind and reconnect key exchange against public protocol
   evidence; use standard cryptographic primitives and independent tests.
   Never accept a key or peer proof merely because a packet CRC is valid.
   Keep session credentials out of diagnostic logs and cloud backups.
3. Require successful authenticated battery/product read before reporting
   Huawei connected. Fail closed on unsupported variants and invalid proof.
4. Implement capability-driven, source-scoped sensor synchronization and
   approved source priority. Verify actual device-supported metrics; never
   fabricate HRV/RR, sleep, GPS or SpO2 if unavailable.
5. Verify that new pairing enables the intended sensor recording settings:
   [Huawei/Honor device notes](https://gadgetbridge.org/basics/topics/huawei-honor/).
   A successful sync with no HR/sleep records may reflect recording disabled,
   not a decoder bug. Do not silently change unrelated settings.
6. Complete dual-device UI, regression, and release gating under
   HUAWEI_GT5_NATIVE_ACCEPTANCE.md.

## v636 first-bind failure and v637 changes

The GT 5 test reported one outgoing 695-byte frame and one incoming 50-byte
frame while waiting for the first HiChain reply. The incoming frame carried
service `25`, command `03` and a 42-byte payload. The watch showed no pairing
prompt before the socket read failed. This command is an unsolicited music
control event. [Gadgetbridge MusicControl](https://github.com/Freeyourgadget/Gadgetbridge/blob/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/huawei/packets/MusicControl.java#L128-L150)

The first HiChain frame needs 671 bytes for its JSON TLV. v636 wrote its
length as `9F 05`, which the Huawei big-endian base-128 decoder interprets as
3,973 bytes. The correct wire bytes are `85 1F`. Single-byte lengths in the
earlier requests were unaffected. The shared codec now handles extended
lengths for transmitted requests and incoming opaque or encrypted TLVs.
The 695-byte frame fits the negotiated Classic MTU and LPv2 slice budget.
[Gadgetbridge length codec](https://github.com/Freeyourgadget/Gadgetbridge/blob/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/huawei/HuaweiTLV.java#L422-L450)
[JoyoFit length test](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/modules/huawei-wear/ios/HuaweiProtocol/Tests/HuaweiProtocolTests/PacketsRealWireTests.swift#L103-L107)

JoyoFit's physical Fit 4 Classic run confirms the sequence PinCode, ISO
operation 1, ISO operation 2, then encrypted device reads. Its simulator
models type `02` terminal messages with a zero request ID. v637 accepts that
sentinel only for a terminal message, preserves proof and MAC checks, sends
the reconnect result as message `13` hexadecimal, and uses 16-byte application
GCM IVs. ISO exchange nonces stay 12 bytes. OpenHarmony corroborates the
cryptographic inputs and derivation labels for the ISO exchange.
[JoyoFit Classic observation](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/protocol/README.md#L1228-L1242)
[JoyoFit terminal simulation](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/modules/huawei-wear/ios/HuaweiProtocol/Tests/HuaweiProtocolTests/PacketsAuthFakeWatch.swift#L368-L382)
[Gadgetbridge session IV](https://github.com/Freeyourgadget/Gadgetbridge/blob/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/huawei/HuaweiPacket.java#L144-L154)
[OpenHarmony ISO derivation](https://github.com/openharmony/security_device_auth/blob/bcbec44ad9774a573146316a074ed8132b12f878/services/legacy/authenticators/src/account_unrelated/iso_task/iso_task_common/iso_task_common.c#L25-L73)

After decrypting the operation 1 token, v637 saves the candidate and client
identity in a separate Keystore-encrypted pending slot before sending the
operation 1 result. A later reconnect can resume an interrupted verification.
The candidate becomes a verified binding only after operation 2 and both
GCM reads. The saved-binding action uses proof-only reconnect with either
HiChain3 variant. Variant 5 can trigger a new bind after explicit consent.
The Kotlin watch simulation exercises first bind,
saved reconnect and interrupted readback recovery. Physical verification
needs an owner-confirmed first bind, matching battery readback, and a
saved-binding reconnect after closing and reopening ALKE.

## Post-authentication initialization and health sync audit

ALKE currently sends ProductInfo (`01/07`) and BatteryLevel (`01/08`) under
the candidate reconnect key and verifies both GCM replies before storing a
new binding. The watch session closes after readback. The SupportedServices
(`01/02`) codec maps reply flags to the corresponding requested IDs and
remains unused by the pairing flow. Later capability discovery, persistent
connection state, and sensor sync require an authenticated session that can
route unsolicited traffic independently of the pending request.

- Gadgetbridge and JoyoFit initialize with product, time, battery, services,
  and per-service commands, then query optional `01/37` ExpandCapability and
  `01/31` SettingRelated. Those results determine the later protocol paths.
  Configuration commands for connection status, wear status, time, account,
  and sensor settings have separate purposes and support gates. Vitaloop
  reports GT 5 testing and notes that its advanced P2P dictionary path needs
  account and agreement requests. Which initialization steps ALKE needs for
  its vendor-free bind remains a question for GT 5 verification.
  [Gadgetbridge initialization](https://github.com/Freeyourgadget/Gadgetbridge/blob/master/app/src/main/java/nodomain/freeyourgadget/gadgetbridge/service/devices/huawei/HuaweiSupportProvider.java#L624-L666)
  [JoyoFit initialization](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/modules/huawei-wear/android/src/main/java/com/joyodigitama/huaweiwear/HuaweiInitializer.kt#L71-L225)
  [Vitaloop GT 5 target](https://github.com/monkey-wenjun/vitaloop-app/blob/c35e1105dc9922b3f55f130edc466281712069d3/README.md#L95-L101)
  [Vitaloop settings and P2P notes](https://github.com/monkey-wenjun/vitaloop-app/blob/c35e1105dc9922b3f55f130edc466281712069d3/android/app/src/main/kotlin/com/awen/running/huawei/VitaloopWatchManager.kt#L2498-L2514)
- FitnessData `07/0A` and `07/0B` count and page timestamped activity
  records that may include steps, heart rate, SpO₂, and resting heart rate.
  `07/03` returns today's totals. A factory-reset watch may require supported
  recording settings for TruSleep (`07/16`), automatic heart rate (`07/17`),
  or automatic SpO₂ (`07/24`) to be enabled by the user. Verify recording,
  field availability, and units on the GT 5 before interpreting empty history.
  [JoyoFit commands and recording settings](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/protocol/README.md#L58-L65)
- Sleep uses `07/0C` and `07/0D` for legacy frames. TruSleep can additionally
  use dictionary class 700013 in `sequence_data` over `2C`, gated by
  ExpandCapability 143, or state/data files over `2C` or `0A`, selected by
  SettingRelated. Legacy sensor windows can lack measured sleep stages.
  P2P HRV and stress-derived RMSSD are separate reported summaries. The
  documented `19/05` stream carries spot-measurement samples. GT 5 support
  for compatible historical beat intervals remains unverified.
  [JoyoFit sleep paths](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/modules/huawei-wear/android/src/main/java/com/joyodigitama/huaweiwear/SleepSync.kt#L19-L36)
  [JoyoFit spot RR protocol](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/protocol/README.md#L86-L87)
  [JoyoFit HRV sources](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/protocol/README.md#L228-L236)
- Workout service `17` uses `07` to list workouts, `08` for totals, `0A` for
  samples, and separate commands for pace, segments, and SpO₂. The optional
  GPS track uses a file download over `2C` or `0A`. A workout's watch counter
  can be reused after reset. Preserve device provenance and timestamps for
  deduplication, and keep a failed section distinguishable from a missing
  workout. [JoyoFit workouts and GPS](https://github.com/dzulfikar08/joyofit/blob/388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e/protocol/README.md#L256-L278)

ALKE's source selector accepts aligned, validated observations and has no
runtime ingestion yet. Huawei step increments require interval alignment
before comparison with WHOOP's cumulative counter deltas. Huawei SpO₂
percentages require representation distinct from WHOOP's raw optical ADC
channels. Store per-device samples and incremental cursors before combining
coverage, and verify on-watch recordings, concurrent WHOOP operation, and
idempotent re-sync on hardware before enabling the policy.

## License constraint

ALKE root LICENSE retains PolyForm Noncommercial for original code. Do not
copy or port AGPL implementation code into this project under that license
without obtaining compatible permissions or resolving the licensing model.
Use documented wire facts and independently written code. OpenHarmony's
Apache-2.0 license may permit reuse of relevant components subject to its
conditions; it is not evidence of drop-in compatibility.

## v637 physical readback and v638 candidate

The owner reported encrypted readback failure with two outgoing frames (100 bytes), 425 incoming bytes and no transport error. The watch displayed Pairing failed. The serialized implementation sends BatteryLevel only after authenticating and parsing ProductInfo, so the trace narrows the failure to BatteryLevel handling or later readback. The exact battery response shape was not captured. v638 handles documented first-byte/multi-level battery fields and success status, preserves GCM authentication and adds tag/length-only readback diagnostics. After both encrypted reads, it queries support for DeviceConfig ConnectStatus (01/03 then 01/35) and sends the documented connected-state request only when advertised, requiring an authenticated success acknowledgement. This is a targeted finalization candidate, not evidence that all GT 5 initialization is complete; account, time, setup and sensor settings still require hardware evidence. No health sync or production merge is authorized by these simulated checks.

## v638 physical evidence and v639 mixed readback

GT5 returned a valid 11-byte BatteryLevel frame, plaintext TLV 01:1, after successfully processing GCM ProductInfo. v638 rejected it because it required an encrypted envelope for every response. Gadgetbridge BatteryLevel.Response explicitly permits firmware-specific plaintext responses. v639 accepts only known command-scoped cleartext schemas after successful GCM ProductInfo, never falls back from malformed or unauthentic ciphertext, and reports whether the battery and ConnectStatus acknowledgements were encrypted. Adds the reference initialization order ProductInfo -> clock -> battery -> services -> commands -> ConnectStatus with independent wire fixtures. Watch UI remains unverified. Additional capability-gated account judgment, agreement (requires owner consent), setup-device status, settings, sensor activation, and long-lived session/ingestion remain distinct work; they are not claimed completed by this patch. No reset, fabricated account, automatic acceptance of legal terms or WHOOP changes.

## v640 consolidated pairing lifecycle audit

Review covers the source chain from system Bluetooth bond through LPv2, HiChain, GCM proof, device initialization, local credential storage, routing, deadlines, UI and sync boundaries. Physical evidence reaches GCM ProductInfo and an 11-byte plaintext battery response; it does not establish successful watch setup.

- Authenticate: retain v637 canonical TLV encoding, cryptographic peer proof, result MAC, reconnect and GCM ProductInfo verification. No change to WHOOP or to scoring.
- Readback: v639 mixed response handling retained. The observed single-byte battery response is accepted only after GCM ProductInfo; no fallback from malformed ciphertext or failed GCM. Battery values remain command-scoped observations.
- Initialization: v640 requests relevant services, maps returned capability flags to requested IDs, discovers device configuration and account commands, conditionally queries expand and settings capabilities, sends vendor-free account judgment only when both account commands are advertised, requests ConnectStatus when advertised, sends one-way setup-device status and queries wear status when capability 109 is advertised. Empty account follows owner-approved vendor-free design; a different-account response or device user-selection request stops rather than confirms reset. Agreement command is recorded but never automatically accepted; no capability impersonation or vendor account invented.
- Transport: optional requests use bounded per-request deadlines without a second simultaneous Bluetooth read. Late replies for abandoned commands and known battery/music notifications are routed separately. Required proof and discovery failures remain failures.
- Lifecycle: diagnostic session holds the socket open for a bounded observation interval after successful readback and initialization, then closes. It is NOT a background connection manager. Pending credential remains locally encrypted; actual hardware verification, persistent authenticated session, sensor data ingestion and source integration are release blockers.
- Tests: simulated first bind, pending recovery, saved reconnect, mixed plaintext/GCM, account mismatch, advertised optional capability, optional timeout, late one-way reply, invalid proofs, invalid battery and invalid ciphertext. Simulation is not physical proof.
- Remaining limitations: a real device may require other capability-specific setup (e.g., user-approved agreements); exact GT 5 firmware responses and watch-screen completion must be verified. Existing health sync/source selection is not runtime-wired. Do not merge to main or publish as a production integration before acceptance criteria are met.

## v641 fitness session foundation

Independent wire codecs for service 07/03 totals and 07/0A, 07/0B minute count/page are based on Gadgetbridge FitnessData.java and GetStepDataCountRequest/GetStepDataRequest, and JoyoFit protocol/README. Capability discovery requests the three commands; session callback runs before key cleanup and uses the same serial socket. App-process-owned test connection retains the serialized socket and key, polls advertised fitness totals, and reads a bounded recent history window; results are reported only after GCM verification. Explicit disconnect closes the socket. No injection into WHOOP or ALKE scoring. Minute history reader bounds dates/pages, requires ordered pages and rejects conflicting duplicate timestamps. Missing values stay null. This is a foundation, not finished background synchronization or merged multi-device data. Foreground-service/process-restart recovery, durable health data storage and integration, legal agreement workflow if required, and physical validation remain blockers.

## v642 source staging and connection lifecycle

Authenticated service 07 fitness totals and minute records now enter a distinct app-private SQLite staging database, keyed by hashed device address and timestamp/day. A repeated minute fetch updates measured fields only; missing fields do not erase previous data; daily cumulative values replace earlier observations rather than being summed. Totals are stored only if the phone's local date does not change during the request. A read-only HR selection preview applies Huawei-first/WHOOP-fallback on exact aligned minute timestamps, without changing native WHOOP scoring or generating artificial 1 Hz samples. Session byte budgets reset per transaction; planned 30-minute key/session rotation and up to three bounded saved-binding retries after confirmed socket failure prevent indefinite key and transport accumulation. No rebind, account reset or legal acceptance during recovery. Independent simulated watch tests now exercise authenticated totals/count/page and reject plaintext health data. Storage remains local and outside automatic backup until explicit archive integration. Production ingestion, full metric consolidation, firmware verification, startup recovery and sensor/workout/sleep pathways remain incomplete; keep draft PR and do not issue final APK.

## v643 native history staging

Based on Gadgetbridge DeviceConfig SupportedServices/SupportedCommands, FitnessData.MessageData and GetSleepDataCountRequest/GetSleepDataRequest, Workout.WorkoutCount/WorkoutTotals and JoyoFit protocol/README. The discovery container is one 0x81 with consecutive service/command pairs; response bytes are positional 0/1 flags. Added bounded GCM-only 07/0C + 07/0D legacy sleep interval reads and 17/07 + 17/08 workout summary reads when both commands are advertised. Database schema v2 adds separate legacy_sleep and workout_summary tables without modifying prior minute/total rows. Native types and calorie raw values are retained, not relabelled into ALKE stages or scores. Workout identity includes start time and native number (which can be reused); missing summary fields do not overwrite earlier measured fields. Fake watch tests exercise both paths on the authenticated socket. This is not TruSleep file sync, detailed workout traces/GPS or production consolidation. No final APK until these and physical validation are addressed.

## v644 workout detail staging

Gadgetbridge Workout.WorkoutData and WorkoutPace document service 17 commands 0A and 0C. Added independent codec for 14-byte sample header, record-length/bitmap validation, timestamps, HR and native speed/cadence/altitude; raw pace split values retain vendor units. Matching workout and page numbers required. Cap aggregate requests at 256 pages per history scan; do not import a partial page series as complete. Database v3 adds sample and pace tables, preserving earlier rows with additive migrations. Sample identity includes device, workout start, native number and timestamp; pace split list replaced only after complete nonempty read. GPS file transfer, TruSleep, production consolidation, header dual-battery wiring and backup are still outstanding; this is not final hardware verification.

## v645 dual status and bounded recovery

The global header observes typed Huawei authenticated/sync/battery state independently of the existing active wearable. The normal single-device battery presentation remains unchanged until Huawei authenticates; a compact labelled pair then uses the same battery glyph. Huawei status is cleared on disconnect or session rotation, and battery readback refreshes every five minutes on the same authenticated socket (the known strictly shaped plaintext battery exception only). The global sync animation includes real Huawei read operations. No battery shortcut is added. Minute history also retrieves up to 23 older one-hour windows per connection after the initial recent hour; repeated reads upsert rather than sum. This is bounded rolling recovery, not a durable sync cursor. GPS/TruSleep file transfer, full scoring/source consolidation, production pairing UI and archive integration remain outstanding. The header is a draft implementation pending device/screen validation.

## v646 historical HR timeline integration

ALKE-style HeartRateTrendCard now reads the selected authenticated GT5's stored minute records and WHOOP's original bucket data. Valid aligned and completed Huawei minutes win; WHOOP minute aggregates fill uncovered windows. Only affected display buckets are recomputed (equal observed-minute weighting); unaffected WHOOP buckets are identical to their original result. Extrema of a Huawei reading represent its observed value, not a synthetic intra-minute peak. Missing/invalid Huawei records or unavailable storage preserve WHOOP. Selection persists across a disconnect/process restart and is updated only after verified authentication; historical device records are not merged across different Huawei identities. A revision signal after committed changed minutes refreshes the displayed timeline, including backfill. Label discloses Huawei priority/WHOOP fallback minute data. No modification to WHOOP raw storage, instantaneous live HR, RR or strain/recovery engines. Tests cover missing/invalid primary, overlap, gaps and exact unchanged WHOOP fallback. GPS, TruSleep, other metric consolidation, archive integration and release pairing UI are still incomplete. Hardware acceptance remains unverified.

## v647 coverage-safe Steps integration

Huawei minute step counts are separate from WHOOP cumulative motion ticks and WHOOP 4 daily gravity-derived estimates. The shared ALKE Steps historical display promotes a Huawei calendar day only if every minute in that day has a valid observed count (including explicit zero); missing minutes are not zero. DST day boundaries use local calendar dates. Current-day and incomplete-day readings retain the existing WHOOP/import/estimate resolver; no whole-day totals are summed or arbitrarily apportioned. Manual workout steps use Huawei only for exact minute-aligned windows with complete coverage. The native estimator, AI calibration, other scores and original storage are untouched. Queries use indexed aggregate counts, and history projection is bounded to the most recent 366 completed calendar days. Tests cover gaps, zero, duplicates, conflicting rows, misalignment and DST. Partial-day cross-device fusion remains unresolved for WHOOP 4; no claim of full consolidation, GPS, TruSleep or hardware acceptance.

## v648 pairing resilience audit

Compared Gadgetbridge DeviceConfig, GetConnectStatusRequest, SendSetUpDeviceStatusRequest, SetTimeRequest and JoyoFit HuaweiInitializer (388cbb867f3b3a3ce0a8fa65cb5740bbfbcb213e). The requested setup name and time encoding match the reference. Adopt the JoyoFit setup/wear-before-ConnectStatus order. Only explicit success status 0x000186a0 counts as ConnectStatus acknowledgement; other syntactically valid responses are reported as unconfirmed, not proof the watch UI completed pairing. A real battery timeout after GCM ProductInfo no longer blocks initialization; battery remains unknown and is not retried on that socket (late response ambiguity). Malformed/invalid encrypted responses and EOF remain fail-closed. Authenticated state is independent of battery availability. Saved/pending credentials permit bounded reconnect on transport write/read failure, EOF, timeout and watchdog expiration, including during initialization; never auto-repeat first bind, account selection, resets or agreements. Tests distinguish silent battery from disconnected link and missing success acknowledgement. Pairing hardware acceptance is still pending: watch UI completion and data sync must be verified together. No vendor agreement acceptance without explicit consent. No production merge or signed release yet.

## v649 regression gate: preserve approved Today surface

v648 Personal Quality Gate run 46118005197 compiled, built and validated the unsigned APK, but the complete 7,263-test suite reported one failure (27 skipped): PremiumV625DesignLockTest.approvedV624SurfacesRemainByteIdentical. Reviewed the exact Git blobs 23fa0a65baaec61083b81e0f6a03f0888fb80140 (approved v627 TodayScreen) and 56b345ed4044a7aa1cde96a440cd751ddd1b8433 (v646 TodayScreen). Exactly four reversible edits exist: source-state/revision declaration, reload key, ALKE-style timeline data routing with unchanged original fallback path, and source label. Applying their inverse yields the exact original bytes. The lock now pins the exact new blob AND independently reverses only those four reviewed changes to require the original approved hash. WorkoutsScreen, HrEventVisuals and Worker locks remain identical. No production UI or algorithm change in this patch. Do not sign before the cumulative regression gate passes; physical GT5 pairing remains unverified.

## v650 continuation and optional telemetry audit

Rechecked Gadgetbridge DeviceConfig.SupportedCommands, FitnessData.MessageData.SleepResponse, GetSleepDataRequest, GetSleepDataCountRequest, GetStepDataCountRequest, GetWorkoutCountRequest, GetWorkoutTotalsRequest and HuaweiSupportProvider. Legacy sleep container 83 tags 04/05 and 6-byte start/minutes are confirmed. Step and workout continuation commands are not necessarily advertised independently: a successful count/list authorizes bounded pages, and a fitness history capability permits a legacy sleep count probe. Do not require explicit continuation flags. Read-only authenticated non-success status is a typed optional rejection; disable only the rejected stream for the current session. Missing/invalid ciphertext, malformed replies, unexpected messages, link timeout and parser errors are not swallowed or downgraded. Tests exercise absent continuation flags and an explicit rejected sleep count followed by successful totals and workout reads. References establish protocol structure, not validation against this physical GT5. JoyoFit lists GT5 as experimental. TruSleep/GPS file transfer, sensor setup controls, partial-day Steps, backup and production lifecycle remain unresolved. Full cumulative CI required before signing.

## v651 optional workout detail rejection isolation

The v650 code review found an authenticated rejection of workout DATA or PACE could propagate out of workoutBundles and discard an otherwise valid summary. Catch only HuaweiFitnessRejected independently for the two optional streams; discard incomplete page collections and reserve the bounded page budget before attempts. Preserve verified summaries and continue querying the other stream. Cryptographic, parser, and transport errors still propagate. The fake-watch regression exercises both optional rejections followed by successful totals. This is not physical GT5 validation; cumulative CI must pass before signing.

## v652 bounded 0x2c file-read foundation

Reviewed the wire fields in Gadgetbridge FileDownloadService2C (blob bbbea533264cf4970be84904a3628fd6144d8fd0) and file identifiers in HuaweiFileDownloadManager (blob 8c855ec5dabbda2196aaf01bbd99b7d497e6fb5e). Independent Kotlin codec for fixed sleep-state/data and workout GPS filenames, exact identity/size metadata, file parameters, block offsets and bounded 16 MiB serial assembly. Cleartext requests are not supported; callers must establish GCM authentication before decoding raw block content. Reject mismatched identity, missing/duplicate/overflowing blocks; only a complete file can be exposed. No runtime file requests, capability changes, filesystem writes, sleep stages or GPS points yet. Protocol fixtures do not prove GT5 compatibility. Next: capability-gated serialized authenticated multi-response transport, physical evidence, decode and persistence. Legacy 0x0A transport remains separate. Gadgetbridge TruSleep analysis itself is unfinished in the reviewed source; do not infer sleep stages from raw PPG/ACC or import them as WHOOP sleep. No AGPL code copied.

## v653 authenticated 0x2c experimental session

Added advertised-command discovery, a serialized file burst path on the proven session, interleaved 04 acknowledgement/05 blocks, exact offsets and bounded receive budget, global 90 s deadline and 4096 packet ceiling. GCM required for metadata and raw blocks; any ambiguous interrupted exchange invalidates the session. Completion is a transfer notification, not a delete. Fake watch exercises interleaved authenticated packets and rejected plaintext. Reviewed Gadgetbridge FileDownloadService2C and GetFileBlockRequest/GetFileDownloadCompleteRequest for wire facts only. Strict authenticated 04 acknowledgement and one-way completion are candidate assumptions; firmware compatibility is unverified. API remains opt-in diagnostic-only and is not invoked by automatic sync. GPS/TruSleep decoding, storage, legacy 0x0A, hardware validation and scoring integration remain pending.

## v654 experimental GPS decoding and staging

Reviewed the little-endian header and 15/19-byte delta records described by Gadgetbridge HuaweiGpsParser (blob 44cd4dca229e41715e16198efe04b361982fc936). Independently implemented a conservative candidate for exact record-aligned files; reject unknown/truncated trailers, nonfinite coordinates, nonmonotonic/out-of-workout times and out-of-range coordinates. Uses origin-relative cumulative offsets with the reference radius; the reference reconstruction and trailer handling have ambiguities, so the candidate is not verified against GT5 data and does not generate route/distance/score output. Optional altitude remains native raw. Additive private SQLite schema v4 stages candidate points transactionally against matching workout identity, with no empty replacement; prior Huawei and WHOOP tables remain unchanged. Decoder fixtures are synthetic. No automatic retrieval or production route selection. Physical file capture and route comparison, database migration verification, TruSleep decoding and archive support remain pending.

## v655 experimental TruSleep sensor decoding and staging

Reviewed HuaweiTruSleepParser (Gadgetbridge blob ec24dfa50622a8fca102b9065d247874df59877a) for wire format facts; its sleep-stage analysis is explicitly unfinished. Independent strict decoder for 16-byte little-endian sleep-state records and interleaved zero-padded ACC/PPG packets, raw and compressed signed 16-bit peak/amplitude arrays, three raw ACC axes and millisecond timestamps. Bounds, duplicates, malformed/truncated packets and timestamp windows are checked before staging; unsupported variants reject the entire pair. Candidate assumptions (ACC length 7, signed compression values and nonnegative increasing peak offsets, requested time window) require physical GT5 samples. Additive SQLite schema v5 separates state metadata, ACC and PPG by device and timestamp, transactionally upserting complete parsed records. No sleep-stage classification, score, HRV/RR conversion, breathing diagnosis, automatic retrieval or sleep source switching. Synthetic tests cover raw/compressed formats, duplicate conflicts and rejection. Real fixtures, migration tests, authenticated retrieval wiring and archive support remain outstanding. WHOOP sleep and all ALKE score paths unchanged.

## v656 combined integration candidate

Batch: exact-minute Steps fusion with independent fallback deltas (no conversion from WHOOP daily estimates); fixture tests preserve explicit zero, overlap, conflicts and unknown gaps. No production daily Steps replacement until a trustworthy fallback minute source is wired. Explicit debug-only GPS/TruSleep test action uses one saved-binding session, latest workout and bounded 24h sleep window; parser layout rejection stages no data, transport/authentication errors terminate the session. No automatic file sync or fabricated stages. Normal native sleep/workout history refreshes every 15 minutes of elapsed session time rather than only on first poll; authenticated rejected streams are suppressed until next session. Encrypted standalone Huawei recovery helper checks schema/version/size/integrity and merges missing rows without replacing existing data; credentials and WHOOP storage excluded. Restore row-domain validation, integration with backup UI/Drive, migration and recovery tests, process-restart/foreground lifecycle, real GT5 validation and final CI remain release blockers. No main merge or signing.

## v657 personal candidate integration and archive audit

Enable the explicitly experimental Devices card on signed Personal variants, while excluding Demo and public non-debug Full. Manual encrypted Huawei recovery via Android document picker (may select a Drive provider; not automatic cloud backup), readback SHA-256 verification and bounded import, row-domain/schema checks including Android locale metadata, orphan detail rejection and merge-only restore. Added native SQLite recovery round-trip test. Authenticated INIT-only file rejection no longer blocks probing the other file stream; other failures still invalidate the session. WHOOP data, credentials and algorithms are not included or replaced. Connection remains app-process-owned, no automatic startup/background service, and Steps fusion is still not selected for incomplete days lacking independent WHOOP 4 minute counts. Real GT5 evidence and complete CI are necessary before production enablement. Candidate for owner smoke test, not a completed production Huawei replacement.

## v658 targeted recovery fix

v657 CI built the Personal APK and passed targeted checks, but the full suite reported 7,284 tests, one failure and 27 skipped: HuaweiRecoveryArchiveTest.encryptedRoundTripMergesMissingRowsWithoutReplacingExisting failed at export (line 27). Logs did not expose the internal require site. Replace live database byte copy and rollback-journal-only restriction with transaction-consistent logical snapshot, canonical schema creation, private snapshot checkpoint and cleanup. Preserve strict validation, encrypted export and merge-only restore. Add WAL-source round-trip regression and run Huawei tests at the pre-build gate with failure XML on error. No unrelated changes. CI must establish whether the patch resolves the failure; no release before green.
