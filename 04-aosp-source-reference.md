# androidx.a2ui 소스 레퍼런스: 폴더별·파일별 정리

AOSP `androidx-main` 커밋 **`ac85854`** (2026-09-02) 기준. 이 글을 쓰는 시점의 최신입니다.
(`54bad67`(09-01) 이후 커밋 8개로 두 트리에서 파일 53개가 바뀌었습니다. **`Video`와 `AudioPlayer`가 계약에 새로 합류**(`d533dcf`·`32ed0cd`)해 컴포넌트 인터페이스가 **열셋에서 열다섯**이 됐고, 이 둘은 `Image`처럼 **앱이 렌더러를 주입하는 형태**라 `materialA2uiBasicCatalogV1()`의 **필수 파라미터가 넷에서 여섯으로 늘었습니다. 기존 호출부가 전부 깨집니다.** 자식 weight도 들어왔습니다(`9fc04da`가 `WeightProperty`를, `c8e76a4`·`55652b0`이 Row/Column 구현을). 어제까지 남아 있던 `TODO(b/547495694)`·`TODO(b/547501861)`이 **하루 만에 사라졌습니다.** 스키마 쪽은 `format`·`if-then` 키워드가 생겼고(`9631b36`), 마지막으로 `7ac433e`가 **전체 코드를 ktfmt 0.64로 재포맷**해 순수 서식 변경이 diff 곳곳에 섞여 있습니다.)
로컬 사본: [`androidx-a2ui-source/`](androidx-a2ui-source/) · [`androidx-material3-a2ui-source/`](androidx-material3-a2ui-source/).
두 사본 모두 GitHub 미러(`androidx/androidx`)에서 sparse checkout으로 받았고, git tree 해시를 각 폴더의 `SOURCE_COMMIT.txt`에 적어 두었습니다.

> 최근 주요 상류 변경사항:
>
> | 언제(UTC) | 무엇이 |
> |---|---|
> | 09-02 21:50 | `7ac433e` **전체 코드를 ktfmt 0.64로 재포맷.** 동작 변화 0. 이 커밋 이후의 diff를 읽을 때는 서식 변경과 실제 변경을 분리해야 합니다. 이 저장소의 Slider 핀도 같은 재포맷을 따라갔습니다 |
> | 09-02 18:25 | `d533dcf` **`Video`가 계약에 합류.** `A2uiBasicCatalogV1.Video`(프로퍼티는 `url` 하나)와 `catalog/MaterialA2uiBasicCatalogV1Video.kt`(64줄) 신설. **`Image`와 같은 렌더러 주입 방식**입니다. `A2uiVideoRenderer` fun interface를 앱이 구현해 넘기고(Media3/ExoPlayer 등), 실패는 `onError` → `reportError`로 돌아옵니다. material3-a2ui 자체는 미디어 라이브러리에 의존하지 않습니다 |
> | 09-02 16:58 | `32ed0cd` **`AudioPlayer`가 계약에 합류.** `A2uiBasicCatalogV1.AudioPlayer` + `catalog/MaterialA2uiBasicCatalogV1AudioPlayer.kt`(78줄). `Video`와 같은 구조(`A2uiAudioPlayerRenderer` 주입) |
> | 09-02 13:43 | `c8e76a4`·`55652b0` **Row·Column 자식 weight 지원.** 어제 남겨졌던 `TODO(b/547495694)`(Row)·`TODO(b/547501861)`(Column)이 **하루 만에 닫혔습니다.** 이제 두 컨테이너의 TODO가 하나도 없습니다 |
> | 09-02 13:08 | `9fc04da` **`WeightProperty`가 계약 컴포넌트들에 추가.** `Text`·`Icon`·`Image`·`Video` 등 자식이 될 수 있는 컴포넌트가 공통으로 `weight`를 갖습니다. 부모 Row/Column이 읽어 `Modifier.weight()`로 매핑 |
> | 09-02 10:37 | `9631b36` **스키마에 `format`·`if-then` 키워드 지원.** `A2uiSchemaKeyword.Format`과 `A2uiSchemaKeyword.IfThen` 신설, `A2uiCoreSchemaValidator`가 64줄 늘며 검증까지. `DateTimeInput`이 첫 사용처입니다 |
> | 08-30 14:18 | `524f473` **레거시 `BasicTextField` deprecate 대응.** `MaterialTextFieldComponent`에 두 줄 |
> | 09-01 22:45 | `6198d65` **`Slider`가 계약에 합류.** `A2uiBasicCatalogV1.Slider` 인터페이스(116줄)가 생기고 `MaterialSliderComponent.kt`(177줄)가 `catalog/MaterialA2uiBasicCatalogV1Slider.kt`(116줄, internal)로 이사했습니다. `min > max`면 그리지 않고 `reportError`하는 방어는 그대로 남았고(`TODO(b/549060875)`, 로딩 상태로 되돌릴지 에러를 띄울지 미정), `steps`·`coerceIn`·눈금 숨김(`drawTick = no-op`)도 유지됩니다. Robolectric 89줄 + androidTest 659줄 신설 |
> | 09-01 22:04 | `f415ef1` **`CheckBox`가 계약에 합류.** `MaterialCheckBoxComponent.kt`(116줄) 삭제 → `catalog/MaterialA2uiBasicCatalogV1CheckBox.kt`(74줄). 계약 쪽에 92줄이 들어갔습니다. **입력 컴포넌트가 계약에 들어간 첫 사례**. Robolectric 73줄 + androidTest 672줄(이번 묶음에서 가장 큰 테스트) 신설 |
> | 09-01 17:46 | `77c3564` **`Divider`가 계약에 합류.** `MaterialDividerComponent.kt`(59줄) 삭제 → `catalog/MaterialA2uiBasicCatalogV1Divider.kt`(39줄). 세 이사 중 첫 번째이고, `MaterialA2uiBasicCatalogV1Defaults.divider`가 함께 생겼습니다 |
> | 09-01 16:29 | `bcfdc1c` **[Row] `justify = stretch` 지원.** `Stretch`일 때 자식에 `Modifier.weight(1f)`를 걸어 남는 가로 공간을 균등 분배합니다. 09-01 12:38 시점에 `Start`와 동일하게 처리되던 값이 이제 실제 의미를 갖습니다. `spaceAround`·`spaceEvenly`·`stretch` androidTest 189줄 추가 |
> | 09-01 16:29 | `9975123` **[Column] `justify`/`align` 실제 구현.** Row와 대칭입니다. `justify`는 `Arrangement.spacedBy(8.dp, Alignment.Top/CenterVertically/Bottom)` 및 `SpaceBetween`/`SpaceAround`/`SpaceEvenly`로, `align`은 `Alignment.Start`/`CenterHorizontally`/`End`로. `align = Stretch`는 Column에 `fillMaxWidth()`·자식에 `fillMaxWidth()`, `justify = Stretch`는 자식에 `weight(1f)`. androidTest 616줄 신설. `TODO(b/546052129)`가 사라지고 대신 자식별 weight를 다루는 `TODO(b/547501861)`(Row는 `b/547495694`)이 남았습니다 |
> | 09-01 12:38 | `b29c38a` **[Row] `justify`/`align` 실제 구현.** `MaterialA2uiBasicCatalogV1Row`의 `TODO(b/546052129)`가 사라지고 두 스키마 프로퍼티가 Compose로 매핑됩니다. `justify`는 `Arrangement.spacedBy(8.dp, Alignment.Start/CenterHorizontally/End)` 및 `SpaceAround`/`SpaceBetween`/`SpaceEvenly`로(`Stretch`는 지금 `Start`와 동일), `align`은 `Alignment.Top`/`CenterVertically`/`Bottom`으로. `align = Stretch`만 특수 처리합니다. Row에 `height(IntrinsicSize.Min)`, 자식에 `fillMaxHeight()`를 걸어 세로로 늘립니다. `RowChildItem`이 오브젝트 안 private 함수로 들어가고 `observeA2uiComponentState` 호출이 호출부로 올라갔습니다. androidTest 441줄 신설(`justify_*` 4개 + `align_*` 4개). 이때는 `Column`이 아직 그대로였지만, 09-01 16:29의 `9975123`이 곧바로 따라잡았습니다 |
> | 08-31 23:18 | `39d77d7` **Robolectric 설정을 `enableRobolectric()`으로 일원화.** `compose/compose-runtime/build.gradle`에서 `testImplementation(libs.robolectric)` 한 줄이 빠졌습니다(같은 파일 64번째 줄의 `enableRobolectric()`이 이미 의존성과 JVM 플래그를 다 걸어 줍니다) |
> | 08-31 23:14 | `743f30b` **build.gradle에서 `minSdk 24` 선언 제거.** AOSP 기본값과 같아서 명시할 필요가 없다는 이유입니다. `a2ui-engine`·`a2ui-model`·`compose-runtime`·`compose-ui`·`integration-tests/testapp` 5개 파일. **최소 SDK가 올라간 게 아니라 선언만 사라진 것** |
> | 08-28 13:41 | `ff3409a` **[번역] `DateTimeInput` 문자열 4개(`button_cancel`·`button_ok`·`select_date`·`select_time`)가 번역 파이프라인에 등록.** 84개 로케일 파일이 갱신됐지만 **실제 번역은 아직 하나도 없습니다.** `en-rCA`만 영어 원문이 채워졌고 나머지 83개는 `<!-- no translation found --><skip />` 뿐이라, 지금 `DateTimeInput` 다이얼로그는 모든 언어에서 영어로 뜹니다. `a2ui/` 트리는 이 커밋에서 한 글자도 안 바뀌었습니다 |
> | 08-27 21:18 | `0beddaf` **`Tabs`가 계약에 합류.** `MaterialTabsComponent`가 삭제되고 `catalog/MaterialA2uiBasicCatalogV1Tabs.kt`(internal)로 이사 |
> | 08-27 19:06 | `dc42691` **`List`가 계약에 합류.** `MaterialListComponent` 삭제 → `catalog/MaterialA2uiBasicCatalogV1List.kt` |
> | 08-27 17:09 | `c3118ed` **`Icon`이 계약에 합류.** `MaterialIconComponent` 삭제 → `catalog/MaterialA2uiBasicCatalogV1Icon.kt` |
> | 08-27 17:02 | `930f335` **[Action Interceptor] `A2uiComponentScope.ProvideActionInterceptor` 신설.** 자손이 `dispatchAction`한 액션을 조상이 가로채는 CompositionLocal 체인. 안쪽부터 바깥쪽으로 흐르고 `true`를 반환하면 소비, 전부 `false`면 서피스로 내려갑니다. 모달처럼 자식 버튼의 액션을 로컬 상태로 처리해야 하는 컨테이너를 위한 API |
> | 08-27 16:01 | `c118587` **a2ui 모듈들이 고정(pinned) 버전 의존을 우선하도록 build.gradle 정리** |
> | 08-27 13:40 | `70ddaea` **`DateTimeInput`이 계약에 합류.** `catalog/MaterialA2uiBasicCatalogV1DateTimeInput.kt`(330줄) 신설. M3 `DatePicker`/`TimePicker` 다이얼로그, ISO 8601 `value`, `enableDate`/`enableTime`/`min`/`max`/`label` |
> | 08-26 | **[Inline Catalog] `A2uiInlineCatalog` 및 인라인 카탈로그 스키마 직렬화 도입.** `A2uiClientCapabilities`가 `inlineCatalogs`를 전달받아 클라이언트 능력 광고에 카탈로그 전체 JSON Schema를 직접 포함할 수 있게 됨 |
> | 08-26 | **[Catalog Serializer] `A2uiCoreCatalogSerializer` 개편 및 `toJsonSchemaString()` / `toJsonSchemaMap()` API.** 기존의 전역 `A2uiCoreCatalog.toJsonSchema()` 확장 함수가 `A2uiCatalog.toJsonSchemaString()` / `toJsonSchemaMap()`으로 공식 제공됨 |
> | 08-20 19:27 | `7081e7f` **`Card`가 계약에 합류.** `A2uiBasicCatalogV1.Card` 인터페이스가 생기고 `MaterialCardComponent`가 삭제됨 |
> | 08-20 19:21 | `30a6b57` **[Slider] 무상태 `Slider`/`RangeSlider` 오버로드 deprecate.** `MaterialSliderComponent`가 새 `SliderState(trackRange = ...)` API로 이사함 |
>
> **이 소스는 읽기용 스냅샷이 아니라 실제로 컴파일됩니다.** `a2ui-compose-labs/androidx-a2ui/` 모듈이 위 두 폴더를 소스 디렉터리로 그대로 가리키고, 앱의 "9. Two dialects" 데모가 그 모듈에 의존합니다. 포크가 아니라 원본 그대로이고, 폴더를 다시 동기화하면 다음 빌드에 반영됩니다.

