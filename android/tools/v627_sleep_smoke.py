"""Exercise v627 Sleep on an isolated, synthetic, offline Android 14 emulator."""
import time
from v625_device_smoke import PACKAGE, OUT, adb, back, dock, tap, top, verify, screenshot, tree, lookup

def dismiss_morning_prompt():
    # The synthetic fixture can trigger this asynchronous journal sheet.
    # Require both exact labels before dismissing; keep evidence of the sheet.
    for _ in range(5):
        root = tree()
        if lookup(root, "Good morning!", presence_only=True) is not None:
            if lookup(root, "Maybe later") is None:
                raise AssertionError("Morning prompt missing dismissal")
            screenshot("morning-prompt")
            tap("Maybe later")
            return
        time.sleep(1)

try:
    adb("logcat", "-c")
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/com.otidotid.alke.ui.MainActivity")
    time.sleep(15)
    dismiss_morning_prompt()
    verify("YOUR DAILY OUTLOOK", "01-today")
    tap("Rest")
    dismiss_morning_prompt()
    verify("Rest score", "02-rest", scroll=True)
    verify("TOTAL DAILY SLEEP", "03-total", scroll=True)
    verify("Day sleep", "04-day-sleep", scroll=True)
    verify("Nap", "05-nap", scroll=True)
    top()
    tap("Sleep Intelligence", scroll=True)
    verify("Allow sleep analysis", "06-consent")
    tap("Cancel")
    top()
    verify("Sleep Architecture", "07-architecture", scroll=True)
    dock("Today")
    verify("YOUR DAILY OUTLOOK", "08-return")
finally:
    screenshot("final")
    (OUT / "meminfo.txt").write_bytes(adb("shell", "dumpsys", "meminfo", PACKAGE))
    (OUT / "frames.txt").write_bytes(adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats"))
    (OUT / "logcat.txt").write_bytes(adb("logcat", "-d", "-t", "2000"))
    crash = adb("logcat", "-b", "crash", "-d").decode(errors="replace")
    (OUT / "crash.txt").write_text(crash)
    if PACKAGE in crash:
        raise AssertionError("ALKE crashed during Sleep smoke")
