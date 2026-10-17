# V2 → PM

> 작성: 2026-10-17 17:05 · V2 · 지금 하는 블레이버스 태스크: **없음 (6.1 완료, 커밋 승인·PM 검토 대기)**

## 결과 요약
- 6.1 매칭 실패 해결 완료. `feat/matching-engine`에 main(2.4) 머지 + 6.1 구현. **커밋·push는 아직 안 함** (사용자 승인 대기)
- 일정 이동(M7, 결제 공유) · 이용권 보관(M8, 크레딧) · 자동 환불(P6→P8→P9, 재시도 후 P10 + 예외) · 미응답 보관(M8a, `RESOLUTION_TIMEOUT` 작업) · 운영자 재처리(P11)·수동 완료(P12) · 중복 환불 방지(환불 키 + 결제 행 잠금)
- 완료 조건 DB 테스트 전부 통과: user14 → S5 결제 1건 유지, user23 REFUNDED, user24 AUTO_KEPT + 크레딧 1개, 실패 주입 → REFUND_FAILED + 예외 1건, 동시 환불 → PG 취소 1회
- 전체 `./gradlew test` 123건 통과(DB 포함), 프론트 build·lint 통과
- 상세는 `docs/tasks/6.1-resolution-flows.md` 완료 보고

## 블레이버스 체크할 태스크
- 결제 유지 일정 이동 처리
- 이용권 보관 처리
- 자동 환불 및 실패 시 예외 전환
- 중복 환불 방지

## PM 확인 필요 (상세는 완료 보고)
1. **P7b 전이 추가** (`REFUND_AVAILABLE → PAID`, SCHEDULER, 미응답 보관용) — 사용자 확인 완료, states.md 반영
2. main 머지 후 V1의 `DemoSeedSchedulerIntegrationTest` 기대값(`match_runs` 수)이 엔진 v2와 달라 고쳤다 — V1에 전달 부탁
3. 마이그레이션 V5를 먼저 넣었다 — V1의 V4가 V5보다 먼저 배포되게 머지 순서 조정 필요
4. Neon dev `v2work`에 남은 예전 데모 시드를 지워도 되는지
5. 응답 기한이 지난 뒤의 선택은 409로 막는다(자동 보관에 맡김) — 괜찮은지

## 사용자 작업 요청
- 커밋·push 승인 (main 머지 커밋 + 6.1 기능 커밋)
