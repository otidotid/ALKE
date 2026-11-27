# Huawei GT 5 interoperability research — 2026-11-27

Scope: native ALKE integration. These notes are research evidence, not a claim
of implemented or hardware-tested authentication.

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

1. Confirm supported authentication mode from real device response; the
   current debug probe ends at security negotiation, not binding.
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

## License constraint

ALKE root LICENSE retains PolyForm Noncommercial for original code. Do not
copy or port AGPL implementation code into this project under that license
without obtaining compatible permissions or resolving the licensing model.
Use documented wire facts and independently written code. OpenHarmony's
Apache-2.0 license may permit reuse of relevant components subject to its
conditions; it is not evidence of drop-in compatibility.

No new driver behavior is enabled by this document.
