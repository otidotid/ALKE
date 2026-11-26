# v627 work in progress — mandatory all-day HRV

Approved owner change: remove separate HRV enable, overnight and battery-pause switches. Retain background connection control and explicit disconnection. Effective background capture depends on background connection only; Live screen keeps its existing on-demand streaming path. Old restored HRV flags cannot narrow capture; legacy keys and helper tests retained for compatibility. No change to RR quality gates, nightly/deep sleep HRV scoring, data schemas, backup, or Charge/Recovery. No new AI classifier in this commit.

24/7 is a capture intent, not proof of uninterrupted measurements: actual availability depends on wearable connectivity, wear, battery, device support and OS restrictions. Higher wearable battery drain is possible. Real-device data continuity and power impact require manual verification.