- 원본: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/a2ui/>
- 미러: <https://github.com/androidx/androidx/tree/androidx-main/a2ui> (읽기 전용. a2ui는 GitHub PR 대상 목록에 없음)

---

## 0. 전체 지도

```
frameworks/support/
├── a2ui/                                   group: androidx.a2ui / androidx.a2ui.compose
│   ├── a2ui-model/                         프로토콜 정의: "무엇을 주고받는가"
│   ├── a2ui-engine/                        엔진: "받은 것을 어떻게 처리하는가"
│   ├── compose/
│   │   ├── compose-runtime/                Compose 상태 매핑: "상태를 어떻게 보관하는가"
│   │   ├── compose-ui/                     컴포넌트 계약: "컴포넌트란 무엇인가"
│   │   └── compose-ui-testing/             테스트 하네스
│   └── integration-tests/testapp/          내부 검증용 앱 (빈 껍데기)
└── compose/material3/material3-a2ui/       group: androidx.compose.material3
                                            실제 M3 컴포넌트: 18개 중 16개
```

| 모듈 | Maven 좌표 | main | test | 성격 |
|---|---|---:|---:|---|
| `a2ui-model` | `androidx.a2ui:a2ui-model` | 66파일 / 6,017줄 | 54파일 / 6,810줄 | 순수 Kotlin |
| `a2ui-engine` | `androidx.a2ui:a2ui-engine` | 20 / 2,968 | 14 / 5,775 | 순수 Kotlin + coroutines |
| `compose/compose-runtime` | `androidx.a2ui.compose:compose-runtime` | 14 / 3,151 | 15 / 6,721 | Compose runtime 의존 |
| `compose/compose-ui` | `androidx.a2ui.compose:compose-ui` | 5 / 2,111 | 26 / 7,857 | Compose UI 의존 |
| `compose/compose-ui-testing` | `androidx.a2ui.compose:compose-ui-testing` | 4 / 859 | 4 / 1,699 | 테스트 전용 |
| `compose/material3/material3-a2ui` | `androidx.compose.material3:material3-a2ui` | 77 / 8,154 | 21 / 10,221 | **quarantine(미출시)** |

material3-a2ui의 77개 중 **60개는 `icons/` 아래 벡터 아이콘**입니다(08-24 추가). 아이콘을 빼면 17파일 / 2,426줄입니다.

테스트 줄 수가 본문보다 많습니다(합계 본문 23,260 / 테스트 39,083). 프로토타입이 아니라 출시를 준비하는 코드라는 신호입니다. 특히 `compose-ui`는 본문 2,111줄에 테스트 7,857줄. 계약이 커질 때마다 테스트가 먼저 붙습니다.

### 앱에서 직접 써보고 알게 된 것

| | |
|---|---|
| 검증 | **없습니다.** `A2uiCheckableSchema`가 a2ui-model에 있지만 읽는 컴포넌트도, 평가하는 런타임도 없습니다. `validationRegexp`는 아예 존재하지 않습니다. 직접 배선해야 합니다. 동적 프로퍼티에 `{"call": "required", ...}`를 물리면 엔진이 함수는 실행해 줍니다 |
| 액션 페이로드 | `{"event": {"name": ..., "context": ...}}` 또는 `{"functionCall": {"call": ..., "args": ...}}`로 **한 겹 더 감쌉니다.** v1.0은 벗겨진 `{"name": ..., "context": ...}` |
| `collectMessages()` | AOSP 샘플은 `Dispatchers.Default`에서 돌리라고 하는데, 그러면 배경 스레드가 만든 Compose 상태를 진행 중인 recomposition이 읽다가 `Reading a state that was created after the snapshot was taken`으로 죽습니다. composition 자신의 디스패처에 두면 프레임 사이에 돌아서 문제가 없습니다 |
| 빌드 | material3-a2ui가 `MaterialTheme.motionScheme`를 쓰는데 이건 material3 **1.5.0-alpha26**에서 공개됩니다. 그 버전이 Compose 1.12.0-beta01을 끌어오고, 그건 AGP 9.1 이상 + compileSdk 37을 요구합니다 |

**의존 방향은 한 줄로 흐릅니다:**

```
a2ui-model ← a2ui-engine ← compose-runtime ← compose-ui ← material3-a2ui
└────────── Compose 없음 ──────────┘ └────────── Compose 있음 ──────────┘
```

`a2ui-model`과 `a2ui-engine`에는 `androidx.compose` import가 **한 줄도 없습니다**
(`grep -rl androidx.compose a2ui-model/src/main a2ui-engine/src/main` → 결과 없음).
프로토콜·검증·상태 관리가 UI 툴킷과 분리돼 있어서, 같은 엔진으로 View든 다른 렌더러든 붙일 수 있습니다.
Compose가 처음 등장하는 지점이 `compose-runtime`입니다.

---

## 1. `a2ui-model/`: 프로토콜 정의

에이전트와 렌더러가 주고받는 **데이터의 모양**과, 그것을 설명하는 **스키마 체계**, 그리고 **내장 함수**.
이 모듈만 알면 A2UI 메시지를 직접 만들거나 파싱할 수 있습니다.

### 1.1 `protocol/`: 와이어 타입 (10파일)

| 파일 | 무엇인가 |
|---|---|
| `A2uiServerToClientMessage.kt` | **에이전트 → 렌더러 메시지 전부.** sealed interface 1개 + 클래스 4개 |
| `A2uiClientToServerMessage.kt` | **렌더러 → 에이전트 메시지 전부.** sealed interface + `A2uiClientEventMessage`, `A2uiClientErrorMessage` |
| `A2uiComponentPayload.kt` | 컴포넌트 하나의 원본 페이로드 (`id`, `type`, `properties: Map<String, Any?>`) |
| `A2uiDataPath.kt` | JSON Pointer(RFC 6901) 파서 + 경로 결합 |
| `A2uiUserAction.kt` | 사용자 상호작용. `A2uiEventAction`(서버로) vs `A2uiFunctionCallAction`(로컬 실행) |
| `A2uiException.kt` | `A2uiValidationException`(message, **path**) / `A2uiRuntimeException`(message, context) |
| `A2uiExecutionContext.kt` | 컴포넌트 스코프 안에서의 환경 접근·동적 평가·함수 실행 인터페이스 |
| `A2uiClientCapabilities.kt` | 능력 광고 페이로드. `{"a2uiClientCapabilities": {"v0.9": {"supportedCatalogIds": [...]}}}` |
| `A2uiClientDataModel.kt` | `sendDataModel = true`인 서피스의 데이터 트리를 아웃바운드 메시지에 첨부 |
| `A2uiProtocolConstants.kt` | `PROTOCOL_VERSION = "v0.9"`, 내부용 `GLOBAL_SURFACE_ID = "__global__"` |

**핵심 코드: 프로토콜 전체가 이 5줄입니다**

```kotlin
public sealed interface A2uiServerToClientMessage { public val surfaceId: String }

class A2uiCreateSurfaceMessage(surfaceId, catalogId, theme, shouldSendDataModel)
class A2uiUpdateComponentsMessage(surfaceId, components: List<A2uiComponentPayload>)
class A2uiUpdateDataModelMessage(surfaceId, path = "/", value: Any? = null)
class A2uiDeleteSurfaceMessage(surfaceId)
```

읽을 때 놓치기 쉬운 것 세 가지:

