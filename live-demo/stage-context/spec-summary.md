# A2UI v1.0: condensed spec for the renderer (all you need)

One JSONL line = one JSON object = one message. Version tag: `"version": "v1.0"`.

## Agent → renderer messages (exactly one key per message)

```json
{"version":"v1.0","createSurface":{"surfaceId":"s1","catalogId":"...","components":[...],"dataModel":{...}}}
{"version":"v1.0","updateComponents":{"surfaceId":"s1","components":[...]}}
{"version":"v1.0","updateDataModel":{"surfaceId":"s1","path":"/user/name","value":"Jane"}}
{"version":"v1.0","deleteSurface":{"surfaceId":"s1"}}
```

- `components` is a FLAT list (adjacency list). Tree structure comes from `children: ["id", ...]`.
- Exactly one component has `"id": "root"`, and rendering starts there.
- Components may arrive in any order, across multiple messages. Missing children = not arrived yet → render nothing for them (progressive rendering).
- `updateDataModel`: upsert at JSON Pointer `path`; `value: null` deletes the key; path `""` or `/` replaces the whole model.

## Component shape

```json
{"id":"name_field","component":"TextField","label":"Name","text":{"path":"/name"}}
```

- `id` and `component` are reserved; everything else is a property.
- Any property value is either a literal (`"Contact us"`, `42`, `true`) or a data
  binding `{"path": "/json/pointer"}` resolved against the surface's data model.
- Container property: `children: ["child_id", ...]`.
- Input components (`TextField.text`, `CheckBox.value`, `Slider.value`) are TWO-WAY:
  render the value at the path, and write user input back to the same path.
  Writes are LOCAL ONLY, never sent anywhere per keystroke.
- Button action property:
  `"action": {"name": "submitForm", "context": {"name": {"path": "/name"}}}`
  On click, resolve every `{"path"}` in context against the data model, then emit:

## Renderer → agent `action` message

```json
{"version":"v1.0","action":{
  "name":"submitForm",
  "surfaceId":"contact_form",
  "sourceComponentId":"submit",
  "timestamp":"2026-09-10T05:00:00Z",
  "context":{"name":"Jane","email":"jane@x.com","subscribed":true}}}
```

## Component set to implement (props)

| component | props |
|---|---|
| Text | `text` (dynamic string) |
| Column / Row / Card / List | `children` (ids); Card wraps children in a Material Card with padding |
| Divider | none |
| Button | `label` (dynamic string), `action` |
| TextField | `label`, `text` (two-way path) |
| CheckBox | `label`, `value` (two-way boolean path) |
| Slider | `label`, `min`, `max` (numbers), `value` (two-way number path) |

Security invariants: declarative data only, never execute anything from messages;
unknown `component` → skip; cap components per surface (200) and render depth (24).
