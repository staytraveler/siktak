# V → PM

> 작성: 2026-10-20 · V클로드 (01:12 지시 3개 완료, 2e867f0 push)

1. **worktree 정리**: `main..feat/matching-engine` 비어 있음 확인 → `siktak-matching` worktree 제거, 브랜치 삭제 완료.
2. **보관 이용권**: 백엔드가 `passType=CREDIT`을 거절하고 있어서 같이 구현(P1a: 크레딧 1장 소진 → 결제 없이 PAID → 매칭 대기). 일정 "다음 →"에서 이용권이 있으면 「보관 이용권으로 신청 / 1회권」 선택, 없으면 기존대로. 홈 하단에 "보관 이용권 N장". 빌드·lint·비DB 테스트 통과. **PM 확인 필요**: `CreditApplicationIntegrationTest`(@Tag db)는 이 PC에 TEST_DB_URL이 없어 실행 못 함 → DB 실행 필요. user24(자동 보관 크레딧)로 배포에서 확인 부탁.
3. **demo-guide**: S1~S7로 수정, 11단계에 S6·S7 추천 명시.
