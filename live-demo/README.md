# Live Demo Kit: "스펙만 주고 40분 안에 렌더러가 나온다"

발표 시작 직후 에이전트(Claude Code)에게 킥오프 프롬프트를 던지고,
발표가 끝나는 시점에 **빌드된 앱이 에뮬레이터에서 도는 것**을 보여주는 퍼포먼스의 준비물.

## 파일 구성

- `kickoff-prompt.md`: 무대에서 그대로 붙여넣는 프롬프트 (영어, 해설 포함)
- `kickoff-prompt.txt`: 같은 프롬프트의 순수 텍스트판 → `claude "$(cat kickoff-prompt.txt)"`
- `setup-stage.sh`: 무대 프로젝트 자동 셋업 (스캐폴드→패치→컨텍스트 배치→워밍 빌드→오프라인 검증)
- `stage-context/CLAUDE.md`: 무대용 프로젝트 루트에 미리 넣어둘 컨텍스트 파일 (Claude Code용)
- `stage-context/AGENTS.md`: 같은 내용의 도구 중립판 (Antigravity/Codex/Gemini CLI 등이 읽음)
- `stage-context/spec-summary.md`: 에이전트가 리서치 없이 바로 구현하도록 압축한 스펙 요약
- `stage-context/fixtures/contact_form.jsonl`: 데모에서 재생할 "에이전트 메시지" (결과 결정적)

## 핵심 원칙: 무대에서 네트워크 금지

오늘(2026-08-01) 리허설에서 실측한 리스크: 의존성 다운로드 28분 타임아웃,
불완전 JDK(jlink 없음), DNS 순단. 전부 **사전 준비**로 제거한다:

1. 코드 생성은 로컬 (Claude Code API 호출만 네트워크, 폰 테더링 백업 준비)
2. Gradle 빌드는 `--offline` 강제. 캐시가 데워져 있으므로 다운로드 0건
3. 에뮬레이터는 발표 전 부팅 완료 상태로 대기

## 사전 준비 (T-1일, 리허설 겸)

```bash
# 1. JDK (이미 설치됨, 확인만)
/opt/homebrew/opt/openjdk@21/bin/java -version

# 2. 무대용 스캐폴드 생성 (렌더러 코드는 없는 빈 템플릿)
cd ~/stage && android create empty-activity --name "A2UI Live" --output=./a2ui-live
cd a2ui-live
# libs.versions.toml에 kotlinx-serialization-json 1.9.0 추가 (a2ui-compose-labs와 동일하게)
# app/build.gradle.kts: jvmToolchain(21), compileOptions 21 (a2ui-compose-labs와 동일하게)

# 3. 캐시 데우기: 딱 한 번 온라인 빌드 (이후 무대에선 --offline)
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleDebug

# 4. 컨텍스트 파일 배치
cp <this-kit>/stage-context/CLAUDE.md ./CLAUDE.md
cp <this-kit>/stage-context/spec-summary.md ./spec-summary.md
mkdir -p app/src/main/assets && cp <this-kit>/stage-context/fixtures/contact_form.jsonl app/src/main/assets/

# 5. 에뮬레이터 준비
android emulator create --name stage --device "pixel_8"   # 시스템 이미지 다운로드 포함
android emulator start stage

# 6. 리허설 2회 이상: 킥오프 프롬프트 실행 → 소요 시간 기록
#    그중 1회는 화면 녹화 → 2~3분 배속 타임랩스 = 폴백 영상
# 7. 리허설 성공 결과물은 git commit. 무대에서 실패 시 checkout 폴백
```

## 무대 타임라인 (run of show)

| 시각 | 발표 | 터미널 |
|---|---|---|
| ~2:00 (슬라이드 3 직후) | "I just gave an agent only the spec. We'll check the result at the end." | 프롬프트 붙여넣고 Enter → 화면은 다시 슬라이드로 |
| ~17:30 (슬라이드 17, 아키텍처) | "By the way, an agent is building exactly this, right now, backstage." | (건드리지 않음) |
| ~27:00 (슬라이드 25 끝) | 한 번 슬쩍 확인 (진행 중인지만) | 상태만 확인 |
| ~38:50 (슬라이드 35 후) | **리빌**: "Let's see what the agent built." | 에뮬레이터 화면 미러링 → 폼 데모 시연 |
| 실패 시 | "Networks are networks. Here is the run from last night." | 타임랩스 영상 재생 + 사전 빌드 APK 시연 |

## 리허설 기준 예상 소요 (2026-08-01 실측)

- 코드 작성(렌더러 9파일 + 데모): 캐시 워밍 상태에서 ~10–15분
- `--offline` 빌드: **56초** (캐시 완비 시)
- 설치+실행: ~30초
→ 총 ~15분 내외. 40분 예산 대비 여유 2배 이상. 단, 리허설로 반드시 실측할 것.

## 리빌 슬라이드(선택)

리빌을 정식 장표로 넣으려면 슬라이드 35와 36 사이에 1장 추가:
"Remember the agent from minute 2?" + 에뮬레이터 라이브 전환.
현재 덱이 ~43:50(콘텐츠 우선)이므로, 이걸 넣는다면 리허설에서 아웃라인의 트림 후보로 1분 이상 확보 필요.