1. **`surfaceId`가 인터페이스에 있습니다.** 라우팅이 문자열 검사가 아니라 타입 시스템으로 결정됩니다.
2. **`updateDataModel`의 `value = null`은 "변경 없음"이 아니라 "그 경로를 삭제"입니다.** KDoc 원문: *"If null (or omitted in JSON), the key/path is deleted from the data model."*
3. **`A2uiDataPath`의 KDoc이 RFC 6901에서 벗어나는 지점을 명시합니다.** 빈 문자열과 `"/"`는 둘 다 루트, 끝 슬래시는 *정확히 하나만* 제거합니다. 그 이유까지 주석에 있습니다("to maintain parsing parity with other A2UI renderers"). 스펙의 모호함이 코드 주석으로 해결된 사례라, 직접 렌더러를 쓸 때 적합성 테스트로 쓸 수 있습니다.

`div` 연산자가 경로 결합을 담당하고, `other`가 절대경로면 기준 경로를 대체합니다. 이게 Child Scope의 기반입니다.

```kotlin
public operator fun div(other: A2uiDataPath): A2uiDataPath =
    if (other.isAbsolute) other else this / other.path
```

이 클래스들은 전부 `data class`가 아니라 `equals`/`hashCode`/`toString`을 손으로 씁니다.
androidx의 API 안정성 규칙 때문입니다(data class는 `copy()`를 공개 API로 노출시킴).

### 1.2 `processor/`: 파싱과 처리 계약 (6파일)

| 파일 | 무엇인가 |
|---|---|
| `A2uiMessageProcessor.kt` | 프로세서 인터페이스. `processMessage`, `processError`, `collectMessages`, 확장 `processInput` |
| `A2uiMessageParser.kt` | `fun interface A2uiMessageParser<T> { fun parse(input: T): A2uiServerToClientMessage }` |
| `A2uiJsonMessageParser.kt` | JSON 문자열 → 메시지. **버전 게이트가 여기 있음** |
| `A2uiJsonReader.kt` | 스트리밍 토큰 리더 인터페이스 + `A2uiJsonToken` enum. 플랫폼이 구현 |
| `A2uiSurfaceModel.kt` | 서피스 인터페이스(`id`만). 구현은 엔진의 `A2uiCoreSurfaceModel` |
| `A2uiActionInterceptor.kt` | `fun interface`. 액션 미들웨어 체인. **null 반환 = 액션 취소** |

**핵심 코드: 파서가 같은 JSON을 두 번 읽는 이유**

```kotlin
override fun parse(input: String): A2uiServerToClientMessage = try {
    // Parse the JSON twice: first to extract and validate the protocol version,
    // ensuring it is supported before we try to parse specific protocol fields.
    val version = detectVersion(jsonReaderProvider(input))
    validateVersion(version)
    jsonReaderProvider(input).use { reader -> parseMessage(reader) }
} catch (e: A2uiException) { throw e
} catch (e: Exception) {
    throw A2uiException.A2uiValidationException("Malformed JSON message: ${e.message}", "/")
}

private val SUPPORTED_VERSIONS = listOf("v0.9", "v0.9.1")   // ← line 383
```

- **미지 버전이 필드 파서에 절대 닿지 않게** 하는 설계입니다. 1회차는 `version`만 찾고 나머지는 `skipValue()`.
- 모르는 필드는 에러가 아니라 스킵. 의도된 전방 호환성.
- 모든 실패는 **문제 필드를 가리키는 JSON Pointer**를 들고 다닙니다. 이게 에이전트에게 되돌아가 자기 교정의 재료가 됩니다.
- 한 JSON에 봉투가 두 개면 거부합니다("Multiple message envelopes found in a single JSON payload"). JSONL 1줄 1메시지가 관례가 아니라 강제입니다.
- ⚠️ **`v1.0` 메시지는 오늘 거부됩니다.** 스펙은 1.0으로 가는 중인데 구현은 v0.9 고정입니다.

`processInput`은 2026-08-16에 추가된 확장 함수로, 파싱 실패를 던지지 않고 `processError`로 흘려보냅니다. 스트리밍 진입점으로 의도된 API입니다.

### 1.3 `schema/`: JSON Schema 체계 (11 + commontypes 14파일)

A2UI에서 카탈로그는 곧 에이전트에게 주는 스키마입니다. 그 스키마를 코드로 표현한 계층입니다.

**노드 타입** (`A2uiSchema`의 sealed 하위):

| 클래스 | 대응 |
|---|---|
| `A2uiObjectSchema` | `type: object` + `properties`, `required`, `additionalProperties` |
| `A2uiArraySchema` | `type: array` + `items`, `minItems`/`maxItems` |
| `A2uiStringSchema` / `A2uiNumberSchema` / `A2uiBooleanSchema` | 원시 타입 |
| `A2uiAnySchema` | 구조 없는 페이로드 또는 키워드만 있는 스키마 |
| `A2uiRefSchema` | `$ref`. 다른 정의 참조 |
| `A2uiCompositeSchema` | 재사용 정의(`$defs`)를 만드는 추상 베이스 |

**키워드**는 노드가 아니라 `A2uiSchemaKeyword`로 분리돼 있습니다. JSON Schema **Draft 2020-12** 정렬(2026-08-09 개편):

```kotlin
public sealed class A2uiSchemaKeyword<out T> {
    class OneOf(schemas) / AllOf(schemas) / AnyOf(schemas) / Not(schema)
    class Enum(values) / Const(value) / Default(value)
}
```

> ⚠️ 이 개편에서 `A2uiAllOfSchema`, `A2uiAnyOfSchema`, `A2uiConstSchema`, `A2uiEnumSchema`, `A2uiOneOfSchema` 5개 클래스가 **삭제**됐습니다. 8월 초 이전 자료를 보고 있다면 그 이름들은 더 이상 없습니다.

**`commontypes/`** 는 스펙의 공통 타입을 그대로 옮긴 것들입니다. 스펙 문서와 1:1로 읽으면 됩니다:

| 스키마 | 스펙 개념 |
|---|---|
| `A2uiComponentCommonSchema` | 모든 컴포넌트 공통 속성 |
| `A2uiComponentIdSchema` | 서피스 내 컴포넌트 식별자 |
| `A2uiChildListSchema` | 자식 목록. 고정 ID 배열 **또는** 템플릿 `{componentId, path}` |
| `A2uiDataBindingSchema` | `{"path": "/user/name"}` |
| `A2uiDynamicStringSchema` 외 4종 | 리터럴 / 데이터 바인딩 / 함수 호출 중 하나 |
| `A2uiDynamicValueSchema` | 타입 무관 동적 값 |
| `A2uiFunctionCallSchema` | `{"call": …, "args": …}` + 내부 enum `FunctionCallableFrom`, `FunctionReturnType` |
| `A2uiActionSchema` | 이벤트 디스패치 / 클라이언트 함수 호출 |
| `A2uiCheckableSchema`, `A2uiCheckRuleSchema` | 클라이언트 측 입력 검증 |
| `A2uiAccessibilityAttributesSchema` | 접근성 속성 |

**Static과 Dynamic의 구분이 여기서 시작됩니다.** `A2uiStringSchema`의 KDoc: *"Use this for static text that **cannot be dynamically bound**."* 어떤 프로퍼티가 데이터를 참조할 수 있는지가 스키마 수준에서 결정되고, 나중에 `compose-runtime`의 타입 시스템으로 강제됩니다.

### 1.4 `catalog/`: 함수 체계 (4 + functions 16 + basiccatalog 1파일)

| 파일 | 무엇인가 |
|---|---|
| `A2uiFunction.kt` | `definition` + 구현을 묶는 인터페이스 |
| `A2uiFunctionDefinition.kt` | 이름·인자·반환 타입. `A2uiFunctionReturnType` enum |
| `A2uiFunctionCollection.kt` | 이름 색인 불변 컬렉션 (`collection["formatString"]`) |
| `A2uiFunctionDefinitionSerializer.kt` | 함수 정의 → `A2uiSchema` (에이전트에게 보여줄 형태) |

**`functions/` 내장 함수 14종.** `createBasicCatalogFunctions(urlOpener, messageFormatter, localeProvider)`가 조립합니다:

- 검증 5: `required`, `regex`, `length`, `numeric`, `email`
- 포맷 5: `formatString`(483줄, 문자열 보간 엔진), `formatNumber`, `formatCurrency`, `formatDate`, `pluralize`
- 논리 3: `and`, `or`, `not`
- 기타: `openUrl`
- (함수 아님) 지원 파일 2개: `A2uiFunctionArgParser`(311줄, 인자 파싱·검증 공용), `A2uiLocaleProvider`

14종은 스펙 `catalogs/basic/catalog.json`의 함수 목록과 **개수·이름이 정확히 일치**합니다.

**플랫폼 주입 지점이 세 개**입니다. 이게 이 설계의 요점입니다. 순수 Kotlin 모듈이 Android에 의존하지 않으면서 Android 기능을 쓰는 방법:

```kotlin
public fun interface A2uiUrlOpener        // openUrl → Intent.ACTION_VIEW
public fun interface A2uiMessageFormatter // pluralize → ICU MessageFormat
public fun interface A2uiLocaleProvider   // 통화·날짜·숫자 포맷의 Locale
```

`localeProvider`가 null이면 각 함수의 `INSTANCE` 싱글턴(기본 로케일)을 쓰고, 주어지면 인스턴스를 새로 만듭니다.

---

## 2. `a2ui-engine/`: 처리 엔진

받은 메시지를 **검증하고, 순서대로 적용하고, 상태를 소유**합니다. 여기에도 Compose가 없습니다.

### 2.1 `processor/`: 메시지 파이프라인 (5파일)

| 파일 | 무엇인가 |
|---|---|
| `A2uiCoreMessageProcessor.kt` | 진입점. 큐 → 서피스별 액터로 라우팅 (208줄) |
| `A2uiCoreSurfaceActor.kt` | **서피스 하나당 코루틴 하나.** 순차 처리 (259줄) |
| `A2uiEngineMessage.kt` | 액터 큐 내부 타입: `External`(서버) / `Action`(로컬 탭) / `Error` |
| `A2uiActionHandler.kt` | 인터셉터 체인 → 서버 이벤트 또는 로컬 함수 실행 |
| `A2uiUserActionParser.kt` | 액션 페이로드 맵 → `A2uiUserAction` |

