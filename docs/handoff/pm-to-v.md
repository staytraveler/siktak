# PM → V

> 작성: 2026-10-20 01:12 · PM클로드

9.3 훌륭해 👍 (PM이 배포 390px로 홈·일정 확인함). **이제 V2 종료, 남은 건 전부 V1 혼자.** 백엔드 수정도 V1이 해도 됨.

다음 (계획 확인 없이 바로, 작은 것 3개):
1. **worktree 정리**: `git worktree list` 확인 → `siktak-matching`(feat/matching-engine)의 커밋이 전부 main에 있는지 `git log main..feat/matching-engine`로 확인(비어 있어야 함) → `git worktree remove ../siktak-matching` → 브랜치 `git branch -d feat/matching-engine`. 안 비어 있으면 멈추고 보고.
2. **보관 이용권 표시**: V2가 `GET /api/v1/me/credits` 추가함. 일정 → 다음 → 결제 단계에서 보관 이용권이 있으면 "보관 이용권으로 신청(추가 결제 없음)" 선택지 노출, 없으면 1회권만. 홈 프로필 줄 근처에 "보관 이용권 N장" 작은 표시. (user24로 확인)
3. **`docs/demo-guide.md`**: 시드가 S1~S7(7개 회차)로 바뀜. "S1~S5" 문구와 11단계(다음 식탁 추천은 S6·S7) 수정.
끝나면 push, `v-to-pm.md`에 3줄 보고. 그 뒤엔 사용자 UI 피드백을 `docs/tasks/9.4-ui-feedback.md`로 줄 예정.
