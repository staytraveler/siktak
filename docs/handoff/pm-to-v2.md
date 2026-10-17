# PM → V2

> 작성: 2026-10-17 13:40 · PM클로드

처음이라면: 나는 기획 세션의 PM클로드야. 너는 **V2**, 매칭 엔진 전담 개발 세션이야. 메인 개발 세션(V1)은 원래 폴더 `C:\Users\Admin\Desktop\siktak` 에서 배포 작업 중이고, 너는 git worktree `C:\Users\Admin\Desktop\siktak-matching` (브랜치 `feat/matching-engine`)에서 일한다.

## 먼저 읽을 것
1. 네 폴더의 `CLAUDE.md` (특히 7장 매칭 엔진 규칙, 11장 세션 규칙. 11장의 "V클로드"는 너에게도 적용되고, 네 채널 파일만 다르다)
2. `docs/domain/matching.md` 전체, `docs/domain/states.md` 4장, `docs/demo-scenario.md` 3·4장
3. 작업지시서: **`C:\Users\Admin\Desktop\siktak\docs\tasks\4.1-matching-engine-core.md`** (원래 폴더에 있다. 네 worktree에는 아직 없을 수 있음)

## 소통 채널 (원래 폴더의 파일을 절대경로로 사용)
- PM → V2: `C:\Users\Admin\Desktop\siktak\docs\handoff\pm-to-v2.md` (이 파일)
- V2 → PM: `C:\Users\Admin\Desktop\siktak\docs\handoff\v2-to-pm.md` (작업 완료·질문 시 덮어쓰기)
- 사용자는 "PM 확인" / "V2 완료" 신호만 전달한다. 이 두 파일은 **원래 폴더에서 V1이 커밋**하니, 너는 이 파일들을 네 브랜치에 커밋하지 마.

## 할 일
- 4.1 지시서대로 매칭 엔진 코어와 테스트를 구현해. 범위 안에서는 확인 없이 진행하고, 명세와 충돌하거나 해석이 갈리는 곳만 `v2-to-pm.md`로 물어봐.
- 계획은 사용자에게 짧게 보여주고 바로 시작해도 된다.
- 노트북 성능이 넉넉하지 않다. 테스트는 매칭 엔진 테스트만 골라 돌리고(`./gradlew test --tests 'com.siktak.matching.*'`), 전체 테스트는 마지막에 한 번만 돌려.
- 끝나면 지시서 완료 보고를 채우고(네 worktree의 지시서 사본이 아니라 원래 폴더 파일에), `v2-to-pm.md`를 쓰고, 사용자에게 "PM에게 전달해주세요"라고 알려줘.
