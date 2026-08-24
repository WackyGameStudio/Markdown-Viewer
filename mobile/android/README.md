# Markdown Viewer for Android

스마트폰과 태블릿을 함께 지원하는 네이티브 Android 앱입니다. 앱 셸과 파일 탐색은 Kotlin·Jetpack Compose로 만들고, Markdown·Mermaid·이미지·PDF·DOCX·PPTX 렌더링은 APK에 포함된 제한된 로컬 WebView 자산으로 처리합니다. HTML은 JavaScript와 네이티브 브리지를 끈 별도 WebView, 영상은 Android Media3 네이티브 플레이어로 엽니다. Wails 런타임과 유료 문서 SDK는 사용하지 않습니다.

Wails v3와 다른 크로스플랫폼 선택지를 비교한 근거는 [ARCHITECTURE.md](ARCHITECTURE.md)에 정리했습니다. 2026년 8월 현재 Wails v3 Android는 동작하지만 공식적으로 experimental이므로, 이 앱의 생산 기반은 Android 네이티브 셸로 결정했습니다.

## 빠른 시작

필수 준비물은 Android Studio(번들 JBR 포함), Android SDK Platform·Build Tools 37, Platform Tools, Node.js/npm입니다. 실기기에 설치할 때는 기기의 개발자 옵션과 USB 디버깅도 켭니다.

저장소 루트의 PowerShell에서 다음 명령을 실행하면 웹 뷰어 테스트·번들, Android 단위 테스트·린트, 디버그 APK 빌드와 연결 기기 설치까지 진행됩니다.

```powershell
Set-Location .\mobile\android
.\scripts\build.ps1
.\scripts\install-debug.ps1
```

Android Studio에서는 `mobile/android` 디렉터리를 프로젝트로 엽니다. 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 지원 형식

| 종류 | 확장자 | 앱 내부 보기 | 비고 |
| --- | --- | --- | --- |
| Markdown | `.md` | 지원 | GFM, Mermaid, 코드 강조, 목차, 상대 링크 |
| 이미지 | `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.svg`, `.bmp` | 지원 | 확대·회전·라이트박스 |
| PDF | `.pdf` | 지원 | 페이지 이동·선택 가능한 텍스트/링크 레이어·암호 입력 |
| 영상 | `.mp4`, `.m4v`, `.webm`, `.mkv`, `.mov` | 지원 | 실제 코덱 지원은 Android 기기 디코더에 따라 다름 |
| Office Open XML | `.docx`, `.pptx` | 보기 전용 지원 | 편집·매크로·완전한 레이아웃 재현은 지원하지 않음 |
| 구형 Office | `.doc`, `.ppt` | 외부 앱으로 열기 | 앱 내부 변환 없음 |
| HTML | `.html`, `.htm` | 제한된 오프라인 보기 | 스크립트·iframe·폼·원격 탐색 차단 |

앱의 기본 언어는 한국어입니다. `설정 → 일반 → 언어`에서 한국어와 영어를 즉시 전환할 수 있으며 선택값은 재실행 후에도 유지됩니다.

## 현재 구현된 기능

- Android Storage Access Framework(SAF) 폴더 선택과 영구 읽기 권한
- 앱 내 SMB 2/3 네트워크 폴더 연결, 여러 연결 저장·수정·삭제, SMB 문서 스트리밍
- 지원 문서만 표시하는 재귀 트리: `.md`, PNG/JPEG/GIF/WebP/SVG/BMP, PDF, MP4/M4V/WebM/MKV/MOV, DOCX/PPTX, DOC/PPT, HTML/HTM
- 최근 폴더 5개, 즐겨찾기, 새로 고침, 문서 앞/뒤 기록
- GFM Markdown, 코드 강조, 보안 수준 `strict`의 Mermaid
- 선택 폴더 안에서만 해석되는 상대 Markdown·이미지 링크
- 이미지 확대/축소·맞춤·회전·라이트박스
- PDF 페이지 이동·확대/축소·맞춤·회전·텍스트/링크 레이어·암호 입력
- DOCX 본문·표·이미지 보기와 PPTX 슬라이드 이동·확대/축소
- JavaScript를 실행하지 않는 로컬 HTML 보기와 같은 SAF 폴더의 CSS·이미지·글꼴·미디어 참조
- 영상 재생/일시정지, 10초 이동, 탐색, 화면 맞춤/채우기, 재생 위치 복원
- 문서별 Markdown 스크롤·글자 크기와 이미지·PDF·영상 표시 상태 복원
- 다음 문서를 읽는 동안 현재 문서를 유지하고, 테마 배경과 140ms 교차 전환으로 흰 화면 깜빡임 방지
- 문서만 남기는 집중 모드와 선택적 시스템 바 숨김
- 왼쪽 가장자리 탐색기, 오른쪽 목차·문서 정보, 위쪽 문서 도구 패널과 역방향 스와이프 닫기
- 빠른 3회 탭·세 손가락 탭·좌우상단 가장자리 스와이프를 동작에 자유롭게 연결하는 제스처 바인딩
- 이미 사용 중인 제스처를 고르면 확인 후 기존 동작을 `지정 안 함`으로 되돌리는 중복 방지
- 문서 종류별 핀치, 진동, 외부 앱 열기 버튼, 영상 동작을 개별 설정하고 DataStore에 저장
- 설정에서 한국어·영어를 즉시 전환하고 재실행 후에도 유지(저장값이 없으면 한국어가 기본값)
- 이미지 20MB·PDF 50MB 크기 제한, PDF 헤더 검증, Office ZIP 경로·항목 수·압축 해제 크기 검증
- 시스템 라이트/다크 테마와 Markdown 본문 너비 저장
- 회전·분할 화면을 포함한 실시간 적응형 레이아웃

