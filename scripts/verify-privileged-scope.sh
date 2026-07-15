#!/usr/bin/env bash
set -euo pipefail

manifest="app/src/main/AndroidManifest.xml"
source_root="app/src/main/java"

permissions="$(grep -o 'uses-permission android:name="[^"]*"' "$manifest" \
  | sed -E 's/.*android:name="([^"]*)"/\1/' \
  | sort -u)"
allowed_permissions=$'android.permission.RECEIVE_BOOT_COMPLETED\nandroid.permission.WRITE_SECURE_SETTINGS'

if [[ "$permissions" != "$allowed_permissions" ]]; then
  echo "ERROR: Manifest permission scope changed." >&2
  echo "Found:" >&2
  printf '%s\n' "$permissions" >&2
  exit 1
fi

if grep -R --line-number -E '\b(Socket|ServerSocket|HttpURLConnection|URLConnection|WebView|DexClassLoader|Runtime\.getRuntime\(\)\.exec|ProcessBuilder)\b' "$source_root"; then
  echo "ERROR: Unexpected network, dynamic-code, or shell-execution API found." >&2
  exit 1
fi

secure_references="$(grep -R -h -E -o 'Settings\.Secure\.[A-Z_]+' "$source_root" | sort -u || true)"
allowed_references=$'Settings.Secure.ACCESSIBILITY_ENABLED\nSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES'

if [[ "$secure_references" != "$allowed_references" ]]; then
  echo "ERROR: Settings.Secure usage changed." >&2
  echo "Found:" >&2
  printf '%s\n' "$secure_references" >&2
  exit 1
fi

echo "Privileged scope check passed."