**핵심 코드: 입구와 출구의 비대칭**

```kotlin
private val inboundQueue = Channel<A2uiEngineMessage>(Channel.UNLIMITED)
private val activeActors = ConcurrentHashMap<String, A2uiCoreSurfaceActor>()
private val _outboundEvents = MutableSharedFlow<A2uiClientToServerMessage>(
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
```

주석이 트레이드오프를 명시합니다: *"We use DROP_OLDEST to prevent OutOfMemoryErrors if the network layer completely stalls, under the assumption that dropping the oldest UI actions/errors is preferable to **freezing the entire Android app**."*
입구가 무제한인 이유도 적혀 있습니다. 코루틴이 언제나 네트워크보다 빠르다는 가정.

**핵심 코드: 예외 필터로 표현된 보안 태도**

```kotlin
try { handleMessage(message) }
catch (e: A2uiException) { handleA2uiException(e) }
// We only catch A2uiExceptions (e.g. ValidationFailed, intentional LLM RuntimeErrors).
// Unexpected JVM exceptions (like NPEs) are considered SDK bugs and should crash the app.
```

에이전트가 보낸 잘못된 입력은 **데이터**이므로 처리하고 보고합니다. 우리 코드의 NPE는 **버그**이므로 크래시시킵니다. 이 두 줄이 위협 모델을 요약합니다.

그 밖에 액터에서 읽을 만한 것:

- `if (isSurfaceCreated) handleAction(message)`. *"Ghost taps on deleted surfaces are safely ignored."* 에이전트가 서피스를 지운 순간 사용자 손가락이 이미 움직이고 있던 경우.
- `activeActors.compute(...)`. 액터가 "나 비었다"를 확인하는 동안 다른 스레드가 메시지를 넣는 레이스를 `ConcurrentHashMap` compute 락으로 닫습니다.
- **액션도 같은 큐로 재진입합니다.** `dispatchAction` → `onDispatchMessageToProcessor(A2uiEngineActionMessage)` → `inboundQueue`. 사용자 탭이 서버 메시지와 같은 순서 보장 안에 들어갑니다.
- `collectMessages()`의 `finally`에서 모든 서피스를 `dispose()`합니다. 구조적 동시성으로 정리됩니다. 수동 해제 API가 없습니다.

### 2.2 `model/`: 서피스와 동적 평가 (6파일)

| 파일 | 무엇인가 |
|---|---|
| `A2uiCoreSurfaceModel.kt` | 서피스 하나의 루트 도메인 모델. 데이터 모델·레지스트리 소유 (277줄) |
| `A2uiCoreSurfaceGroupModel.kt` | 활성 서피스 전체 관리. `StateFlow<List<...>>` 노출 |
| `A2uiCoreDynamicEvaluator.kt` | `{path}` / `{call, args}` 평가기 (348줄) |
| `A2uiCoreExecutionContext.kt` | 컴포넌트 하나의 실행 환경 묶음 |
| `A2uiCoreValueResolver.kt` | `fun interface`. 코어가 데이터를 동기적으로 읽되, **프레임워크가 그 읽기를 추적**할 수 있게 하는 콜백 |
| `A2uiCoreCacheProvider.kt` | 컴포넌트+함수 쌍별 캐시 |

**핵심 코드: 검증이 보안 경계인 지점**

```kotlin
internal fun updateComponents(payloads: List<A2uiComponentPayload>) {
    val validPayloads = mutableListOf<A2uiComponentPayload>()
    for (payload in payloads) {
        try {
            validateComponent(payload)
            caches.remove(payload.id)        // 재정의된 컴포넌트의 낡은 캐시 폐기
            validPayloads.add(payload)
        } catch (e: A2uiException) {
            dispatchError(exception = e, componentId = payload.id)
        }
    }
    componentRegistry.update(validPayloads)  // 생존자만 UI로
}

private fun validateComponent(payload: A2uiComponentPayload) {
    val componentDef = catalog.componentDefinitions[payload.type]
        ?: throw A2uiValidationException("Component type '${payload.type}' not found in catalog", basePath)
    schemaValidator.validateSchema(payload.properties, componentDef.propertySchema, basePath)
}
```

- **거부가 컴포넌트 단위**입니다. 12개 중 1개가 틀리면 11개를 그리고 1개를 보고합니다.
- **거부가 조용하지 않습니다.** `dispatchError`는 (a) 로컬 레지스트리에 에러 표시해서 UI가 에러 경계를 그리게 하고 (b) JSON Pointer가 붙은 에러를 에이전트에게 보냅니다.
- `dispatchError`의 KDoc 경고: `componentId`는 **로컬 표시 전용**이고 아웃바운드 context에 자동으로 들어가지 않습니다. 에이전트가 알아야 하면 예외에 직접 넣어야 합니다.
- `updateDataModel`에는 별도 게이트가 있습니다. 절대경로 강제, `~` 이스케이프 형식 검사.

`A2uiCoreDynamicEvaluator`는 명시적 work stack으로 재귀 없이 트리를 평가합니다(`KEY_PATH = "path"`, `KEY_CALL = "call"`). 깊게 중첩된 페이로드에서 스택 오버플로가 안 나게 하려는 설계입니다.

### 2.3 `catalog/`: 카탈로그 → 스키마 직렬화 (6파일)

| 파일 | 무엇인가 |
|---|---|
| `A2uiCoreCatalog.kt` | `id`, `componentDefinitions`, `functions`, `themeSchema`, `isInline` |
| `A2uiCoreCatalogAdapter.kt` | `A2uiCoreCatalog`를 `A2uiInlineCatalog`로 변환하는 어댑터 (`toJsonSchemaMap()`, `toJsonSchemaString()`) |
| `A2uiCoreComponentDefinition.kt` | `name`, `description`, `propertySchema` |
| `A2uiCoreComponentDefinitionCollection.kt` | 이름 색인 불변 컬렉션 |
| `A2uiCoreCatalogSerializer.kt` | 카탈로그 → **A2UI v0.9.1 카탈로그 스펙 JSON Schema Map/String** |
| `A2uiCoreComponentDefinitionSerializer.kt` | 컴포넌트 정의 → `A2uiSchema` |

`A2uiCatalog.toJsonSchemaString()`이 나오는 곳입니다. **이 문자열이 곧 에이전트 시스템 프롬프트에 들어가는 내용**입니다. 허용 목록과 프롬프트가 같은 객체에서 나오므로 어긋날 수 없습니다.

### 2.4 `schema/A2uiCoreSchemaValidator.kt` (422줄)

sealed `A2uiSchema` 트리를 재귀적으로 도는 **무상태** 검증기.
KDoc: *"ensures that incoming JSON payloads from the agent structurally conform to the expected UI component schemas **before** they are allowed into the reactive data models."*

```kotlin
for (keyword in schema.keywords) { when (keyword) {
    is OneOf -> validateOneOf(...)   is AllOf -> validateAllOf(...)
    is AnyOf -> validateAnyOf(...)   is Not   -> validateNot(...)
    is Enum  -> validateEnum(...)    is Const -> validateConst(...)
    is Default -> { /* annotation keyword */ } } }
when (schema) {
    is A2uiObjectSchema -> validateObject(payload, schema, path)
    is A2uiStringSchema -> validateString(payload, path)
    // …
}
```

- `A2uiObjectSchema.isAdditionalPropertiesAllowed = false`면 모르는 키를 즉시 거부합니다.
- `@RestrictTo(LIBRARY_GROUP_PREFIX)`. **공개 API가 아닙니다.** 엔진을 쓰면 검증이 따라오는 구조이지, 직접 호출하는 도구가 아닙니다.

---

## 3. `compose/compose-runtime/`: Compose 상태 매핑

프로토콜이 Compose와 처음 만나는 곳. 14파일 2,513줄.

| 파일 | 무엇인가 |
|---|---|
| `A2uiComponentRegistry.kt` | `A2uiCoreComponentRegistry`의 Compose 구현. **상태 저장의 심장** |
| `A2uiDataModel.kt` | `A2uiCoreDataModel`의 Compose 구현. JSON 트리 → 스냅샷 상태 (491줄) |
| `A2uiComponentRecord.kt` | 레지스트리가 보관하는 불변 레코드. `Valid`(type+properties) / `Error` |
| `A2uiComponentState.kt` | **`Loading` / `Success` / `Error`** + `observeA2uiComponentState` |
| `A2uiComponentModel.kt` | 렌더 준비가 끝난 컴포넌트: `(surface, type, properties, scope)` |
| `A2uiComponentProperties.kt` | 원본 프로퍼티 맵의 불변 래퍼. O(1) 동등성으로 리컴포지션 최적화 |
| `A2uiComponentReference.kt` | 컴포넌트 포인터 + 선택적 데이터 스코프(`baseDataPath`) |
| `A2uiComponentScope.kt` / `Impl.kt` | **`bind` / `bindUpdater` / `bindChildReferences` / `dispatchAction` / `reportError`** |
| `A2uiProperty.kt` | 타입 프로퍼티 토큰 전부 (904줄, 24개 내부 클래스) |
| `A2uiReadinessEvaluator.kt` | `isReady` 훅 + `LocalA2uiReadinessEvaluator` CompositionLocal |
| `A2uiRuntimeCatalog.kt` | 마커 인터페이스. 런타임이 시각 계층에 결합되지 않게 하는 불투명 토큰 |
| `A2uiRuntimeMessageProcessor.kt` | Compose 백엔드로 코어 프로세서를 조립하는 팩토리 |
| `A2uiMessageParser.kt` | `A2uiJsonReaderImpl`. Android `JsonReader` 어댑터 |

**핵심 코드: 인접 리스트가 곧 Compose 상태**

