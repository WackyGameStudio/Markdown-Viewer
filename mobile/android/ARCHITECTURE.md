# Android 아키텍처 결정 기록

기준일: 2026-08-23

## 결론

이 앱은 **Kotlin·Jetpack Compose 네이티브 셸 + APK 내부의 제한된 WebView 렌더러**로 개발한다. Wails v3를 그대로 사용하지 않는다.

Wails v3로 Android 앱을 만드는 것은 이제 기술적으로 가능하다. 공식 모바일 문서에 따르면 Android WebView와 `WebViewAssetLoader`를 사용하고, Go 코드를 NDK로 `libwails.so`에 컴파일하며, Storage Access Framework 폴더 열기 등도 제공한다. 다만 Wails 프로젝트가 모바일 지원을 아직 **experimental**로 분류하며 데스크톱 베타의 호환성 보장 범위에도 포함하지 않는다. 따라서 신규 Android 앱의 장기 기반으로 채택하기에는 현재 위험이 더 크다.

이 프로젝트는 데스크톱 Go 백엔드의 재사용보다 다음 Android 고유 기능의 품질이 더 중요하다.

- SAF 트리 권한의 획득·영구 보존·해제와 재귀 문서 탐색
- 창 크기와 회전·분할 화면에 즉시 반응하는 스마트폰/태블릿 UI
- Android 생명주기, 접근성, 뒤로 가기, 외부 인텐트
- WebView 원본·브리지·파일 접근을 직접 제한하는 보안 정책
- Play 배포, App Bundle, 서명, 백업 제외 정책

네이티브 셸은 이 기능을 Android API로 직접 제어한다. 반면 Markdown, Mermaid, 구문 강조, PDF.js처럼 웹 생태계가 강한 렌더링 부분만 로컬 WebView에 유지하므로, 전부 네이티브로 다시 만들 때의 비용도 피한다.

## 대안 비교

| 방식 | 장점 | 이 앱에서의 비용·위험 | 판단 |
| --- | --- | --- | --- |
| Kotlin + Compose + 로컬 WebView | Android API 직접 사용, 적응형 UI·접근성·SAF 제어, 기존 JS 렌더러 재사용 | Kotlin과 TypeScript 두 계층을 관리 | **채택** |
| Wails v3 모바일 | 데스크톱 Go 서비스와 웹 UI를 크게 공유, Android 빌드·SAF 기본 제공 | 모바일 experimental, Go 1.25·NDK·Gradle 계층 추가, Android 세부 동작에서 프레임워크 성숙도 위험 | 데스크톱 Go 코드 공유가 최우선일 때 재검토 |
| Capacitor | 기존 웹 앱을 빠르게 Android/iOS 컨테이너로 전환, 플러그인 API | 파일 트리·적응형 탐색 UI·WebView 보안에 결국 Kotlin 플러그인/커스텀 셸 필요 | 웹 UI 공유가 최우선인 앱에 적합 |
| React Native | Android/iOS UI와 TypeScript 로직 공유, 큰 생태계 | SAF와 고급 WebView 제어는 네이티브 모듈 필요, 현재 데스크톱 구조와 직접 공유 이점이 작음 | iOS 동시 개발이 확정될 때 후보 |
| Flutter | 일관된 크로스플랫폼 적응형 UI와 도구 | Dart UI 재작성, Mermaid·PDF.js는 다시 WebView에 넣게 됨 | Flutter 조직 역량이 이미 있을 때 후보 |
| Kotlin/Compose Multiplatform | Kotlin 비즈니스 로직·UI를 Android/iOS/desktop에 공유 가능 | Android 전용 SAF/인텐트/WebView는 플랫폼 구현이 계속 필요 | 향후 iOS가 확정되면 현재 구조에서 점진 도입 가능 |

## 현재 구조

```text
Compose UI
├─ 창 크기 분류: compact / medium / expanded / large
├─ SAF와 SMB 2/3 폴더 탐색, 최근 폴더, 즐겨찾기, 문서 기록
├─ 집중 모드, 가장자리 패널, DataStore 제스처 설정
├─ Media3 네이티브 영상 재생과 재생 위치
├─ 별도 무스크립트 WebView의 로컬 HTML 문서
└─ 생명주기와 문서별 표시 상태
        │ 제한된 메시지/데이터 경계
        ▼
로컬 WebView 렌더러
├─ Markdown + GFM + Mermaid + 코드 강조
├─ 이미지 확대·회전과 PDF.js 페이지·텍스트·링크 레이어
├─ 오픈소스 렌더러 기반 DOCX·PPTX 보기
└─ 다중 포인터·가장자리 입력을 현재 바인딩된 네이티브 동작으로 전달
```

