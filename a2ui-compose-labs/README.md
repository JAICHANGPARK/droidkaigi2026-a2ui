# A2UI Compose Labs

Companion app for the DroidKaigi 2026 talk
**"A2UI for Android: Safely Rendering AI-Generated UI with Jetpack Compose"**.

A minimal, readable A2UI renderer as its own library module, one unified
assistant, eight demos that each isolate a single mechanism, and two that run
on Google's own `androidx.a2ui` instead of ours.

Demos 1–3 and 5 replay recorded A2UI **JSONL** line by line, exactly like an
agent streaming over the wire, so they need no server and no API key. The
Assistant and demos 4, 6, 7, 8 and 10 put a real Gemini on the other end of the
same pipe, and fall back to a recording when there is no key (or no network).

## Demos

| Demo | What it shows | Source |
|---|---|---|
| ★ Assistant | **The point of the whole app.** One catalog, one chat: device health, sales charts, albums, playlists, surveys, table bookings and food orders. The agent picks the components, the app supplies every number | Gemini + recordings |
| 1. Chat assistant | Agent UI grows inside a chat bubble (progressive rendering, `updateDataModel` live change) | `assets/chat_demo.jsonl` |
| 2. Contact form | Basic-catalog form, two-way binding (local!), `action` round-trip with resolved `context` | `assets/contact_form.jsonl` |
| 3. Playlist builder | Custom catalog (`SongRow`, `PlaylistCard`): the agent may only use the app's own components | `assets/playlist_demo.jsonl` |
| 4. Survey generator | **Describe a form, get a form.** The case A2UI actually wins: the structure differs per request. A generated survey *is* five lines of JSONL, so generate once and replay it for every respondent with no model in the loop | Gemini, `assets/survey_*.jsonl` |
| 5. Album browser | Two surfaces, template lists, an action the app answers itself | `assets/album_*.jsonl` |
| 6. Analytics | Canvas charts, app-owned numbers, tap a slice to drill down. The agent never sees a figure | Gemini + `SalesData` |
| 7. Dining | **The talk's worked example.** One chat that books a table *and* takes a delivery order: quantity steppers, a total computed by a catalog function, payment, live tracking, and a review survey. Sessions persist, and a delivery keeps moving while you are on another screen | Gemini + `assets/dining_*.jsonl` |
| 8. Live agent | **The full loop, bare.** The catalog schema goes into Gemini's system prompt, Gemini answers with components, and the same schema validates every message. A wire log shows what was applied and what was rejected | Gemini, or `assets/live_agent_fallback.jsonl` |
| 9. Two dialects | The booking form and the café survey rendered **twice**: `androidx.a2ui` reading a v0.9.1 wire, this app's renderer reading the v1.0 twin. Both halves are live, and both quote the same catalog, so the protocol version is the only difference | `androidxa2ui/{Booking,Survey}Scripts.kt` |
| 10. Support satisfaction | **A CSAT form written per closed ticket**, rendered by `androidx.a2ui`. Four tickets, four different sets of five questions. A late parcel and a locked account share no question worth asking, so there is no template. One or two stars gets a follow-up the **app** writes into the surface that is already on screen | Gemini (v0.9.1 dialect), or `androidxa2ui/CsatScripts.kt` |

Run: open in Android Studio, or `./gradlew assembleDebug`, then
`android install --apks=app/build/outputs/apk/debug/app-debug.apk` and
`adb shell am start -n com.example.a2uicomposelabs/.MainActivity`.

### Live-agent setup (optional)

Every live demo runs without a key: it falls back to a recorded response, which
is also what saves the talk if the stage network dies.

To drive them with a real model, open **Settings** in the app (home screen, or
the link on demo 8) and paste a Gemini API key. "List models for this key" asks the
API which models the key can actually call, so there is no guessing at model
names. The key is stored in the app's private preferences and takes precedence
over anything supplied at build time, so the recommended setup is to build with
no key at all and configure the app on the device.

If you prefer a build-time key instead (handy for repeat installs), put it in
`local.properties` (gitignored, never committed):

```properties
GEMINI_API_KEY=your-key-here
```

That value reaches `BuildConfig` for **debug builds only**; the release variant
is compiled with an empty key, so a distributable APK can never carry one. The
environment is deliberately *not* consulted: an ambient `GEMINI_API_KEY` in a
shell would otherwise get baked into an APK without anyone deciding to put it
there.

Either way the key stays out of the repository. A key that reaches the device is
still extractable by whoever holds the device, which is fine for a conference
demo and wrong for a shipped app: in production the model is called from your own
backend, or through Firebase AI Logic, so the key never reaches the device at
all. `NoHardcodedSecretsTest` fails the build if a key-shaped literal ever
appears in a tracked file.

