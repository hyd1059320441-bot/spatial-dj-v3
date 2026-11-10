#!/usr/bin/env python3
"""Build and run a score by locating and contacting the live Spatial Loop UI."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import wave

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent.parent
PACKAGE = "com.example.spatialdj.loopv3"
CLASS = "com.example.spatialdj.p170bd28c.UiMusicPerformance"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
JDK = "/Users/bytedance/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home"


def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--score", type=Path, default=HERE / "score.json", help="UI performance score JSON")
    parser.add_argument("--serial", help="ADB serial; a single online device is selected automatically")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--listen", type=int, default=600, help="Keep the UI playing this many seconds after recording (0 disables)")
    args = parser.parse_args()
    if not args.serial:
        lines = run(["adb", "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
        online = [line.split()[0] for line in lines if len(line.split()) >= 2 and line.split()[1] == "device"]
        if len(online) != 1:
            parser.error("Specify --serial when zero or multiple devices are online")
        args.serial = online[0]
    adb = ["adb", "-s", args.serial]
    score_path = args.score.resolve()
    score = json.loads(score_path.read_text())
    stem = score.get("fileStem", "touch-house")
    if not re.fullmatch(r"[A-Za-z0-9_-]+", stem):
        parser.error("fileStem must contain only letters, numbers, underscores or hyphens")
    score_text = json.dumps(score, separators=(",", ":"))
    output = score_path.parent / "runs" / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    output.mkdir(parents=True)
    (output / "score.json").write_text(json.dumps(score, indent=2) + "\n")
    if not args.skip_build:
        env = dict(os.environ, JAVA_HOME=JDK)
        run(["./gradlew", ":app:assembleDebug", ":app:assembleDebugAndroidTest"], cwd=PROJECT, env=env)
    run(adb + ["install", "-r", str(PROJECT / "app/build/outputs/apk/debug/app-debug.apk")])
    run(adb + ["install", "-r", str(PROJECT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")])
    print(f"开始定位控件并演奏：{score['title']}，{score['bpm']} BPM，{score['bars']} 小节。", flush=True)
    command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class", CLASS + "#performScore",
                     "-e", "score", "'" + score_text.replace("'", "'\\''") + "'", RUNNER]
    # adb shell joins its argument vector into a remote shell command; quote JSON explicitly.
    with (output / "instrumentation.log").open("w") as log:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            sys.stdout.write(line)
            log.write(line)
            log.flush()
        if process.wait() != 0:
            raise RuntimeError("Device performance command failed; see instrumentation.log")
    if "OK (1 test)" not in (output / "instrumentation.log").read_text():
        raise RuntimeError("UI performance failed; see instrumentation.log. No previous result is reused.")
    report_bytes = run(adb + ["exec-out", "run-as", PACKAGE, "cat", "files/ui-music-result.json"], capture_output=True).stdout
    (output / "result.json").write_bytes(report_bytes)
    result = json.loads(report_bytes)
    if not result.get("ok"):
        raise RuntimeError(result.get("error", "UI performance failed"))
    run(adb + ["pull", result["wav"], str(output / f"{stem}.wav")])
    project_bytes = run(adb + ["exec-out", "run-as", PACKAGE, "cat", "files/saved.loop"], capture_output=True).stdout
    (output / f"{stem}.loop").write_bytes(project_bytes)
    with wave.open(str(output / f"{stem}.wav")) as audio:
        samples = audio.readframes(audio.getnframes())
        pcm = struct.unpack("<" + "h" * (len(samples) // 2), samples)
        if audio.getnchannels() != 2 or audio.getframerate() != 48000:
            raise RuntimeError("Unexpected WAV format")
        if max(abs(v) for v in pcm) <= 1000:
            raise RuntimeError("Exported audio is silent")
        duration = audio.getnframes() / audio.getframerate()
    print(f"完成：{result['clicks']} 次控件输入，各轨音符 {result['counts']}，WAV {duration:.2f} 秒。\n{output}", flush=True)
    (score_path.parent / "latest-run.txt").write_text(str(output) + "\n")
    if args.listen > 0:
        # Instrumentation ends by killing its process. Relaunch and load/play through
        # the same UI locator; keep this runner alive, without a production debug API.
        command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                         CLASS + "#resumeSavedMusic", "-e", "listenSeconds", str(args.listen), RUNNER]
        with (output / "listening.log").open("w") as log:
            listener = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        (output / "listening.pid").write_text(str(listener.pid) + "\n")
        print(f"真机正在载入并播放保存工程；监听进程 {listener.pid}，持续 {args.listen} 秒。", flush=True)


if __name__ == "__main__":
    main()
