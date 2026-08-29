# A2UI Live — stage project context

You are building a live conference demo. Time matters; correctness matters more.

## Environment (verified before the talk — trust this, do not re-verify)

- JDK: `JAVA_HOME=/opt/homebrew/opt/openjdk@21` (full JDK incl. jlink). Always export it before gradlew.
- Gradle/AGP/Kotlin: already configured in this project; **all dependencies are cached**. Build with `--offline` ALWAYS. Never remove that flag, never add new dependencies — everything you need (Compose BOM, Material 3, kotlinx-serialization-json) is already declared and cached.
- An Android emulator is already booted. `adb` and the `android` CLI are on PATH.
- The A2UI spec facts you need are condensed in `spec-summary.md` — do not search the web.

## Hard rules

- No network access (except your own model API). No new dependencies. No plugin changes.
- kotlinx-serialization: use the runtime JSON tree API (`Json.parseToJsonElement`, `JsonObject`, `JsonPrimitive`). Do NOT add `@Serializable` classes for protocol types.
- Never render a component type that is not in the registry — skip it silently.
- Keep files small and readable: this code will be shown on a conference screen.

## Definition of done

`./gradlew assembleDebug --offline` is green, the app runs on the emulator,
`contact_form.jsonl` replays visibly (progressive rendering), typing updates state,
Submit shows the action JSON containing the typed values. Screenshot captured.