## Module structure

- **`:a2ui-renderer`**: the A2UI v1.0 renderer as a standalone Android library module
  (`com.android.library`, AAR). Public API: `A2uiClient`, `A2uiSurface`, `ComponentRegistry`,
  `BasicCatalog`, `A2uiCatalog`/`BasicCatalogSchema`, `A2uiSchema`, `A2uiSchemaValidator`,
  `BindingScope`, `A2uiAction`, `A2uiMessage`, `JsonPointer`, `replayAsset`.
  No network code lives here; the renderer never talks to a model.

  Its packages are named after the AOSP modules they answer to, so the two trees can be
  read side by side:

  | Package | Files | Lines | `androidx.compose` imports | Answers to |
  |---|---|---|---|---|
  | `a2ui.model` | 6 | 1,357 | **0** | `a2ui-model`, the wire: messages, actions, schema, catalog, functions |
  | `a2ui.engine` | 4 | 519 | **0** | `a2ui-engine`, for validation, dynamic-value evaluation, string templates, JSON Pointer |
  | `a2ui.runtime` | 3 | 587 | 6 | `compose-runtime`, for snapshot state, binding scope, the message processor |
  | `a2ui.ui` | 2 | 96 | 6 | `compose-ui`, the component contract and the recursive surface |
  | `a2ui.catalog` | 3 | 1,223 | 138 | `material3-a2ui`, the eighteen components and the fourteen functions |
  | `a2ui.testing` | 1 | 24 | 0 | `compose-ui-testing`, JSONL replay for the demos |

  The split is drawn on one line: **`model` and `engine` have zero Compose imports.** That is
  the same line AOSP draws, and it is checkable here the same way, with `grep -c "^import
  androidx.compose"` over either package returns 0. Everything Compose knows about lives from
  `runtime` up.

  Two caveats, visible now that the packages are separate and invisible while everything sat in
  one: `model.A2uiExecutionContext` holds a `runtime.SurfaceState` and an
  `engine.A2uiDynamicEvaluator`, and `model.BasicCatalogSchema` names `catalog.BasicCatalogFunctions`.
  So the dependency arrows are not yet strictly downward. The folders describe the layering,
  they do not enforce it. Separate Gradle modules would; that is the next step, not this one.
- **`:app`**: demo app depending on `project(":a2ui-renderer")` *and* on `project(":androidx-a2ui")`
  for demos 9 and 10; the assistant and the ten demos, the agent
  side (`agent/GeminiAgent.kt`, `agent/A2uiSystemPrompt.kt`, `agent/A2uiPayloadFixer.kt`),
  and runtime configuration (`agent/AgentSettings.kt`, `SettingsScreen.kt`).

## Renderer design (`:a2ui-renderer`): the talk's slides, as code

| Slide concept | File / class here |
|---|---|
| Message parsing (`createSurface` / `updateComponents` / `updateDataModel` / `deleteSurface`) | `model/A2uiMessage.kt` |
| Message processor, reject-at-the-door | `runtime/A2uiClient.kt` |
| Catalog as data: one declaration feeding both the prompt and the validator | `model/A2uiCatalog.kt` |
| JSON Schema node types (`oneOf`, `enum`, `additionalProperties`, …) | `model/A2uiSchema.kt` |
| Schema validation before state is touched | `engine/A2uiSchemaValidator.kt` |
| Surface state = `mutableStateMapOf` + `mutableStateOf` (message → state → recomposition) | `runtime/SurfaceState.kt` |
| JSON Pointer (RFC 6901) data binding | `engine/JsonPointer.kt`, `runtime/BindingScope.kt` |
| Catalog allowlist enforced in code (unknown → skip, never crash, never guess) | `ui/ComponentRegistry.kt`, `catalog/BasicCatalog.kt` |
| Surface = a composable; recursive adjacency-list rendering, bounded depth | `ui/A2uiSurface.kt` |
| Renderer → agent `action` with resolved context | `model/A2uiAction.kt`, `runtime/BindingScope.dispatchAction` |
| LLM streaming (JSONL, one message per line) | `testing/JsonlReplay.kt` |
| Making Gemini speak A2UI (slide 33): system prompt = role + catalog schema + examples | `app/…/agent/A2uiSystemPrompt.kt` |
| Components as a tool call, not as text: the agent assembles the messages, so the model never punctuates one | `app/…/agent/GeminiAgent.kt` (`streamUi`, `applyOneCall`) |
| Refusals go back to the model itemised, so it rewrites one component and not the screen | `app/…/agent/GeminiAgent.kt` (`toolResult`) |
| The three autofixes the official agent SDK applies, on the agent side only | `app/…/agent/A2uiPayloadFixer.kt` |
| The agent call itself (SSE streaming, no HTTP dependency) | `app/…/agent/GeminiAgent.kt` |

