#!/usr/bin/env bash
set -euo pipefail

# Collect diagnostics before emulator-runner shuts down the device, including on test failure.
collect_diagnostics() {
  mkdir -p work/webview-smoke
  timeout 20s adb -s emulator-5554 logcat -d > work/webview-smoke/logcat.txt || true
  timeout 20s adb -s emulator-5554 pull \
    /sdcard/Android/data/csqtt.quic.amurcanov.maxdebug/files/max-webview.png \
    work/webview-smoke/ || true
}
trap collect_diagnostics EXIT

./gradlew :app:connectedDebugAndroidTest -Pcsqtt.abis=x86_64 \
  "-Pandroid.testInstrumentationRunnerArguments.class=${MAX_WEBVIEW_TEST_CLASS:-com.csqtt.client.MaxAuthWebViewSmokeTest}" \
  '-Dorg.gradle.jvmargs=-Xmx3G -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED' \
  --max-workers=2 --no-daemon --stacktrace
