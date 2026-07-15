#!/usr/bin/env bash
set -euo pipefail

manifest="app/src/main/AndroidManifest.xml"
source_root="app/src/main/java"

# Parse the AndroidManifest as XML so multiline permission declarations work.
permissions="$(
  python3 - "$manifest" <<'PY'
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

root = ET.parse(sys.argv[1]).getroot()

permissions = sorted({
    element.get(ANDROID_NS + "name")
    for element in root.findall("uses-permission")
    if element.get(ANDROID_NS + "name")
})

print("\n".join(permissions))
PY
)"

allowed_permissions=$'android.permission.RECEIVE_BOOT_COMPLETED\nandroid.permission.WRITE_SECURE_SETTINGS'

if [[ "$permissions" != "$allowed_permissions" ]]; then
  echo "ERROR: Manifest permission scope changed." >&2
  echo "Expected:" >&2
  printf '%s\n' "$allowed_permissions" >&2
  echo "Found:" >&2
  printf '%s\n' "$permissions" >&2
  exit 1
fi

if grep -R --line-number -E \
  '\b(Socket|ServerSocket|HttpURLConnection|URLConnection|WebView|DexClassLoader|Runtime\.getRuntime\(\)\.exec|ProcessBuilder)\b' \
  "$source_root"; then
  echo "ERROR: Unexpected network, dynamic-code, or shell-execution API found." >&2
  exit 1
fi

secure_references="$(
  grep -R -h -E -o 'Settings\.Secure\.[A-Z_]+' "$source_root" |
    sort -u || true
)"

allowed_references=$'Settings.Secure.ACCESSIBILITY_ENABLED\nSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES'

if [[ "$secure_references" != "$allowed_references" ]]; then
  echo "ERROR: Settings.Secure usage changed." >&2
  echo "Expected:" >&2
  printf '%s\n' "$allowed_references" >&2
  echo "Found:" >&2
  printf '%s\n' "$secure_references" >&2
  exit 1
fi

echo "Privileged scope check passed."
