# Changelog

## 0.2.0

- Added optional, disabled-by-default boot reapply
- Added a second delayed apply during boot for TV firmware that initializes accessibility settings late
- Documented permission persistence and the distinction between Debug and fixed Release signing
- Updated CI to AGP 8.13.2, Gradle 8.13, JDK 17, and current GitHub Actions majors
- Restricted the manifest permission allowlist to `WRITE_SECURE_SETTINGS` and `RECEIVE_BOOT_COMPLETED`

## 0.1.0

- Initial independent implementation
- Generic discovery and management of installed accessibility services
- Preservation of unknown existing entries
- One-step backup and restore