```kotlin
private val registry = MutableScatterMap<String, MutableState<A2uiComponentRecord?>>()

// 배치 전체가 한 번의 스냅샷으로 원자적 반영 → 찢어진 프레임 없음
Snapshot.withMutableSnapshot {
    for (i in statesToApply.indices) statesToApply[i].value = recordsToApply[i]
}
Snapshot.sendApplyNotifications()
```

- 쓰기 전에 **호출 스레드에서** 구조적 동등성을 선검사합니다. 안 바뀐 컴포넌트는 아예 쓰지 않아 UI 스레드에서 참조 동등성이 성립하고 Compose가 건너뜁니다.
- 배치 안에 중복 id가 있으면 **역순으로 순회**해서 마지막 지정이 이깁니다.
- `ReentrantLock(true)`. *공정* 락 두 개(업데이트용/삽입용). 도착 순서대로 적용되어야 하니까요.

**핵심 코드: 상태 기계**

```kotlin
when (record) {
    null                    -> A2uiComponentState.Loading   // 아직 안 온 것 ≠ 에러
    is A2uiComponentRecord.Error -> A2uiComponentState.Error(record.exception)
    is A2uiComponentRecord.Valid -> A2uiComponentState.Success(A2uiComponentModel(...))
}
if (state is Success && !evaluator.isReady(state.component)) return Loading
```

**"없는 id = Loading"** 이 한 줄이 스트리밍 렌더링의 전부입니다. 루트 상수는 `RootComponentId = "root"`, `RootComponentDataPath = A2uiDataPath("/")`.

**핵심 코드: 바인딩 3종**

```kotlin
// 읽기: 타입이 안 맞으면 아무것도 그리지 않고 에이전트에게 보고
val castedValue = property.safeCast(evaluatedValue)
if (castedValue == null) {
    // Type mismatch detected, report the error to the agent for self-correction.
    SideEffect(evaluatedValue, property.key) { reportError(A2uiRuntimeException(...)) }
    return null
}

// 쓰기: 페이로드가 {"path": …} 데이터 바인딩일 때만 쓰기 함수가 나옴
val isWritablePath = payload is Map<*, *> && payload.containsKey("path")
if (isWritablePath) { newValue -> surface.dataModel.update(baseDataPath / path, newValue) } else null

// 자식: 정적 ID 배열 또는 템플릿 → 원소마다 자기 baseDataPath를 가진 참조
List(dataList.size) { index -> A2uiComponentReference(componentId, "$path$separator$index") }
```

- 리터럴은 **구조적으로 읽기 전용**입니다. 에이전트가 직접 박은 값에 되쓸 방법이 없습니다. 쓸 곳이 없으니까요.
- 템플릿 리스트 처리가 곧 **Child Scope**이고, 실질 15줄입니다.
- 평가는 `remember { derivedStateOf { … } }` 안에 있어서, 데이터 모델의 한 경로가 바뀌면 그 경로를 읽은 컴포넌트만 리컴포즈됩니다.
- 에러 보고는 컴포지션 중이 아니라 `SideEffect`에서 일어납니다.

**`A2uiProperty.kt`(1126줄)** 는 팩토리 23종을 담습니다. 중요한 건 3분류입니다:

| 종류 | 예 | 의미 |
|---|---|---|
| `StaticA2uiProperty` | `string`, `number`, `stringEnum`, `numberEnum`, `enum`, `componentId`, `action`, `nested`, `nestedList`, `custom` | **데이터 바인딩 불가**. `properties[prop]`로 직접 읽음 |
| `DynamicA2uiProperty` | `dynamicString`, `dynamicNumber`, `dynamicBoolean`, `dynamicStringList`, `dynamicValue`, `dynamicCustom` | `bind()`로만 읽음. 리터럴·경로·함수 호출 모두 가능 |
| `ChildListA2uiProperty` | `childList` | `bindChildReferences()` 전용 |

`bind()`가 `DynamicA2uiProperty`만 받기 때문에, **"어떤 프로퍼티가 데이터를 참조할 수 있는가"가 타입 시스템으로 강제**됩니다.

`A2uiDataModel.kt`는 JSON 트리를 `SnapshotStateMap`과 자체 구현 `SnapshotStateSparseList`로 매핑합니다. 중첩 구조를 자동으로 다루고, 세밀한 반응성을 위해 설계됐습니다.

---

## 4. `compose/compose-ui/`: 컴포넌트 계약

**5파일, 2,596줄.** "컴포넌트란 무엇인가"와 "카탈로그란 무엇인가"의 정의 전부입니다. 08-21 기준 736줄이었으니 **열이틀 만에 세 배 반으로 불었고**, 늘어난 분량은 사실상 전부 `A2uiBasicCatalogV1.kt` 하나입니다.

| 파일 | 무엇인가 |
|---|---|
| `A2uiComponent.kt` (133줄) | `A2uiComponent` 인터페이스 + 재귀 라우터 composable |
| `A2uiCatalog.kt` (236줄) | `A2uiCatalog` 인터페이스 + 팩토리 함수 + `asReadinessEvaluator()` |
| `A2uiComponentCollection.kt` (109줄) | 이름 색인 컴포넌트 컬렉션 |
| `A2uiMessageProcessor.kt` (51줄) | Compose용 프로세서 팩토리 |
| `catalog/A2uiBasicCatalogV1.kt` (2,067줄) | **08-19 신설.** 기본 카탈로그 API 계약. 08-21 `Card`(258줄) 이후 컴포넌트가 하나씩 합류할 때마다 커졌고, 09-01 `Divider`·`CheckBox`·`Slider`, 09-02 `Video`·`AudioPlayer`로 **컴포넌트 인터페이스가 열다섯**이 됐습니다. 09-02에는 자식이 될 수 있는 컴포넌트들에 공통 `WeightProperty`도 들어왔습니다 |

```kotlin
@Stable
public interface A2uiComponent {
    public val name: String                       // 페이로드의 "component" 필드와 일치
    public val description: String                // 에이전트 스키마에 들어감
    public val properties: List<A2uiProperty<*>>  // 스키마 생성 + 런타임 키

    @Composable
    public fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean = true

    @Composable
    public fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier)
}
```

**`Content`가 `A2uiComponentScope`의 확장 함수라는 게 핵심**입니다. 스타일이 아니라 접근 제어 결정입니다. `bind`, `dispatchAction`, `reportError`는 컴포넌트 본문 안에서만 존재합니다. 임의의 코드가 서피스에 손을 뻗을 수 없습니다. 리시버가 거기 없으니까요.

**재귀 라우터는 4줄입니다:**

```kotlin
val a2uiComponent = catalog.components[component.type]
    ?: throw IllegalStateException("Component with type '${component.type}' is not registered")
with(a2uiComponent) { component.scope.Content(properties, modifier) }
```

Card가 자식을 위해 `A2uiComponent`를 부르고, 그 자식이 또 부릅니다. 트리 렌더링 끝.

**카탈로그 팩토리:**

```kotlin
public fun A2uiCatalog(
    catalogId: String,                                // 버전 붙은 URI 권장 (능력 협상에서 광고됨)
    components: List<A2uiComponent>,
    functions: List<A2uiFunction> = emptyList(),
    themeSchema: A2uiSchema? = null,
): A2uiCatalog
```

내부에서 각 컴포넌트의 `properties`를 훑어 `A2uiObjectSchema(properties, required)`를 만듭니다. **한 번의 선언이 스키마와 런타임 키 두 역할**을 하는 지점입니다. 이름이 중복되면 `IllegalArgumentException`.

`A2uiCatalogImpl`이 `A2uiCatalog`, `A2uiRuntimeCatalog`, `A2uiCoreCatalog` 세 인터페이스를 동시에 구현합니다. 세 계층이 같은 객체를 각자의 관점으로 보는 구조입니다.

**`catalog/A2uiBasicCatalogV1.kt`: 기본 카탈로그를 라이브러리가 정의하기 시작했습니다 (08-19):**

8/17에 들어왔다 당일 되돌려졌던 커밋이 `Revert^2`로 다시 착륙했습니다. 지금까지 "기본 카탈로그 18개"는 스펙 문서에만 있는 목록이었고, 라이브러리는 `A2uiComponent` 구현체를 낱개로 줄 뿐이었습니다. 이제는 **타입이 있는 계약**이 코드에 있습니다.

```kotlin
public class A2uiBasicCatalogV1(
    public val text: Text,                        // 08-19
    public val image: Image,                      // 08-24
    public val icon: Icon,                        // 08-27
    public val card: Card,                        // 08-21
    public val row: Row,                          // 08-21
    public val column: Column,                    // 08-21
    public val list: List,                        // 08-27
    public val tabs: Tabs,                        // 08-27
    public val button: Button,                    // 08-24
    public val dateTimeInput: DateTimeInput,      // 08-27
    // TODO(b/547851648): 나머지 컴포넌트 타입 추가
    public val functions: kotlin.collections.List<A2uiFunction>,
) {
    public val catalogId: String = CatalogId      // .../v0_9_1/catalogs/basic/catalog.json
    public val themeSchema: A2uiSchema = ThemeSchema   // primaryColor, iconUrl, agentDisplayName
    public val components: kotlin.collections.List<A2uiComponent> =
        listOf(text, image, icon, card, row, column, list, tabs, button, dateTimeInput)

    public interface Text : A2uiComponent {
        override val name: String get() = "Text"
        public enum class Variant(public val value: String) { H1, H2, H3, H4, H5, Caption, Body }

        // 계약이 바인딩을 대신 해 주고, 디자인 시스템은 그리기만 구현합니다.
        @Composable
        public fun A2uiComponentScope.TypedContent(text: String, variant: Variant, modifier: Modifier)
    }

    public interface Card : A2uiComponent {
        override val name: String get() = "Card"
        // 프로퍼티는 `child` 하나, 필수. 여러 개를 넣고 싶으면 Row/Column으로 싸라고
        // description이 에이전트에게 대문자로 못을 박습니다.
        @Composable
        public fun A2uiComponentScope.TypedContent(childId: String, modifier: Modifier)
    }
}
```

읽을 점 네 가지:

