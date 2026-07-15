# Validation notes

Before packaging, the project was checked with:

- XML parsing for every Android resource and manifest XML file
- YAML parsing for GitHub Actions and Dependabot configuration
- Bash syntax and execution of the privileged-scope verification script
- Java compilation against a minimal API-compatible Android stub set to catch syntax and type-use errors
- Source scan confirming that the manifest permission allowlist contains only:
  - `android.permission.WRITE_SECURE_SETTINGS`
  - `android.permission.RECEIVE_BOOT_COMPLETED`
- Source scan confirming that no network, dynamic-code-loading, WebView, or shell-execution APIs are present
- Source scan restricting `Settings.Secure` references to:
  - `ENABLED_ACCESSIBILITY_SERVICES`
  - `ACCESSIBILITY_ENABLED`

The definitive Android SDK compilation, unit-test, and lint run is performed by `.github/workflows/build.yml` after the repository is pushed to GitHub. The current execution environment could not download Gradle or Android SDK packages, so no local APK is claimed here.
