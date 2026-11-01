# .claude — 프로젝트 단위 클로드 코드 설정

- `settings.json`: 훅. Edit/Write 뒤 Java/Kotlin 파일이면 `spotlessApply`(gradlew가 있을 때만). 테스트는 매 편집마다 돌리지 않고 커밋 전(git pre-commit)·PR 직전에.
- 훅은 stdin JSON(`tool_input.file_path`)을 `jq`로 읽는다 — Git Bash에 `jq`가 없으면 `winget install jqlang.jq`. 클로드 코드 버전에 따라 훅 스키마가 다를 수 있으니 처음 켤 때 `claude --help`/문서로 한 번 확인.
- `skills/`: 1차 마감 뒤 반복 작업이 보이면 추가 — `/add-endpoint`(컨트롤러→서비스→테스트→openapi 재생성→frontend-handoff 기록). 지금은 비움.
- 플러그인(superpowers, pr-review-toolkit, context7 등)은 사용자 전역 설정에서 관리. 이 폴더엔 두지 않는다.