### One catalog, two jobs

`BasicCatalogSchema` is declared once, as data, and used twice: `toJsonSchema()`
serializes it into the agent's system prompt, and `A2uiSchemaValidator` enforces
the same tree on everything that comes back. What the model is told it may emit
and what the renderer will accept cannot drift, because they are one object.
`CatalogDriftTest` additionally pins the schema's component set to the set the
registry can actually draw.

### The third form a property can take

The spec gives a property three forms, not two: a literal, a `{"path"}` binding,
and a `{"call"}` into the catalog's **functions**, and they nest, because
arguments are evaluated before the function runs.

`BasicCatalogFunctions` implements all fourteen of the Basic Catalog's:
`required` `length` `numeric` `email` `regex` validate, `and` `or` `not` combine
those results, `formatString` `formatNumber` `formatCurrency` `formatDate`
`pluralize` format, and `openUrl` acts. `formatString` carries the spec's little
interpolation language: `${'$'}{/path}`, nested `${'$'}{fn(arg:…)}` calls, `\${'$'}{` escaping.

That is the entire computation surface. There is no arithmetic, no comparison, no
assignment, and no way to name anything the app did not publish: `A2uiClient`
rejects a component that calls a function the catalog does not list, and the
schema pins each call to the return type its property expects.

`checks` builds on it: `[{"condition": <boolean>, "message": "…"}]` on any input.
The agent says *what* must hold; the app's own functions decide whether it does
(`BindingScope.checkFailures`).

Defense layers implemented: parse rejection and per-component **schema
validation** (`A2uiClient`: unknown component, unknown function, wrong property
type, invented property), registry allowlist, `MAX_COMPONENTS` / `MAX_DEPTH` /
evaluator-depth caps, action mediation (demos decide what an action does),
local-only two-way binding, and an https-only `A2uiUrlPolicy` gating every media
URL *and* `openUrl`.

Still not implemented: `callableFrom` + `INVALID_FUNCTION_CALL` (the function
boundary between renderer and agent), `severity` on a check and auto-disabling a
Button when one fails, `ValidationResult`, bidirectional function-call RPC with
`functionCallId` pairing, `action.functionCall`, protocol-version negotiation,
per-surface message ordering (the official engine gives each surface an actor),
and an action-interceptor chain.

## Comparison with the official androidx implementation

The official (pre-alpha) source lives in AOSP `androidx-main:a2ui/`
(<https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:a2ui/>),
a copy is in [`../androidx-a2ui-source/`](../androidx-a2ui-source/)
(commit `ac85854`, 2026-09-02). The shapes match:

| This mini renderer | Official androidx.a2ui |
|---|---|
| `A2uiClient` + `A2uiMessage.parse` | `a2ui-engine` `A2uiCoreMessageProcessor`, `a2ui-model` `A2uiJsonMessageParser` |
| `A2uiSchema` + `A2uiSchemaValidator` | `a2ui-model` `A2uiSchema`/`A2uiSchemaKeyword` + `a2ui-engine` `A2uiCoreSchemaValidator` |
| `A2uiCatalog.toJsonSchema()` | `serializeCatalogToJsonSchema` in `A2uiCoreCatalogSerializer` |
| `A2uiComponentDefinition` (`name`, `description`, `propertySchema`) | `A2uiCoreComponentDefinition` (same three members) |
| `SurfaceState` | `A2uiCoreSurfaceModel` / `compose-runtime` `A2uiComponentState`, `A2uiDataModel` |
| `ComponentRegistry` + factory lambdas | `compose-ui` `A2uiCatalog(catalogId, components, functions)` + `A2uiComponent` (`name`, `properties`, `@Composable Content(scope, properties, modifier)`) |
| `BindingScope.readString/write` | `A2uiComponentScope.bind()` / `bindUpdater()` |
| `BindingScope.dispatchAction` | `A2uiComponentScope.dispatchAction()`, mediation via `A2uiActionInterceptor` |
| progressive placeholder (`?: return`) | `A2uiReadinessEvaluator` / `A2uiComponent.isReady()` |

Note: `androidx.a2ui:a2ui:1.0.0-alpha01` is listed on the release page
(July 2026) but is **not on Google Maven** (re-checked 2026-08-17: no `a2ui`
group in master-index, `group-index.xml` 404, POM 404), and that coordinate
names a module that does not exist. The real artifacts would be
`androidx.a2ui:a2ui-model`, `androidx.a2ui:a2ui-engine`, and
`androidx.a2ui.compose:{compose-runtime, compose-ui, compose-ui-testing}`.
Read the AOSP source instead of depending on the artifact.

