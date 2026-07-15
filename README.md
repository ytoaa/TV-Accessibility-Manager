# TV Accessibility Manager

Fire TV와 Android TV에서 설치된 `AccessibilityService`를 리모컨으로 켜고 끄기 위한 독립 오픈소스 구현입니다. 특정 앱 전용이 아니며 C9, 버튼 매퍼, 런처 등 기기에 설치된 모든 접근성 서비스 구성요소를 검색해 관리합니다.

## 특징

- 설치된 모든 접근성 서비스 목록 조회
- 앱 하나에 여러 접근성 서비스가 있으면 서비스별로 표시
- 현재 활성화 상태 표시 및 D-pad/리모컨 토글
- `enabled_accessibility_services`와 `accessibility_enabled`만 변경
- 목록에 표시되지 않는 기존 활성 항목 보존
- 변경 전 상태 1회 백업 및 복구
- 저장 후 Fire OS가 값을 유지했는지 재확인
- 선택적으로 재부팅 후 마지막 저장 상태 재적용
- 인터넷 권한, 자체 ADB 클라이언트, 네이티브 바이너리, 셸 실행 없음

## 관리 가능한 범위

이 앱은 Android의 `AccessibilityService` 활성화 상태를 관리합니다.

가능한 예:

- C9 커서 서비스
- 버튼 매퍼의 접근성 서비스
- 서드파티 런처의 접근성 서비스
- 자동화 앱의 접근성 서비스
- 한 앱에 포함된 여러 접근성 서비스

다음 별도 특수 권한까지 관리하지는 않습니다.

- 다른 앱 위에 표시
- 알림 접근
- 사용 정보 접근
- VPN
- 기기 관리자
- Shizuku 권한

## 최초 설치

GitHub Actions의 **Build Android APK** 실행 결과에서 APK를 받아 설치합니다.

Debug 빌드 패키지명:

```text
io.github.yowo.tvaccessibilitymanager.debug
```

Release 빌드 패키지명:

```text
io.github.yowo.tvaccessibilitymanager
```

설치 후 사용하는 빌드에 맞춰 외부 ADB에서 **한 번만** 권한을 부여합니다.

```bash
# Debug APK
adb shell pm grant io.github.yowo.tvaccessibilitymanager.debug \
  android.permission.WRITE_SECURE_SETTINGS

# Release APK
adb shell pm grant io.github.yowo.tvaccessibilitymanager \
  android.permission.WRITE_SECURE_SETTINGS
```

여러 ADB 장치가 연결되어 있다면:

```bash
adb -s FIRE_TV_IP:5555 shell pm grant \
  io.github.yowo.tvaccessibilitymanager \
  android.permission.WRITE_SECURE_SETTINGS
```

권한 확인:

```bash
adb shell dumpsys package io.github.yowo.tvaccessibilitymanager \
  | grep -A8 WRITE_SECURE_SETTINGS
```

## 재부팅 후 권한과 접근성 상태

`pm grant`로 부여한 `WRITE_SECURE_SETTINGS` 권한은 일반적으로 재부팅 후에도 유지됩니다. 따라서 Fire TV를 재부팅할 때마다 ADB 명령을 다시 실행할 필요는 없습니다.

다시 부여해야 할 수 있는 경우:

- 앱을 삭제한 뒤 재설치
- 공장 초기화
- 서명이 다른 APK로 바꾸기 위해 기존 앱을 삭제한 경우
- Fire OS 업데이트가 해당 개발용 권한을 명시적으로 회수한 예외적인 경우

같은 패키지명과 같은 서명키로 정상 업데이트하면 권한과 앱 데이터는 보통 유지됩니다.

Fire OS가 재부팅 과정에서 **다른 앱의 접근성 활성화 목록만** 초기화하는 경우에는 관리자 앱의 ADB 권한과는 별개입니다. 앱의 `재부팅 후 마지막 저장 상태 자동 재적용` 옵션을 켜면 마지막으로 저장한 전체 접근성 서비스 목록을 부팅 완료 시 두 차례 재적용합니다. 기본값은 꺼짐이며, Fire OS가 부팅 후 설정을 다시 덮어쓰는 경우에만 켜는 것을 권장합니다.

주의: 이 옵션은 사용자가 나중에 다른 방법으로 끈 서비스도 마지막 저장 목록에 포함되어 있으면 다시 켤 수 있습니다. 목록을 바꾼 뒤 반드시 이 앱에서 `선택 저장`을 눌러 원하는 상태를 갱신하세요.

## GitHub Actions 빌드

저장소에 push하면 `.github/workflows/build.yml`이 자동으로 다음을 실행합니다.

1. JDK 17, Gradle 8.13, Android SDK 36 준비
2. 권한 범위 검증 스크립트 실행
3. 단위 테스트와 Android Lint
4. Debug APK 빌드 및 Artifact 업로드
5. 서명 Secrets가 모두 있으면 signed Release APK 추가 빌드

Debug APK는 즉시 생성되지만 GitHub-hosted runner의 기본 debug 키는 실행마다 달라질 수 있습니다. 새 Action 빌드의 Debug APK를 기존 Debug APK 위에 업데이트하지 못할 수 있으며, 삭제 후 재설치하면 ADB 권한도 다시 줘야 합니다. 장기 사용은 반드시 고정된 개인 release 키를 사용하세요.

## 고정 Release 서명 설정

로컬에서 키를 한 번 생성합니다.

```bash
keytool -genkeypair -v \
  -keystore tv-accessibility-manager.jks \
  -alias tv-accessibility-manager \
  -keyalg RSA -keysize 3072 -validity 10000
```

Base64 값 생성:

```bash
# macOS / Linux
base64 < tv-accessibility-manager.jks | tr -d '\n'
```

GitHub 저장소의 `Settings → Secrets and variables → Actions`에 다음 Secrets를 등록합니다.

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

서명키는 절대로 저장소에 커밋하지 마세요. 이 앱은 강한 시스템 설정 권한을 받으므로 공개된 서명키를 사용하면 제3자가 동일 서명으로 변조 업데이트를 만들 수 있습니다.

## 패키지명 변경

현재 기본 패키지명은 `io.github.yowo.tvaccessibilitymanager`입니다. GitHub 사용자명과 다르다면 최초 설치 전에 다음을 함께 변경하세요.

- `app/build.gradle`의 `namespace`, `applicationId`
- `app/src/main/java/io/github/yowo/tvaccessibilitymanager/` 경로
- 모든 Java 파일의 `package` 선언

패키지명을 바꾸면 Android는 별도 앱으로 취급하므로 권한도 새 패키지에 다시 부여해야 합니다.

## 직접 빌드

Gradle 8.13이 설치된 환경에서:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions에서는 `gradle/actions/setup-gradle`이 Gradle을 설치하므로 Wrapper 바이너리를 저장소에 포함하지 않아도 빌드됩니다.

결과:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 변경하는 설정

앱은 아래 Android Secure Settings 두 개만 변경합니다.

```text
enabled_accessibility_services
accessibility_enabled
```

목록에 표시되지 않지만 기존 설정에 남아 있는 Component 항목과 파싱할 수 없는 기존 토큰은 기본적으로 삭제하지 않고 보존합니다.

## 독립 구현

이 프로젝트는 Android 공개 API와 시스템 설정 형식을 기반으로 새로 작성한 독립 구현입니다. Accessibility Permissions Manager by SweenWolf의 소스 코드, 리소스 또는 바이너리를 포함하지 않습니다.

## License

Apache License 2.0