WebView는 인터넷 서버가 아니라 `https://appassets.androidplatform.net`의 APK 자산만 로드한다. 선택한 SAF 트리의 문서만 네이티브 브리지를 통해 읽고, 직접 `file://`·`content://` 접근과 혼합 콘텐츠는 차단한다.

WebView 브리지는 임의 명령을 받지 않고 열거형으로 검증된 제스처 입력과 문서 상태만 Compose 계층에 전달한다. Compose가 DataStore에 저장된 충돌 없는 바인딩을 조회해 집중 모드, 탐색기, 상세 정보, 도구, 외부 앱 열기 중 하나를 실행한다. 영상은 WebView로 복사하지 않는다. SAF 문서는 `content://` URI를 Media3 `ExoPlayer`에 직접 넘기고, SMB 문서는 SMBJ의 임의 위치 읽기를 Media3 `DataSource`로 연결해 대용량 파일을 스트리밍한다.

SMB 연결은 SMBJ 기반의 읽기 전용 저장소 어댑터다. 문서 URI에는 자격 증명을 포함하지 않고 무작위 연결 ID만 넣으며, 암호는 Android Keystore AES-GCM으로 암호화한다. 연결 서명은 기본으로 요구하고 SMB 3 암호화는 서버별 선택 사항이다. 외부 앱 전달은 SMB 파일을 앱 캐시에 한시적으로 복사한 뒤 비공개 `FileProvider` URI에 일회성 읽기 권한을 부여한다.

DOCX·PPTX는 네이티브 계층에서 ZIP 구조와 크기 제한을 먼저 검증한 뒤 각각 Apache-2.0 라이선스의 `docx-preview`, `@aiden0z/pptx-renderer`로 렌더링한다. 구형 DOC·PPT는 외부 앱으로만 연다. HTML은 이 브리지 WebView와 분리하고 JavaScript를 완전히 끈 채 CSP와 SAF 리소스 경계를 적용한다.

## 적응형 UI 기준

기기 모델이나 “phone/tablet” 문자열을 사용하지 않고 현재 창의 dp 크기로 결정한다.

| 현재 창 | UI |
| --- | --- |
| 폭 600dp 미만 또는 높이 480dp 미만 | 탐색기/문서 단일 화면 전환 |
| 폭 600–839dp | 260dp 탐색기 + 문서 |
| 폭 840–1199dp | 300dp 탐색기 + 문서 |
| 폭 1200dp 이상 | 320dp 탐색기 + 문서 + 280dp 목차 |

이 방식은 폴더블, 태블릿의 세로/가로 회전, 데스크톱 창 모드와 분할 화면도 별도 기기 목록 없이 처리한다.

## Wails v3 재검토 조건

다음 조건이 함께 만족되면 Wails v3 모바일 전환을 다시 비교한다.

1. 공식 문서에서 Android가 experimental을 벗어나 호환성 정책에 포함된다.
2. 현재 데스크톱 Go 서비스의 공유 가치가 Kotlin 네이티브 통합 비용보다 커진다.
3. SAF 영구 폴더 권한, 렌더 프로세스 복구, Android 17 대상 빌드, AAB 서명을 실제 기기와 Play 내부 테스트에서 통과한다.
4. 스마트폰/태블릿 적응형 UI와 접근성 테스트 결과가 현재 Compose 구현 이상이다.

## 공식 참고 자료

- [Wails v3 상태](https://v3.wails.io/status/)
- [Wails v3 모바일 개요](https://v3.wails.io/guides/mobile/)
- [Android 창 크기 클래스](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Android list-detail 적응형 레이아웃](https://developer.android.com/develop/adaptive-apps/guides/list-detail)
- [Android Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider)
- [WebView 로컬 콘텐츠 보안 로딩](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)
- [Capacitor 공식 개요](https://capacitorjs.com/docs)
- [Kotlin Multiplatform 개요](https://www.jetbrains.com/kotlin-multiplatform/)

## 출시 전에 남은 결정

구현 선택과 개발 환경 준비는 완료되어 있다. 다음 항목은 제품 소유자의 값 없이는 확정할 수 없다.

- 영구 application ID(현재 임시값 `com.example.markdownviewer`)
- 스토어 표시 이름, 아이콘, 색상과 브랜드
- 릴리스 업로드 키 생성·보관·백업 방식
- 버전 정책, 개인정보처리방침 URL, Play Console 계정/배포 트랙