## 화면 크기 정책

기기 종류 문자열이 아니라 현재 앱 창의 실제 dp 크기를 사용합니다.

| 조건 | 구성 |
| --- | --- |
| 폭 600dp 미만 또는 높이 480dp 미만 | 탐색기와 문서를 한 화면씩 표시 |
| 폭 600–839dp | 2분할, 260dp 탐색기 + 문서 |
| 폭 840–1199dp | 2분할, 300dp 탐색기 + 문서 |
| 폭 1200dp 이상 | 3분할, 320dp 탐색기 + 문서 + 280dp 목차 |

Markdown 목차는 2분할 이하에서 하단 시트로 표시됩니다. 따라서 스마트폰 회전, 태블릿 세로/가로, 멀티 윈도우에서도 같은 코드가 창 크기에 맞춰 다시 배치됩니다.

집중 모드에서는 앱 바·탐색기·목차·문서 헤더가 접히고 문서만 표시됩니다. 기본 설정에서는 가장자리 패널 제스처가 집중 모드에서만 동작합니다. Android 시스템 뒤로 가기는 열린 패널, 집중 모드, compact 문서 화면 순서로 닫습니다.

## 제스처 바인딩 기본값

| 앱 동작 | 기본 제스처 |
| --- | --- |
| 집중 모드 전환 | 빠른 3회 탭 |
| 탐색기 전환 | 왼쪽 가장자리에서 안쪽으로 스와이프 |
| 목차·문서 정보 전환 | 오른쪽 가장자리에서 안쪽으로 스와이프 |
| 문서 도구 전환 | 위쪽 가장자리에서 안쪽으로 스와이프 |
| 외부 앱으로 열기 | 지정 안 함 |

각 동작의 드롭다운에서 `지정 안 함`, 빠른 3회 탭, 세 손가락 탭, 좌·우·위 가장자리 스와이프를 선택합니다. 이미 다른 동작에 연결된 제스처를 선택하면 현재 연결을 알려 주는 확인 창이 먼저 뜨며, 승인할 때만 새 동작으로 옮겨집니다. 빠른 3회 탭은 Android 확대 접근성 기능, 세 손가락 제스처는 TalkBack과 충돌할 수 있습니다.

핀치는 바인딩 대상이 아니라 문서별 토글입니다. 기본적으로 Markdown만 꺼져 있고 이미지·PDF·Office·HTML·영상은 켜져 있습니다. 영상 핀치는 화면 맞춤/채우기를 전환하고 나머지는 확대/축소에 사용됩니다. 가장자리 제스처는 기본적으로 집중 모드에서만 받지만 설정에서 항상 사용하도록 바꿀 수 있습니다.

## Office와 HTML 범위

- DOCX와 PPTX는 앱 안에서 보기 전용으로 렌더링합니다. 편집, 매크로, PPT 애니메이션·전환, 암호화 문서, Word/PPT와 완전히 같은 레이아웃 재현은 지원하지 않습니다.
- 구형 바이너리 DOC와 PPT는 앱 내부 변환기를 넣지 않고 외부 앱으로 전달합니다.
- 문서 우측 상단의 외부 앱 열기 버튼은 모든 형식에 사용할 수 있고 설정에서 숨길 수 있습니다.
- HTML은 오프라인 문서 보기 용도입니다. 원격 탐색·스크립트·iframe·폼 제출을 차단하고, 사용자가 선택한 SAF 트리 안의 상대 리소스만 제공합니다.
- DOCX 렌더러 `docx-preview`와 PPTX 렌더러 `@aiden0z/pptx-renderer`는 모두 Apache-2.0 오픈소스이며 유료 SDK나 클라우드 변환 서버를 사용하지 않습니다.