- **열흘 만에 둘에서 열이 됐습니다.** 8/19 `Text` 하나로 시작해 8/21 `Card`·`Row`·`Column`, 8/24 `Button`·`Image`, 8/27 하루에만 `Icon`·`List`·`Tabs`·`DateTimeInput`이 합류했습니다. 남은 여덟 개는 여전히 `TODO(b/547851648)` 주석으로 자리만 잡혀 있지만, **속도가 한 달에 두 개에서 하루에 네 개로 바뀌었습니다.** `Card`가 들어오던 8/21에 `hashCode`/`toString`이 `text` 대신 `components`를 쓰도록 바뀐 것도 이 전제 위에서 고친 자리입니다.
- **`List`가 이름을 잡아먹습니다.** 계약에 `A2uiBasicCatalogV1.List` 인터페이스가 생기면서 클래스 본문 안에서는 `List`가 더 이상 `kotlin.collections.List`가 아닙니다. 그래서 `functions`와 `components`의 타입이 `kotlin.collections.List<...>`로 전부 풀어 적혀 있습니다. 계약을 구현하는 쪽에서도 같은 함정을 만나게 됩니다.
- **`DateTimeInput`이 세 번째 입력 컴포넌트입니다.** `value`(ISO 8601 dynamic string, 필수)에 `enableDate`/`enableTime`/`min`/`max`/`label`. `min`/`max`는 아직 JSON Schema `format`을 못 써서(`TODO(b/553193771)`) `A2uiAnySchema` + `allOf`로 우회해 두었습니다.
- **`Content`가 이미 구현돼 있습니다.** 계약이 `properties.bind(textProperty)`로 필수 프로퍼티를 꺼내고 `variant`의 기본값(`Body`)까지 정한 뒤 `TypedContent(text, variant, modifier)`를 부릅니다. 구현자는 **JSON을 만지지 않습니다.** 문자열과 enum만 받습니다.
- **프로퍼티 스키마도 계약이 갖습니다.** `textProperty`(필수, dynamic string)와 `variantProperty`(static enum)가 companion에 있고, 이걸 구현체가 상속합니다. 즉 **에이전트에게 광고되는 스키마가 디자인 시스템마다 달라질 수 없습니다.** 지금까지 앱이 자유롭게 정하던 부분입니다.
- **카탈로그 ID는 아직 v0.9.1입니다.** `CatalogId`는 `https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json`이고, `TODO(b/547900174): v1.0 지원이 구현되면 갱신` 주석이 붙어 있습니다. 라이브러리가 v0.9 고정이라는 사실이 여기서도 그대로 보입니다.

`A2uiCatalog()` 팩토리에 오버로드가 하나 늘었습니다: `A2uiCatalog(basicCatalog: A2uiBasicCatalogV1)`. 계약 객체를 그대로 넣으면 id·컴포넌트·함수·테마 스키마가 한 번에 배선됩니다.

**조립 순서**(`A2uiSurface` KDoc 기준, 4단계):

1. `A2uiCatalog(...)`로 카탈로그 정의
2. `A2uiMessageProcessor(catalogs)`. Compose 백엔드 레지스트리·데이터 모델을 붙임 (보통 ViewModel에)
3. `collectMessages()`를 백그라운드 디스패처에서 실행
4. `activeSurfaces`를 collect해서 `A2uiSurfaceModel`을 composable에 전달

---

## 5. `compose/compose-ui-testing/`: 테스트 하네스

2026-08-04 신설. 4파일 859줄 + 샘플.

| 파일 | 무엇인가 |
|---|---|
| `A2uiTestSurface.kt` | 테스트용 루트 마운트 composable |
| `A2uiTestController.kt` | 테스트 조작 API (201줄) |
| `A2uiTestControllerImpl.kt` | 구현 (452줄) |
| `A2uiComponentStub.kt` | 컴포넌트 스텁. id별 또는 타입별로 교체 |

```kotlin
public interface A2uiTestController {
    public val surface: A2uiSurfaceModel
    public val dispatchedActions: List<A2uiUserAction>        // 탭이 무엇을 보냈나
    public val outboundEvents: List<A2uiClientEventMessage>
    public val outboundErrors: List<A2uiClientErrorMessage>   // 무엇이 거부됐나

    public fun updateData(path: String, value: Any?)
    public fun updateComponent(id: String, type: String, properties: Map<String, Any?>)
    public fun failComponent(id: String, exception: A2uiException)
    public fun getRawData(path: String): Any?
    public fun clearDispatchedActions() / clearOutboundEvents() / clearOutboundErrors()
}
```

에이전트 UI 테스트가 **"메시지를 넣고, 상태와 아웃바운드를 단언한다"** 로 표준화됩니다. `A2uiComponentStub`으로 자식을 잘라내고 특정 컴포넌트만 격리해 볼 수 있습니다.

---

## 6. `integration-tests/testapp/`

`MainActivity.kt`(27줄), `TestAppApplication.kt`(21줄). 내부 통합 테스트용 껍데기입니다. 샘플 앱을 기대하고 열면 실망합니다. **읽을 만한 사용 예제는 `compose-ui-testing/samples/`와 `material3-a2ui/samples/`에 있습니다.**

---

## 7. `compose/material3/material3-a2ui/`: 실제 컴포넌트 (18개 중 16개)

`a2ui/` **밖에**, Material 3 트리 안에 있습니다. 의존 방향 때문입니다. `compose-ui`가 Material 3에 의존하면 안 되는데 M3 카탈로그는 양쪽에 의존하므로, M3 쪽에 둘 수밖에 없습니다.

- group `androidx.compose.material3`, `mavenVersion = LibraryVersions.COMPOSE_MATERIAL3_A2UI_QUARANTINE` → **quarantine, 미출시**
- `type = PUBLISHED_LIBRARY_ONLY_USED_BY_KOTLIN_CONSUMERS`
- 2026-08-04 시작, main 19파일 2,607줄 + `icons/` 벡터 아이콘 60개(8,154줄 중 5,728줄). 파일 수보다 **내용이 `catalog/` 아래로 이사하는 속도**가 이 모듈의 성격입니다. 09-01 하루에 standalone 셋이 한꺼번에 `catalog/`로 넘어가면서 파일 수는 17로 그대로인데 줄 수는 오히려 줄었고(2,468 → 2,425), 09-02에 `Video`·`AudioPlayer`가 새로 붙어 19파일 2,607줄이 됐습니다

| 파일 | 상태 | 무엇인가 |
|---|---|---|
| `A2uiSurface.kt` (216줄) | 08-14 | M3 스타일 서피스 진입점 + `A2uiSurfaceDefaults` |
| `MaterialA2uiDefaults.kt` (105줄) | 08-17 | 컴포넌트들이 공유하는 `transitionSpec` 등 M3 기본값 |
| `MaterialTextFieldComponent.kt` (220줄) | 08-25 | `"TextField"`. **09-01 이후 유일하게 남은 standalone public object** |
| `icons/` (60파일) | 08-24 | `A2uiIcon` + 벡터 아이콘 59개. `Icon` 컴포넌트가 이름으로 찾아 씁니다 |
| `catalog/MaterialA2uiBasicCatalogV1.kt` (185줄) | 08-19 | `materialA2uiBasicCatalogV1()` 팩토리 + `MaterialA2uiBasicCatalogV1Defaults`. 09-01 `divider`·`checkBox`·`slider`가, 09-02 `video`·`audioPlayer`가 늘었습니다. 뒤의 둘은 **기본값이 없는 필수 파라미터** |
| `catalog/…Text.kt` (82줄) | 08-19 | `"Text"` (**마크다운 처리 아직 없음**) |
| `catalog/…Card.kt` (76줄) | 08-21 | `"Card"`. 자식 하나를 M3 `Card`로 감싸고 로딩/에러를 `AnimatedContent`로 전환 |
| `catalog/…Row.kt` (150줄) / `…Column.kt` (148줄) | 08-24 (**09-01·09-02 개정**) | `"Row"` / `"Column"`. `justify`/`align`(09-01)에 이어 자식 `weight`(09-02 `c8e76a4`·`55652b0`)까지 구현됐습니다. **두 파일 모두 TODO가 하나도 남지 않았습니다** |
| `catalog/…Button.kt` (181줄) | 08-24 | `"Button"` |
| `catalog/…Image.kt` (124줄) | 08-25 | `"Image"`. 유일하게 팩토리(`Defaults.image(imageRenderer)`)를 거칩니다 |
| `catalog/…Icon.kt` (119줄) | **08-27 신설** | `"Icon"`. `MaterialIconComponent.kt`(204줄)를 대체. 09-02에 `weight`를 받습니다 |
| `catalog/…List.kt` (177줄) | **08-27 신설** | `"List"`. `LazyRow`/`LazyColumn`. `MaterialListComponent.kt`(230줄)를 대체 |
| `catalog/…Tabs.kt` (127줄) | **08-27 신설** | `"Tabs"`. 탭이 지워져도 선택 인덱스를 유효하게 유지. `MaterialTabsComponent.kt`(173줄)에서 이름만 바뀐 게 아니라 46줄이 계약으로 빠졌습니다 |
| `catalog/…DateTimeInput.kt` (324줄) | **08-27 신설** | `"DateTimeInput"`. M3 `DatePicker`/`TimePicker` 다이얼로그. **이 모듈에서 가장 큰 컴포넌트**이고, 09-02 새 `format`·`if-then` 스키마 키워드의 첫 사용처입니다 |
| `catalog/…Divider.kt` (39줄) | **09-01 신설** | `"Divider"`. `MaterialDividerComponent.kt`(59줄)를 대체. 프로퍼티가 전부 `StaticA2uiProperty`인 유일한 컴포넌트 |
| `catalog/…CheckBox.kt` (74줄) | **09-01 신설** | `"CheckBox"`. `MaterialCheckBoxComponent.kt`(116줄)를 대체. **첫 입력 컴포넌트**가 드디어 계약 안으로 |
| `catalog/…Slider.kt` (116줄) | **09-01 신설** | `"Slider"`. `MaterialSliderComponent.kt`(177줄)를 대체. `min > max`면 그리지 않고 `reportError`(`TODO(b/549060875)`) |
| `catalog/…Video.kt` (64줄) | **09-02 신설** | `"Video"`. 프로퍼티는 `url` 하나. **컴포넌트가 아니라 `A2uiVideoRenderer`를 앱이 줍니다**(Media3/ExoPlayer 등). 모듈 자체는 미디어 라이브러리에 의존하지 않습니다 |
| `catalog/…AudioPlayer.kt` (78줄) | **09-02 신설** | `"AudioPlayer"`. `Video`와 같은 렌더러 주입 구조(`A2uiAudioPlayerRenderer`) |