Also worth knowing: the official client is pinned to protocol **v0.9**:
`A2uiJsonMessageParser.SUPPORTED_VERSIONS = listOf("v0.9", "v0.9.1")`, so it
would reject the v1.0 messages this renderer accepts. And Google's own Material
catalog for A2UI lives outside `a2ui/`, in
[`../androidx-material3-a2ui-source/`](../androidx-material3-a2ui-source/),
**14 of 18** as of 29 Aug 2026, in a quarantined module.

Most of them are no longer `Material*Component` objects. Upstream introduced
`A2uiBasicCatalogV1` in `compose-ui`: a class holding one typed component
interface per basic-catalog entry, plus the catalog id, the theme schema and
the function list. It started with `Text` (19 Aug) and by 27 Aug held ten:
`Text`, `Image`, `Icon`, `Card`, `Row`, `Column`, `List`, `Tabs`, `Button`,
`DateTimeInput`, with the other eight still a `TODO(b/547851648)`. A design
system claims the basic catalog by implementing those interfaces:
`A2uiBasicCatalogV1.Text` asks for `TypedContent(text, variant, modifier)`,
`Button` for `TypedContent(childId, variant, action, modifier)`, and the
contract does the property binding for you.

Each time a component joins the contract, the identically named public object
is deleted: `MaterialTextComponent` (19 Aug), `MaterialCardComponent` (21 Aug),
`Material{Button,Row,Column}Component` (24 Aug),
`Material{Icon,List,Tabs}Component` (27 Aug). Only
`Material{Divider,CheckBox,Slider,TextField}Component` are still standalone, and
they are on the same path. Reach everything through
`MaterialA2uiBasicCatalogV1Defaults.*`, or take the whole catalog from
`materialA2uiBasicCatalogV1(image, urlOpener, messageFormatter, localeProvider)`
(`image` is the one required argument: it needs your `A2uiImageRenderer`).
The catalog id it declares is still
`https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json`.

One new runtime API is worth knowing about: `ProvideActionInterceptor` (27 Aug,
`compose-runtime`) lets an ancestor composable intercept actions dispatched by
descendants. The interceptors chain innermost-to-outermost through a
`CompositionLocal`; returning `true` consumes the action so the surface never
sees it. It is how container components such as modals are meant to turn a
child button's action into local UI state.

### Talking to androidx.a2ui with a live agent (demo 10)

Demos 9 and 10 compile the AOSP snapshot as `:androidx-a2ui` and render through
the real `A2uiMessageProcessor`, so two things this app takes for granted stop
being true, and demo 10 is where both are handled rather than described.

**The wire is older.** Every other demo prompts for spec v1.0 and lets the shared
tool (`agent/A2uiToolCall.kt`) write `{"version":"v1.0", …}` envelopes around the
model's components. `A2uiJsonMessageParser` reads the version field first and
throws, so `androidxa2ui/CsatPrompt.kt` teaches the model the v0.9.1 component
shapes: `Card.child`, a two-component labelled `Button`, `action.event`,
`TextField.valid`/`error` instead of `checks`, and `DialectEnvelope.kt` splits
the opening message back into `createSurface` + `updateDataModel` and stamps the
older version on the rest. About thirty lines, no component touched: what a
protocol version costs you is an envelope, and what it costs the agent is a
prompt.

**The catalog is thinner.** `material3-a2ui` ships sixteen of the spec's eighteen
components, fifteen of them behind `A2uiBasicCatalogV1` after `Divider`, `CheckBox`
and `Slider` joined on 1 Sep 2026 and `Video` and `AudioPlayer` on 2 Sep, but still
no `TextField`, so
`Question`, `StarRating`, `ChoicePicker` and `TextField` are written in this app
against `androidx.a2ui.compose.ui.A2uiComponent`, the same interface every
`A2uiBasicCatalogV1` component implements. Each androidx screen
then declares its own catalog (`AndroidxBookingCatalog`, `AndroidxSurveyCatalog`,
`AndroidxSupportCatalog`), because a catalog is an allowlist: the café survey
cannot draw a `ChoicePicker` even though the file it lives in is right there.

**The refusal is asynchronous.** `A2uiClient.apply` answers on the spot; the
engine queues the message and reports a refusal later on `outboundEvents`, the
same channel that carries user actions. So the tool's "what did the renderer say"
result waits a beat before answering the model. See `accept()` in
`SupportCsatDemo.kt`.
