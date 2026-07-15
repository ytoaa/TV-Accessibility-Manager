# Security

This application receives `android.permission.WRITE_SECURE_SETTINGS`, a powerful development permission that must be granted through ADB or by a privileged installer.

Security boundaries in this project:

- No `INTERNET` permission
- No local or remote ADB client
- No shell command execution
- No native libraries or downloaded code
- No analytics or advertising SDK
- Secure Settings writes are limited to:
  - `enabled_accessibility_services`
  - `accessibility_enabled`
- Unknown existing accessibility entries are preserved
- Automatic boot reapply is disabled by default and only restores the last state explicitly saved by the user

Use a private, stable release signing key. Do not publish that key or commit it to the repository. A public signing key would let third parties create an APK that Android accepts as an update to this privileged app.
