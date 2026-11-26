"""Exercise v627 Sleep on an isolated, synthetic, offline Android 14 emulator."""
import time
from v625_device_smoke import PACKAGE, OUT, adb, back, dock, tap, top, verify, screenshot

try:
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/com.otidotid.alke.ui.MainActivity")
    time.sleep(15)
    verify("YOUR DAILY OUTLOOK", "01-today")
    tap("Rest")
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
    crash = adb("logcat", "-b", "crash", "-d").decode(errors="replace")
    (OUT / "crash.txt").write_text(crash)
    if PACKAGE in crash:
        raise AssertionError("ALKE crashed during Sleep smoke")