영상 컨테이너 확장자를 인식하더라도 실제 재생 가능 여부는 기기의 디코더와 파일 안의 영상·오디오 코덱에 따라 달라집니다. 호환성이 가장 넓은 배포 형식은 일반적으로 MP4 컨테이너의 H.264 영상과 AAC 오디오입니다.

## SMB 네트워크 폴더

상단의 서버 모양 버튼에서 SMB 연결을 등록합니다. 현재 테스트 서버 값은 다음과 같이 기본 입력되어 있습니다.

- 서버: `100.69.138.65`
- 포트: `445`
- 공유 이름: `n100-share`
- UNC 표기: `\\100.69.138.65\n100-share`

사용자 이름과 비밀번호를 입력하고 `저장 후 연결`을 누르면 됩니다. 계정을 비워 두면 게스트 로그인을 시도하지만 현재 서버는 게스트 로그인을 거부하므로 저장된 SMB 계정이 필요합니다. 비밀번호는 연결 URI나 일반 설정 JSON에 넣지 않고 Android Keystore의 AES-GCM 키로 암호화해 저장합니다.

SMB 기능 자체는 Tailscale 전용이 아닙니다. 같은 화면에 일반 LAN IP나 DNS 이름을 입력해도 됩니다. 다만 `100.69.138.65`는 Tailscale 주소이므로 태블릿의 Tailscale VPN이 연결되어야 접근됩니다. SMB 포트 445를 공용 인터넷에 직접 노출하지 말고, 외부에서는 Tailscale 같은 사설 오버레이 네트워크나 신뢰할 수 있는 VPN 안에서만 사용합니다.

SMB 문서는 SAF 문서와 같은 렌더러로 Markdown·이미지·PDF·DOCX·PPTX·HTML을 엽니다. 영상은 SMB 파일을 모두 내려받지 않고 Media3가 필요한 위치를 임의 읽기하여 재생·탐색합니다. 외부 앱으로 열 때만 파일을 앱 캐시에 임시 복사하고 `FileProvider` 읽기 권한으로 전달합니다. SMB 서명 요구는 기본으로 켜져 있고, 서버가 SMB 3 암호화를 지원할 때는 별도의 `SMB 암호화 요구`도 켤 수 있습니다.

## 개발 환경

프로젝트 빌드 기준은 Android Gradle Plugin 9.3.1, Gradle 9.7.1, Kotlin 2.4.10, Compose BOM 2026.08.00입니다. `minSdk 28`, `targetSdk 37`, `compileSdk 37`로 구성했습니다.

필요한 도구는 다음과 같습니다.

- Android Studio의 번들 JBR 또는 Gradle 9.7.1과 호환되는 JDK(앱 소스·바이트코드 기준은 Java 17)
- Android SDK Platform 37, Build Tools 37.0.0, Platform Tools
- Node.js 24와 npm
- 테스트용 Android 9(API 28) 이상 기기 또는 에뮬레이터

