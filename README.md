# A2UI for Android — demos & reference code

Working code from the DroidKaigi 2026 talk
**"A2UI for Android: Safely Rendering AI-Generated UI with Jetpack Compose."**

[A2UI](https://a2ui.org) is an open protocol (announced by Google, Dec 2025) where an
AI agent sends declarative UI **as data** — JSONL messages bound to a catalog of
components the app already owns — and the app renders it natively in Compose. The
agent never ships code, never ships a layout, and never sees a number the app did
not hand it.

Spec: <https://a2ui.org> · Protocol source: <https://github.com/a2ui-project/a2ui>

> This repository holds **only the code**: the renderer, the demo app, the AOSP
> source snapshot it compiles against, and the live-demo kit. Slides, speaker
> scripts and the rest of the presentation package are not here.

## What's in here

| Path | What it is |
|---|---|
| [`a2ui-compose-labs/`](a2ui-compose-labs/) | The Android project. An A2UI v1.0 renderer as a standalone library module (`:a2ui-renderer`) implementing the full 18-component Basic Catalog, plus a demo app with one unified assistant and ten demos. |
| [`androidx-a2ui-source/`](androidx-a2ui-source/) | Byte-for-byte snapshot of the official `androidx.a2ui` source from AOSP `androidx-main:a2ui/`. **Not a reading copy — it is compiled.** |
| [`androidx-material3-a2ui-source/`](androidx-material3-a2ui-source/) | Same, for `androidx-main:compose/material3/material3-a2ui` — Google's Material catalog for A2UI. |
| [`live-demo/`](live-demo/) | Kit for the on-stage live build: kickoff prompt, agent context files (`CLAUDE.md` / `AGENTS.md`), a compressed spec summary, a deterministic JSONL fixture, and an offline stage-setup script. |
| [`04-aosp-source-reference.md`](04-aosp-source-reference.md) | Folder-by-folder, file-by-file walkthrough of the AOSP source — what every module, package and key class does, mapped to the spec concept it implements, plus the full path a JSONL line takes to become a Compose UI. Written in Korean. |

## The demo app

Ten demos plus an assistant, in `a2ui-compose-labs/app`. Demos 1–3 and 5 replay
recorded JSONL line by line — exactly like an agent streaming over the wire — so
they need **no server and no API key**. The assistant and demos 4, 6, 7, 8 and 10
put a real Gemini on the other end of the same pipe, and fall back to a recording
when there is no key or no network.

| Demo | What it shows |
|---|---|
| ★ Assistant | One catalog, one chat: device health, sales charts, albums, playlists, surveys, table bookings, food orders — the agent picks the components, the app supplies every number |
| 1. Chat assistant | Agent UI grows inside a chat bubble (progressive rendering, live `updateDataModel`) |
| 2. Contact form | Basic-catalog form, two-way binding (local only), `action` round-trip with resolved `context` |
| 3. Playlist builder | Custom catalog — the agent may only use the app's own components |
| 4. Survey generator | Describe a form, get a form. A generated survey *is* five lines of JSONL, so generate once and replay it with no model in the loop |
| 5. Album browser | Two surfaces, template lists, an action the app answers itself |
| 6. Analytics | Canvas charts over app-owned numbers; tap a slice to drill down — the agent never sees a figure |
| 7. Dining | The worked example: one chat that books a table *and* takes a delivery order — steppers, a total computed by a catalog function, payment, live tracking, a review survey |
| 8. Live agent | The full loop, bare. Catalog schema into Gemini's system prompt, Gemini answers with components, the same schema validates every message. A wire log shows what was applied and what was rejected |
| 9. Two dialects | The same booking form and café survey rendered twice: `androidx.a2ui` on a v0.9.1 wire, this renderer on the v1.0 twin. Both halves live, both quoting the same catalog |
| 10. Support satisfaction | A CSAT form written per closed ticket, rendered by `androidx.a2ui`. Four tickets, four different sets of questions — no template |

Full detail, including the renderer's design and a table mapping each spec
concept to the file that implements it, is in
[`a2ui-compose-labs/README.md`](a2ui-compose-labs/README.md).

## Build and run

Needs **JDK 21** and an Android SDK with API 37.

Opening `a2ui-compose-labs/` in Android Studio and running `:app` is enough —
Studio writes the `local.properties` that points Gradle at your SDK. From the
command line that file does not exist (it is gitignored, as it should be), so
export the location yourself:

```bash
cd a2ui-compose-labs
export ANDROID_HOME=$HOME/Library/Android/sdk   # or wherever your SDK lives
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.a2uicomposelabs/.MainActivity
```

Tests:

```bash
cd a2ui-compose-labs
./gradlew test
```

Verified on JDK 21.0.12 / AGP with `compileSdk = 37`: `assembleDebug` and `test`
both green from a clean checkout of this repository.

> The `:androidx-a2ui` module compiles the two `androidx-*-source/` folders
> directly, reaching them as `rootProject.projectDir.parentFile`. **Keep this
> directory layout** — move `a2ui-compose-labs/` out on its own and demos 9 and 10
> stop building.

## Optional: driving the live demos with a real model

Every live demo runs without a key by falling back to a recorded response.

To use a real model, open **Settings** in the app and paste a Gemini API key. It is
stored in the app's private preferences and takes precedence over anything supplied
at build time, so the recommended setup is to build with no key at all and configure
the app on the device.

A build-time key is also supported, in `local.properties` (gitignored, never
committed):

```properties
GEMINI_API_KEY=your-key-here
```

That value reaches `BuildConfig` for **debug builds only** — the release variant is
compiled with an empty key, so a distributable APK can never carry one. The
environment is deliberately not consulted, so an ambient `GEMINI_API_KEY` cannot get
baked into an APK by accident. `NoHardcodedSecretsTest` fails the build if a
key-shaped literal ever appears in a tracked file.

A key that reaches the device is extractable by whoever holds the device. That is
fine for a conference demo and wrong for a shipped app: in production the model is
called from your own backend, or through Firebase AI Logic, so the key never reaches
the device at all.

## On the official `androidx.a2ui`

`androidx.a2ui:a2ui:1.0.0-alpha01` appears on the release page (July 2026) but is
**not on Google Maven**, and that coordinate names a module that does not exist. The
real artifacts would be `androidx.a2ui:a2ui-model`, `androidx.a2ui:a2ui-engine` and
`androidx.a2ui.compose:{compose-runtime, compose-ui, compose-ui-testing}`. Until they
ship, reading — and compiling — the AOSP source is the way in, which is what the
`androidx-*-source/` folders are for.

The official client is also pinned to protocol **v0.9/v0.9.1**, so it rejects the
v1.0 messages this repo's renderer accepts. Demo 9 runs both side by side.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).

`androidx-a2ui-source/` and `androidx-material3-a2ui-source/` are unmodified
snapshots of the Android Open Source Project, pinned by commit and git tree hash in
each folder's `SOURCE_COMMIT.txt`, and carry their own AOSP copyright headers. See
[`NOTICE`](NOTICE).
