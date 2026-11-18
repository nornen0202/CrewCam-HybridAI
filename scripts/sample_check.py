"""Run a public synthetic fixture on one real, explicitly selected Android installation."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time

PACKAGE = "com.galaxycrew.crewcam.hybrid"
CHECKS = {"status": 0, "download": 1, "local": 2, "cloud": 3, "fallback": 4, "appcheck": 5,
          "preferred": 6, "categories": 7, "medium": 8, "close": 9, "absent": 10}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("check", choices=CHECKS)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--require-nano4", action="store_true",
                        help="A local check must report an actual Nano 4 model name.")
    args = parser.parse_args()
    if args.require_nano4 and args.check != "local":
        parser.error("--require-nano4 applies to the local check")
    if args.out.exists():
        parser.error("Choose a new evidence filename.")
    def adb(*words, check=True):
        result = subprocess.run([args.adb, "-s", args.serial, *words], capture_output=True,
                                text=True, encoding="utf-8", errors="replace", timeout=20)
        if check and result.returncode:
            raise RuntimeError("ADB failed at " + words[0] + " (" + str(result.returncode) + ")")
        return result.stdout.strip()
    def read_events():
        raw = adb("exec-out", "run-as", PACKAGE, "cat", "files/runs.jsonl", check=False)
        events = []
        for line in raw.splitlines():
            try:
                value = json.loads(line)
                if isinstance(value, dict):
                    events.append(value)
            except ValueError:
                continue
        return events
    base = next(line[8:] for line in adb("shell", "pm", "path", "--user", "0", PACKAGE).splitlines()
                if line.startswith("package:") and line.endswith("/base.apk"))
    with args.apk.open("rb") as apk_file:
        local_hash = hashlib.file_digest(apk_file, "sha256").hexdigest()
    installed_hash = adb("shell", "sha256sum", base).split()[0]
    if installed_hash != local_hash:
        raise RuntimeError("Installed APK differs from the candidate.")
    baseline = len(read_events())
    adb("shell", "am", "start", "-S", "--user", "0", "-n",
        PACKAGE + "/com.galaxycrew.crewcam.MainActivity", "--ei", "sample_check", str(CHECKS[args.check]))
    deadline = time.monotonic() + 125
    events = []
    while time.monotonic() < deadline:
        events = read_events()[baseline:]
        if any(e.get("event") == "sample_status" and e.get("index") == CHECKS[args.check] for e in events):
            break
        time.sleep(1)
    receipt = {"check": args.check, "apk_sha256": local_hash, "installed_hash_matched": True,
               "device": adb("shell", "getprop", "ro.product.model"),
               "android": adb("shell", "getprop", "ro.build.version.release"),
               "transport": "USB test control, no peer", "origin": "generated sample", "events": events}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    status = next((e["display"] for e in events if e.get("event") == "sample_status"), "No completion receipt")
    print(args.check, status, "->", args.out)
    expected = {"local": ("ONLY_ON_DEVICE", "ON_DEVICE", 1, "WIDE"), "cloud": ("ONLY_IN_CLOUD", "IN_CLOUD", 1, "WIDE"),
                "fallback": ("PREFER_ON_DEVICE", "IN_CLOUD", 1, "WIDE"),
                "medium": ("ONLY_IN_CLOUD", "IN_CLOUD", 1, "MEDIUM"),
                "close": ("ONLY_IN_CLOUD", "IN_CLOUD", 1, "CLOSE"),
                "absent": ("ONLY_IN_CLOUD", "IN_CLOUD", 1, "UNCERTAIN")}.get(args.check)
    if expected:
        return 0 if any((e.get("mode"), e.get("source"), e.get("images"), e.get("framing")) == expected
                        and e.get("result") == "valid"
                        and (not args.require_nano4 or "nano-v4" in str(e.get("local_model")).lower())
                        for e in events) else 1
    if args.check == "appcheck":
        return 0 if status == "App Check verified for this installation." else 1
    if args.check == "preferred":
        return 0 if any(e.get("mode") == "PREFER_ON_DEVICE" and e.get("images") == 1
                        and e.get("source") in ("ON_DEVICE", "IN_CLOUD") and e.get("result") == "valid"
                        and e.get("framing") == "WIDE" for e in events) else 1
    return 0 if any(part.strip() == "AVAILABLE" for line in status.splitlines() for part in line.split("·")) else 1

if __name__ == "__main__":
    raise SystemExit(main())