> `MaterialTextComponent.kt`(08-12)·`MaterialCardComponent.kt`(08-17)에 이어 **8/24 `MaterialButtonComponent`·`MaterialRowComponent`·`MaterialColumnComponent`가, 8/27 `MaterialIconComponent`·`MaterialListComponent`·`MaterialTabsComponent`가, 9/1 `MaterialDividerComponent`·`MaterialCheckBoxComponent`·`MaterialSliderComponent`가 삭제됐습니다.** 같은 코드가 `catalog/` 아래로 옮겨가면서 `public object`에서 `internal object`가 됐고, 타입이 `A2uiBasicCatalogV1.X`로 바뀌었습니다. 이름으로 import하던 코드는 전부 깨집니다. 이 저장소의 데모 카탈로그가 8/19, 8/21, 8/24에 이어 이번에도 그랬습니다.
>
> 패턴이 확인됐습니다. **계약에 컴포넌트가 하나 들어갈 때마다 같은 이름의 public object가 하나 사라집니다.** 9/1 하루에 셋이 한꺼번에 넘어가면서 남은 public object는 **`MaterialTextFieldComponent` 하나**뿐입니다. 이것도 같은 길로 갈 것이므로 `Material*Component`를 이름으로 붙들고 있는 코드는 전부 시한부입니다. 실질적으로 **이 이주는 이미 끝났다고 봐도 됩니다.** 안전한 쪽은 처음부터 `MaterialA2uiBasicCatalogV1Defaults`를 거치는 것입니다.

**카탈로그를 통째로 받는 길이 생겼습니다:**

```kotlin
public fun materialA2uiBasicCatalogV1(
    image: A2uiBasicCatalogV1.Image,              // 렌더러를 앱이 준다
    video: A2uiBasicCatalogV1.Video,              // 09-02 신설, 기본값 없음
    audioPlayer: A2uiBasicCatalogV1.AudioPlayer,  // 09-02 신설, 기본값 없음
    urlOpener: A2uiUrlOpener,
    messageFormatter: A2uiMessageFormatter,
    localeProvider: A2uiLocaleProvider,
    text:  A2uiBasicCatalogV1.Text   = MaterialA2uiBasicCatalogV1Defaults.text,
    icon:  A2uiBasicCatalogV1.Icon   = MaterialA2uiBasicCatalogV1Defaults.icon,          // 08-27
    card:  A2uiBasicCatalogV1.Card   = MaterialA2uiBasicCatalogV1Defaults.card,
    row:   A2uiBasicCatalogV1.Row    = MaterialA2uiBasicCatalogV1Defaults.row,
    column:A2uiBasicCatalogV1.Column = MaterialA2uiBasicCatalogV1Defaults.column,
    list:  A2uiBasicCatalogV1.List   = MaterialA2uiBasicCatalogV1Defaults.list,          // 08-27
    tabs:  A2uiBasicCatalogV1.Tabs   = MaterialA2uiBasicCatalogV1Defaults.tabs,          // 08-27
    divider:  A2uiBasicCatalogV1.Divider  = MaterialA2uiBasicCatalogV1Defaults.divider,  // 09-01
    button:A2uiBasicCatalogV1.Button = MaterialA2uiBasicCatalogV1Defaults.button,
    checkBox: A2uiBasicCatalogV1.CheckBox = MaterialA2uiBasicCatalogV1Defaults.checkBox, // 09-01
    slider:   A2uiBasicCatalogV1.Slider   = MaterialA2uiBasicCatalogV1Defaults.slider,   // 09-01
    dateTimeInput: A2uiBasicCatalogV1.DateTimeInput =
        MaterialA2uiBasicCatalogV1Defaults.dateTimeInput,                                // 08-27
    // TODO(b/547851648): 나머지 컴포넌트 타입 추가
): A2uiCatalog
```

앱이 컴포넌트를 하나씩 모아 `A2uiCatalog(...)`를 부르던 조립 작업을 라이브러리가 대신합니다. 함수 목록도 `createBasicCatalogFunctions(...)`로 함께 들어갑니다. 여기 들어가는 컴포넌트가 **8/21의 둘에서 8/27에 열, 9/1에 열셋, 9/2에 열다섯이 됐습니다.** Text·Image·Icon·Video·AudioPlayer·Card·Row·Column·List·Tabs·Divider·Button·CheckBox·Slider·DateTimeInput. 8/21 기준으로 "실제로 화면을 그리려면 여전히 `A2uiCatalog(catalogId, components, functions)` 쪽을 써야 한다"고 적었지만, 이제는 **팩토리 하나로 화면이 그려집니다.** 계약 밖에 남은 셋(구현된 건 `TextField` 하나뿐, 나머지 둘은 아직 `TODO(b/547851648)`)을 섞어 쓸 때만 낮은 층 생성자가 필요합니다. 이 저장소의 androidx 카탈로그 세 개가 그 예입니다.

> **9/2에 깨진 약속.** 8/21에 여기 적었던 "파라미터가 컴포넌트마다 하나씩 늘어나고 전부 기본값이 있으므로 호출하는 쪽 코드는 계약이 채워져도 그대로"라는 말은 **더 이상 사실이 아닙니다.** `Video`와 `AudioPlayer`는 `Image`처럼 앱이 렌더러를 주입해야 해서 기본값을 줄 수 없고, 그래서 **기본값 없는 필수 파라미터**로 들어왔습니다. 필수 파라미터가 넷에서 여섯이 되면서 `materialA2uiBasicCatalogV1(...)`의 **기존 호출부는 전부 컴파일 에러**가 납니다. 계약이 채워지는 비용은 "이름으로 import한 `Material*Component`가 사라지는 것"만이 아니었습니다. **재생 계열 컴포넌트는 라이브러리가 대신 줄 수 없다**는 게 이유이니, 앞으로 미디어·지도처럼 외부 의존이 필요한 컴포넌트가 합류할 때마다 같은 일이 반복됩니다.

**`MaterialA2uiBasicCatalogV1Button`: 컴포넌트 작성의 표준 형태 (08-24부터 이 모양):**

```kotlin
internal object MaterialA2uiBasicCatalogV1Button : A2uiBasicCatalogV1.Button {
    // 프로퍼티 선언이 없습니다. childId/variant/action의 스키마는 계약이 갖고 있습니다.
    @Composable
    override fun A2uiComponentScope.TypedContent(
        childId: String,
        variant: A2uiBasicCatalogV1.Button.Variant,   // Default / Primary / Borderless
        action: Map<String, Any?>,
        modifier: Modifier,
    ) {
        val currentAction by rememberUpdatedState(action)
        val onClick: () -> Unit = remember { { dispatchAction(currentAction) } }

        val childState = observeA2uiComponentState(childId)   // 자식이 아직 안 왔을 수 있음
        ButtonVariant(variant, enabled = childState is Success, error = childState is Error,
                      onClick = onClick, modifier = modifier) {
            AnimatedContent(childState, MaterialA2uiDefaults.transitionSpec()) { state ->
                when (state) {
                    is Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    is Error   -> Text(stringResource(R.string.error), maxLines = 1)
                    is Success -> A2uiComponent(component = state.component)
                }
            }
        }
    }
}
```

배울 점:

- **08-11의 `public object MaterialButtonComponent : A2uiComponent`가 08-24에 이 모양으로 바뀌었습니다.** `A2uiProperty.componentId(...)` 세 줄이 통째로 사라진 자리가 계약이 가져간 부분입니다. 구현체는 이제 **JSON도 프로퍼티 키도 만지지 않고** 타입 붙은 인자만 받습니다. `override val name`/`properties`도 계약의 기본 구현이 대신합니다.
- **버튼의 라벨이 문자열이 아니라 자식 컴포넌트 ID입니다.** 그래서 자식 상태를 관찰하고, 로딩 중엔 스피너를, 실패하면 에러 라벨을 그립니다. 스트리밍을 기본값으로 우아하게 다룹니다.
- `variant` enum → `Default`=`OutlinedButton`, `Primary`=`Button`, `Borderless`=`TextButton`. **에이전트는 의미를 고르고 테마가 픽셀을 정합니다.**
- 자식이 Error면 disabled 컨테이너 색을 `errorContainer`로 `animateColorAsState` 시킵니다.
- `contentKey`에 `Pair(childId, state.component.type)`를 물려서 **같은 자식이 데이터만 갱신될 때는 전환 애니메이션이 돌지 않습니다.**

**액션을 조상이 가로챌 수 있습니다 (08-27 `ProvideActionInterceptor`):**

```kotlin
@Composable
public fun A2uiComponentScope.ProvideActionInterceptor(
    onIntercept: (actionPayload: Map<String, Any?>) -> Boolean,
    content: @Composable () -> Unit,
)
```

`dispatchAction`이 서피스로 내려가기 전에 `LocalA2uiActionInterceptor` 체인을 먼저 거칩니다. **안쪽(깊은 자식)부터 바깥쪽으로** 흐르고, `true`를 돌려주면 거기서 소비되어 바깥 인터셉터도 서피스도 그 액션을 보지 못합니다. 전부 `false`면 평소대로 서피스로 갑니다. 모달처럼 **자식 버튼의 클릭을 로컬 상태 변경으로 처리해야 하는 컨테이너**를 위해 들어온 API입니다. 아직 계약에 없는 `Modal`이 이 위에 올라올 것으로 보입니다.