시스템 전역 Gradle 설치는 필요하지 않습니다. 저장소의 Gradle Wrapper를 사용합니다. `scripts/android-env.ps1`은 이미 유효한 `JAVA_HOME`, `STUDIO_JDK`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`를 우선 사용하고, 설정되지 않았으면 Windows의 일반적인 Android Studio와 SDK 설치 위치를 탐색합니다.

현재 개발 PC에서는 Android Studio 2026.1.3.8, API 36·37 에뮬레이터, Node.js 24.14.1로 검증했습니다. 실기기는 개발자 옵션과 USB 디버깅을 켜고, Windows가 인식하지 못할 때만 제조사 USB 드라이버를 추가합니다.

## 빌드와 실행

PowerShell에서 다음 명령으로 웹 자산 테스트·번들, Android 단위 테스트·린트, 디버그 APK 조립을 한 번에 수행합니다.

```powershell
Set-Location .\mobile\android
.\scripts\build.ps1
```

연결된 에뮬레이터에서 계측 테스트까지 실행하려면 다음을 사용합니다.

```powershell
.\scripts\build.ps1 -SkipWebInstall -WithDeviceTests
```

에뮬레이터와 APK 설치는 다음과 같습니다.

```powershell
.\scripts\start-emulator.ps1 -Device Phone
.\scripts\start-emulator.ps1 -Device Tablet
.\scripts\install-debug.ps1
```

기본은 API 37입니다. API 36 호환성을 확인하려면 `-Api 36`, 자동화에서 화면 없이 실행하려면 `-Headless -WaitForBoot`를 추가합니다.

생성 APK는 `app\build\outputs\apk\debug\app-debug.apk`에 있습니다.

축소·리소스 최적화를 적용한 릴리스 산출물은 다음 명령으로 만들 수 있습니다.

```powershell
.\gradlew.bat assembleRelease bundleRelease
```

결과는 `app\build\outputs\apk\release\app-release-unsigned.apk`와 `app\build\outputs\bundle\release\app-release.aab`입니다. 현재 두 파일은 의도적으로 정식 서명하지 않았습니다. 영구 application ID와 업로드 키를 정한 뒤 정식 AAB에 서명해야 합니다.

## 디렉터리 구조

```text
mobile/android/
├─ app/                 Compose 앱과 APK에 포함되는 viewer 자산
├─ viewer-web/          React 기반 Markdown·Mermaid·이미지·PDF 렌더러와 웹 제스처 감지
├─ samples/             폴더·상대 링크·SVG·Mermaid 검증 자료
└─ scripts/             환경 설정, 빌드, 에뮬레이터, 설치 스크립트
```

`viewer-web/src`를 수정한 뒤에는 `npm run build`를 실행해야 `app/src/main/assets/viewer`가 갱신됩니다. 일반 빌드 스크립트는 이 과정을 자동 수행합니다.

## 생성 파일과 Git 정책

프로젝트의 `.gitignore`는 Gradle·Kotlin 캐시, 모든 모듈의 `build` 디렉터리, Android Studio 개인 설정, `local.properties`, Node 의존성과 임시 번들, APK/AAB, 서명 키와 로컬 비밀 설정을 제외합니다.

다음 파일은 생성되거나 환경에 따라 달라지더라도 의도적으로 다르게 취급합니다.

- `gradle/wrapper/gradle-wrapper.jar`와 `gradle-wrapper.properties`는 재현 가능한 빌드를 위해 추적합니다.
- `viewer-web/package-lock.json`은 웹 의존성 버전을 고정하므로 추적합니다.
- `app/src/main/assets/viewer`는 오프라인 WebView 번들이며 APK 빌드에 필요하므로 추적합니다. `viewer-web/dist`와는 역할이 다릅니다.
- `*.jks`, `*.keystore`, `keystore.properties`, `signing.properties`, `.env*`는 커밋하지 않습니다. 공유가 필요한 값은 비밀이 없는 예제 파일만 별도로 만듭니다.

## 보안 경계

- 저장소 전체 권한을 요청하지 않고 사용자가 선택한 SAF 트리만 읽습니다.
- SMB 자격 증명은 Android Keystore로 암호화하고, 문서 URI에는 무작위 연결 ID와 공유 내부 경로만 기록합니다.
- SMB는 읽기 전용 접근 마스크만 사용하고 SMB 1을 지원하지 않으며, 서명 요구를 기본으로 적용합니다.
- 상대 경로의 `..`가 선택 루트 밖으로 나가면 거부합니다.
- WebView는 `file://`·직접 `content://` 접근을 끄고 `WebViewAssetLoader`의 HTTPS 원본만 사용합니다.
- JavaScript 브리지는 선택된 트리의 문서 URI만 제공합니다.
- 외부 최상위 탐색은 차단하고 HTTP(S)·mailto 링크는 Android 기본 앱으로 전달합니다.
- Markdown 안의 원시 HTML은 렌더링하지 않으며 Mermaid는 `securityLevel: strict`입니다.
- HTML 문서는 별도 WebView에서 JavaScript·브리지·원격 탐색·폼·frame·object를 차단합니다.
- DOCX/PPTX는 렌더링 전에 ZIP 경로 순회, 과도한 항목 수와 압축 해제 크기를 검사합니다.
- 평문 HTTP와 혼합 콘텐츠는 차단합니다.

## 문제 해결

