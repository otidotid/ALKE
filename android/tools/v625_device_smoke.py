"""Isolated synthetic-device black-box smoke. Never uses production credentials or real history."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.otidotid.alke.demo.debug"
SERIAL = os.environ.get("ALKE_SMOKE_SERIAL", "emulator-5554")
OUT = Path("smoke-evidence")
OUT.mkdir(exist_ok=True)

def adb(*args, timeout=45):
    return subprocess.check_output(["adb", "-s", SERIAL, *args], timeout=timeout)

def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/alke-smoke.xml")
    data = adb("shell", "cat", "/sdcard/alke-smoke.xml")
    return ET.fromstring(data)

def bounds(node):
    nums = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    return nums if len(nums) == 4 else [0, 0, 0, 0]

def lookup(root, label, dock=False, presence_only=False):
    matches = []
    for node in root.iter("node"):
        if node.get("package") != PACKAGE:
            continue
        labels = [node.get("text", ""), node.get("content-desc", "")]
        if not any(label.casefold() == x.strip().casefold() for x in labels):
            continue
        x1, y1, x2, y2 = bounds(node)
        if x2 <= x1 or y2 <= y1:
            continue
        if dock and y1 < 2050:
            continue
        matches.append(node)
    # Presence assertions do not select a tap target. Record every match and
    # retain screenshots/XML for visual review of any duplicate labels.
    if presence_only and matches:
        exact = [n for n in matches if label in [n.get('text', '').strip(), n.get('content-desc', '').strip()]]
        if len(matches) > 1:
            print(f'PRESENCE {label!r}: {len(matches)} matches; bounds={[n.get("bounds") for n in matches]}', flush=True)
        return (exact or matches)[0]
    # Semantics may expose both parent and text child; same center is one target.
    centers = {}
    for n in matches:
        x1, y1, x2, y2 = bounds(n)
        centers[((x1+x2)//2, (y1+y2)//2)] = n
    if len(centers) == 1:
        return next(iter(centers.values()))
    if len(centers) > 1:
        clickable = [n for n in centers.values() if n.get("clickable") == "true"]
        if len(clickable) == 1:
            return clickable[0]
        raise AssertionError("Ambiguous selector: " + label)
    return None

def screenshot(name):
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    (OUT / (name + ".xml")).write_bytes(ET.tostring(tree(), encoding="utf-8"))

def find(label, scroll=False, dock=False, presence_only=False):
    for attempt in range(11 if scroll else 6):
        root = tree()
        n = lookup(root, label, dock, presence_only=presence_only)
        if n is not None:
            return n
        if scroll:
            adb("shell", "input", "swipe", "540", "1850", "540", "650", "450")
        time.sleep(1)
    raise AssertionError("Missing UI: " + label)

def tap(label, scroll=False, dock=False):
    x1,y1,x2,y2=bounds(find(label,scroll,dock))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1.4)

def verify(label, name, scroll=False):
    find(label, scroll, presence_only=True)
    screenshot(name)
    print("PASS:", name, flush=True)

def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.5)

def top():
    for _ in range(8):
        adb("shell", "input", "swipe", "540", "650", "540", "1850", "250")

def dock(label):
    tap(label, dock=True)
    top()

def main():
    adb("logcat", "-c")
    adb("shell", "am", "start", "-W", "-n", PACKAGE+"/com.otidotid.alke.ui.MainActivity")
    time.sleep(20)
    verify("YOUR DAILY OUTLOOK", "01-today")
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tap("Rest")
    verify("Sleep performance", "02-sleep")
    verify("Sleep Architecture", "03-sleep-architecture", scroll=True)
    tap("Sleep Balance", scroll=True)
    screenshot("04-sleep-balance-expanded")
    back()
    dock("Today")
    tap("HEART RATE", scroll=True)
    verify("Timeline", "05-heart-rate")
    tap("Live HR")
    verify("Rhythm", "06-live-console")
    tap("Rhythm")
    screenshot("07-rhythm")
    tap("Timeline")
    screenshot("08-heart-rate-return")
    back()
    dock("Train")
    verify("Activity Overview", "09-train-overview", scroll=True)
    verify("All Sessions", "10-all-sessions", scroll=True)
    tap("Manual")
    screenshot("11-manual-filter")
    tap("All")
    dock("Progress")
    verify("Fitness Factors", "12-fitness-age", scroll=True)
    tap("Trends Studio", scroll=True)
    verify("Explore", "13-trends")
    tap("Explore")
    verify("Compare", "14-explore")
    tap("Compare")
    screenshot("15-compare")
    tap("Explore")
    screenshot("16-explore-return")
    back()
    dock("Insight")
    verify("Your Signals", "17-signals", scroll=True)
    verify("Daily Journal", "18-journal", scroll=True)
    tap("Mood")
    screenshot("19-mood")
    top()
    tap("Caffeine", scroll=True)
    screenshot("20-caffeine")
    verify("Personal Analytics", "21-personal-analytics", scroll=True)
    verify("Your Insight Summary", "22-ai-summary", scroll=True)
    dock("Me")
    verify("Your Profile", "23-profile")
    verify("Connected Sources", "24-connected-sources", scroll=True)
    tap("Customize Dock", scroll=True)
    screenshot("25-dock-editor")
    back()
    top()
    tap("More Tools", scroll=True)
    tap("All Modules", scroll=True)
    tap("Activity", scroll=True)
    tap("Stress & Breathe", scroll=True)
    verify("Stress & Breathe", "26-stress")
    tap("Breathe")
    screenshot("27-breathe")
    tap("Stress")
    screenshot("28-stress-return")
    dock("Today")
    verify("YOUR DAILY OUTLOOK", "29-today-return")
    (OUT/"frames.txt").write_bytes(adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats"))
    (OUT/"meminfo.txt").write_bytes(adb("shell", "dumpsys", "meminfo", PACKAGE))

if __name__ == '__main__':
    try:
        main()
    finally:
        (OUT/"final.png").write_bytes(adb("exec-out", "screencap", "-p"))
        (OUT/"final.xml").write_bytes(ET.tostring(tree(), encoding="utf-8"))
        crash = adb("logcat", "-b", "crash", "-d").decode(errors="replace")
        (OUT/"crash.txt").write_text(crash)
        (OUT/"logcat.txt").write_bytes(adb("logcat", "-d", "-t", "2000"))
        if PACKAGE in crash:
            raise AssertionError("App crash during smoke; inspect crash.txt")
