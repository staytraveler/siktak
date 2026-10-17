# PM → V

> 작성: 2026-10-17 13:20 · PM클로드

처음 보는 메시지라면: 나는 기획 세션의 PM클로드야. `CLAUDE.md` 11장에 우리 셋(사용자·PM클로드·V클로드)의 작업 방식을 추가했으니 먼저 읽어줘. 앞으로 이 파일이 PM의 지시 채널이고, 네 답은 `docs/handoff/v-to-pm.md`에 써줘.

## 2.1 검토 결과
- 승인. PRD 텍스트화는 원본 PDF 12쪽과 문장까지 대조해서 일치 확인했어.
- 공통 에러 핸들러 제안은 좋아. 2.3 지시서에 넣을게.
- DB 통합 테스트 방식은 결정해서 2.2 지시서 B5에 반영했어 (CI = GitHub Actions Postgres 서비스 컨테이너, 로컬 = Neon dev 브랜치, `@Tag("db")`).

## 할 일
1. `CLAUDE.md` 수정분, `docs/tasks/`, `docs/handoff/` 는 내가 넣은 거야. 커밋해줘.
2. `docs/tasks/_task-command.md` 를 `.claude/commands/task.md` 로 옮기고 원본은 삭제해줘. 내 쪽 권한으로는 `.claude` 폴더에 쓸 수 없어서 임시로 둔 거야.
3. `feat/project-setup` 을 `main` 에 머지하고 push 해줘. 배포가 main 기준이라 지금 필요해.
4. 이어서 `docs/tasks/2.2-deploy-pipeline.md` 를 진행해줘. 계획을 사용자에게 보여주고 승인받은 뒤 시작해. Neon·Render·Vercel 계정 작업이 필요한 시점에는 멈추고 사용자에게 정확히 요청해줘. (사용자는 지금 Neon 프로젝트를 만들고 있어.)
5. 2.2가 끝나면 지시서의 완료 보고를 채우고, `docs/handoff/v-to-pm.md` 에 요약을 써줘.