- JDK 또는 SDK를 못 찾으면 `JAVA_HOME`과 `ANDROID_HOME`을 설정한 뒤 새 PowerShell에서 다시 실행합니다. Android Studio의 번들 JBR도 사용할 수 있습니다.
- `adb devices -l`에 기기가 없으면 USB 연결 모드를 파일 전송으로 바꾸고, USB 디버깅 승인 창을 확인한 뒤 데이터 전송을 지원하는 케이블로 다시 연결합니다.
- 웹 뷰어 코드를 바꿨는데 앱에 반영되지 않으면 `scripts/build.ps1`을 다시 실행합니다. 이 스크립트가 `viewer-web`을 빌드해 APK 자산을 갱신합니다.
- SMB 연결이 실패하면 서버 주소와 공유 이름을 별도 칸에 입력했는지, 포트 445 접근이 가능한지, LAN 또는 VPN이 연결됐는지 먼저 확인합니다. 공용 인터넷에 445 포트를 직접 노출하지 않습니다.
- Android Studio와 명령줄의 SDK가 다르면 `ANDROID_HOME`을 Studio의 SDK 경로와 일치시킵니다.

## 출시 전에 사용자 결정이 필요한 항목

현재 애플리케이션 ID는 개발용 `com.example.markdownviewer`입니다. Google Play에 최초 업로드하기 전에 영구 패키지 ID를 정해야 하며, 이후에는 바꾸기 어렵습니다. 또한 앱 표시 이름·아이콘, 릴리스 서명 키 보관 위치, 버전 정책, 개인정보처리방침 URL이 필요합니다.

현재 `minSdk 28`, `targetSdk 37`, `compileSdk 37`이며 디버그 APK는 바로 설치할 수 있습니다. 2026년 8월 31일부터 새 앱과 업데이트에 요구되는 Android 16(API 36) 이상 대상 정책도 충족합니다. 릴리스 AAB 생성과 Play Console 등록은 위 출시 정보가 확정된 뒤 진행합니다.

## 검증 기록

- JVM 단위 테스트: 창 분류, 상대 경로 보안, Markdown 목차, 영상 종류·상태, 제스처 중복 이동, Office ZIP 검증
- 웹 단위 테스트: 문서 종류, 외부/로컬 링크, 제스처 방향, DOCX/PPTX 참조 처리
- Compose 계측 테스트 2건: 앱 시작·탐색기·폴더 액션, 설정 창과 스크롤된 제스처 섹션
- Pixel 9 API 36: SAF, 트리, Markdown/GFM, 로컬 SVG, Mermaid, 코드 강조, 상대 링크, 이미지, PDF 확인
- Pixel Tablet API 36: 1280×800dp 3분할과 800×1280dp 2분할 확인
- Pixel Tablet API 37: SAF 실제 폴더 선택, 3분할/2분할/compact 전환, WebView가 제거된 뒤 Markdown 스크롤 복원 확인
- API 37 에뮬레이터: Compose 계측 테스트와 강제 종료 후 SAF 권한 복원 확인
- R8 축소 릴리스 APK: 비디버그 설치·실행과 JavaScript 브리지/문서 상태 복원 확인
- Lenovo TB710FU, Android 16: `install -r` 업데이트, 1280×800dp 3분할, 집중 모드, 좌·우·위 패널 열기와 역방향 닫기, 빠른 3회 탭 설정, SAF 권한 재복원 확인
- Lenovo TB710FU의 실제 H.264 화면 녹화 MP4: 프레임 표시, 재생과 시간 진행, 10초 이동·맞춤/채우기 UI, 치명적 로그 0건 확인 후 테스트 파일 제거
- Lenovo TB710FU: DOCX 본문·표, PPTX 2개 슬라이드, HTML 본문·로컬 CSS·로컬 이미지 렌더링 확인. HTML 안의 스크립트가 실행되지 않는 것도 WebView DOM으로 확인
- Lenovo TB710FU 다크 테마: 실제 Markdown 파일 간 전환에서 WebView 배경색 유지와 View Transition 실행, 치명적 로그 0건 확인
- Lenovo TB710FU + `\\100.69.138.65\n100-share`: SMB 서명 연결, 재귀 트리, Markdown·PDF·PNG 렌더링, 임의 위치 읽기, 외부 앱용 FileProvider 전달 확인
- SMB 실기기 계측 테스트 3건: 지원 문서 탐색, 영상용 임의 읽기, 외부 앱용 임시 파일의 PDF 헤더 확인
- Lenovo TB710FU: 한국어 기본 UI, 설정에서 영어 전환, PDF WebView 영어 도구, 강제 종료 후 영어 유지와 한국어 복귀 확인