**`A2uiSurface`** 는 `A2uiCoreSurfaceModel`과 `A2uiCatalog`를 요구하고, `catalog.asReadinessEvaluator()`를 `LocalA2uiReadinessEvaluator`로 제공한 뒤 Loading/Error/Success를 `AnimatedContent`로 전환합니다. `A2uiSurfaceDefaults`가 M3 motion scheme 기반 기본 전환·로딩 인디케이터·에러 폴백을 제공합니다.
KDoc에 명시된 성능 배려: **데이터만 바뀐 업데이트는 구조 전환 애니메이션을 트리거하지 않습니다.**

---

## 8. JSONL 한 줄이 UI가 되기까지: 전체 경로

```
"{"version":"v0.9","updateComponents":{...}}"
   │
   ├─ A2uiJsonMessageParser.parse()                       [model/processor]
   │     ① detectVersion → validateVersion (v0.9/v0.9.1만)
   │     ② 스트리밍 파싱 → A2uiUpdateComponentsMessage
   │
   ├─ A2uiMessageProcessor.processMessage()               [engine/processor]
   │     → inboundQueue (Channel.UNLIMITED)
   │     → activeActors[surfaceId] 라우팅
   │
   ├─ A2uiCoreSurfaceActor.runProcessingLoop()            [engine/processor]
   │     순차 처리, A2uiException만 catch
   │
   ├─ A2uiCoreSurfaceModel.updateComponents()             [engine/model]
   │     컴포넌트별: 카탈로그 멤버십 → A2uiCoreSchemaValidator
   │     실패 → dispatchError (로컬 표시 + 에이전트 통보)
   │
   ├─ A2uiComponentRegistry.update()                      [compose-runtime]
   │     구조적 동등성 선검사 → Snapshot.withMutableSnapshot 원자 배치
   │
   ├─ observeA2uiComponentState()                         [compose-runtime]
   │     null→Loading / Error / Valid→Success + isReady 확인
   │
   ├─ A2uiSurface → A2uiComponent(component)              [material3 / compose-ui]
   │     catalog.components[type] → scope.Content(...)
   │
   ├─ A2uiBasicCatalogV1.Button.Content()                  [compose-ui, 계약]
   │     properties.bind(ChildProperty/VariantProperty/ActionProperty)
   │     → TypedContent(childId, variant, action, modifier)
   │
   └─ MaterialA2uiBasicCatalogV1Button.TypedContent()      [material3-a2ui]
         observeA2uiComponentState(childId) → Material 3 Button
```

역방향(사용자 탭):

```
dispatchAction(payload)                    [compose-runtime/A2uiComponentScopeImpl]
  → A2uiCoreSurfaceModel.dispatchAction    → A2uiUserAction.fromPayload
  → onDispatchMessageToProcessor           → inboundQueue (같은 순서 보장 안으로!)
  → A2uiCoreSurfaceActor.handleAction      → A2uiCoreExecutionContext 구성
  → A2uiActionHandler.handleAction         → 인터셉터 체인 (null = 취소)
       ├ A2uiEventAction        → context 평가 → A2uiClientEventMessage → 서버
       └ A2uiFunctionCallAction → args 평가 → executeFunction (로컬)
```

---

## 9. 스펙 개념 ↔ 코드 대응표

| A2UI 스펙 개념 | 구현 위치 |
|---|---|
| `createSurface` / `updateComponents` / `updateDataModel` / `deleteSurface` | `A2uiServerToClientMessage`의 4개 클래스 |
| `action` / `error` (렌더러 → 에이전트) | `A2uiClientEventMessage` / `A2uiClientErrorMessage` |
| Surface | `A2uiSurfaceModel`(인터페이스) → `A2uiCoreSurfaceModel`(구현) |
| Component (인접 리스트) | `A2uiComponentPayload` → `A2uiComponentRecord` → `MutableState` |
| Data model (JSON 트리) | `A2uiCoreDataModel`(인터페이스) → `A2uiDataModel`(스냅샷 상태) |
| Catalog = 허용 목록 | `A2uiCatalog` + `A2uiCoreSurfaceModel.validateComponent` |
| 카탈로그 → 에이전트 스키마 | `A2uiProperty` → `A2uiObjectSchema` → `A2uiCoreCatalog.toJsonSchema()` |
| 데이터 바인딩 `{"path": …}` | `A2uiDataPath` + `A2uiComponentScope.bind` |
| 양방향 바인딩 | `bindUpdater`. `path`가 있을 때만 쓰기 가능 |
| 함수 호출 `{"call": …}` | `A2uiCoreDynamicEvaluator` + `catalog/functions/*` 15종 |
| Child Scope (템플릿 리스트) | `bindChildReferences`의 `Map` 분기 + `A2uiComponentReference.baseDataPath` |
| 프로그레시브 렌더링 | `A2uiComponentState.Loading` + `A2uiComponent.isReady()` |
| 액션 중재 | `A2uiActionInterceptor` 체인 (`A2uiActionHandler`) |
| 능력 협상 (`supportedCatalogIds`) | `A2uiClientCapabilities.toPayloadMap()` |
| 접근성 속성 | `A2uiAccessibilityAttributesSchema` |
| 프로토콜 버전 | `A2uiProtocolConstants.PROTOCOL_VERSION` + `SUPPORTED_VERSIONS` |

---

## 10. 읽는 순서 추천

시간이 없다면 이 순서로 6파일만 읽어도 전체 그림이 잡힙니다:

1. `a2ui-model/protocol/A2uiServerToClientMessage.kt` (143줄): 와이어 전부
2. `a2ui-model/processor/A2uiJsonMessageParser.kt`의 `parse()` (37–79줄): 버전 게이트
3. `a2ui-engine/model/A2uiCoreSurfaceModel.kt`의 `updateComponents` (197–251줄): 보안 경계
4. `compose-runtime/A2uiComponentRegistry.kt` (130줄): 메시지가 상태가 되는 곳
5. `compose-ui/A2uiComponent.kt` (133줄): 컴포넌트 계약 + 재귀 라우터
6. `material3-a2ui/catalog/MaterialA2uiBasicCatalogV1Button.kt` (181줄): 위 다섯 개가 합쳐진 실물. 계약 쪽 짝은 `compose-ui/catalog/A2uiBasicCatalogV1.kt`의 `Button` 인터페이스

깊이 파려면: `A2uiCoreSurfaceActor.kt`(동시성 모델) → `A2uiComponentScopeImpl.kt`(바인딩) →
`A2uiProperty.kt`(타입 체계) → `A2uiCoreSchemaValidator.kt`(검증) 순서.

---

## 11. 현황 요약 (2026-08-21 확인)

- **소스는 살아 있습니다.** 신규 테스트 모듈(08-04), 스키마 API 개편(08-09), `A2uiSurfaceModel` 도입(08-12), 버전 검증(08-13), `processInput`(08-16), Row·Column·Divider 컴포넌트(08-17), 프로퍼티 팩토리 확장(08-18), CheckBox(08-18), List·Tabs·Slider와 기본 카탈로그 계약(08-19), Placeholder 정리(08-20), Slider의 새 `SliderState` API와 `Card` 계약 합류(08-20~21). 하루 이틀이 아니라 **하루에 여러 번** 움직이는 중입니다.
- **아티팩트는 없습니다.** Google Maven에 `androidx.a2ui` 그룹 자체가 없습니다(master-index 없음, group-index 404, POM 404). 릴리스 페이지의 `androidx.a2ui:a2ui` 좌표는 **존재하지 않는 모듈**입니다.
- **프로토콜은 v0.9 고정입니다.** 스펙이 v1.0으로 가는 중이지만 구현은 `v0.9`/`v0.9.1`만 받습니다.
- **컴포넌트 카탈로그는 10/18입니다.** Text·Button·Card·Row·Column·Divider·CheckBox·List·Tabs·Slider. 그것도 quarantine 모듈이고, 입력을 받는 것은 CheckBox와 Slider 둘뿐입니다. 이름·날짜·평점을 받으려면 여전히 직접 써야 합니다.
- **라이브러리가 카탈로그를 주기 시작했습니다.** 8/17에 들어왔다 되돌려졌던(`120e2b24` → `1468f10b`) 기본 카탈로그 계약이 8/19 `Revert^2`(`cd391bdf`)로 재착륙했습니다. `A2uiBasicCatalogV1`이 컴포넌트 타입·프로퍼티 스키마·테마 스키마·카탈로그 ID를 라이브러리 쪽에 고정하고, `materialA2uiBasicCatalogV1(...)`이 M3 구현을 통째로 내줍니다. **계약에 들어 있는 컴포넌트는 `Text`(8/19)와 `Card`(8/21) 둘**이고 나머지 16개는 `TODO(b/547851648)`입니다. 이틀에 하나꼴이니, 이 문서를 읽는 시점의 숫자는 둘보다 클 것입니다.
- **상류가 배포된 material3보다 앞서 갑니다.** 8/20 `MaterialSliderComponent`가 `SliderState(trackRange = ...)`로 이사했고 같은 날 모듈의 의존이 `material3:1.5.0-alpha26`에서 `project(":compose:material3:material3")`로 바뀌었습니다. 즉 **지금의 `material3-a2ui`는 AOSP 트리 밖에서 그대로 컴파일되지 않습니다.** 이 저장소처럼 소스를 가져다 쓰는 쪽은 파일 단위로 리비전을 고정하게 됩니다. 그리고 그 고정은 **상류가 파일을 옮기면 같이 따라가야 합니다.** 9/1 `Slider`가 계약으로 들어가면서 `MaterialSliderComponent.kt`가 사라졌고, `a2ui-compose-labs/androidx-a2ui/`의 핀도 `catalog/MaterialA2uiBasicCatalogV1Slider.kt`로 옮겼습니다. 9/2 `7ac433e`의 ktfmt 0.64 재포맷도 핀에 그대로 반영해, 핀과 상류의 차이는 **여전히 `SliderState(trackRange = ...)` 한 곳뿐**입니다. Maven 최신은 아직도 `1.5.0-alpha27`(2026-09-03 재확인).

정리하면, **프레임워크는 읽고 배울 만큼 완성돼 있고, 컴포넌트 카탈로그는 절반쯤 쓰였고, 배포는 시작되지 않았습니다.**
