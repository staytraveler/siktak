# V2 → PM

> 작성: 2026-10-17 16:13 · V2 · 지금 하는 블레이버스 태스크: **없음 (4.4 완료, PM 검토 대기)**

## 결과 요약
- 4.4 완료. main 머지(`181f466`) 후 기능별 커밋·push: `d1d3b37` V3·예외 큐·식당 배정 / `bd124c0` 엔진 v2 연결 / `c79688a` 조회 API·시드 / `330b78d` 문서. main 머지는 안 함
- **DB 통합 테스트(Neon dev)로 J1·J2가 demo-scenario 4장과 일치**: S2 6인, S1 3테이블(user05·06 분리), user15 2단계 재배치, user14 WAITING → J2에서 ALTERNATIVE_OFFERED(추천 S4→S5), user16 제외, S1 테이블 1개 `VENUE_UNAVAILABLE` 예외, E20 재실행 중복 없음, 운영자 재배정
- 엔진 전환 `siktak.matching.engine=v2|simple` (기본 v2)
- 상세는 `docs/tasks/4.4-matching-pipeline.md` 완료 보고

## 블레이버스 체크할 태스크
- 최소 점수 기준 자동 확정 / 실패자 ALTERNATIVE_OFFERED 전환 / MatchRun 실행 기록 및 배치 사유 저장
- 식당·수용 인원 데이터 구조 / 배정 실패 시 대체 탐색 및 예외 전환
- ExceptionCase 저장 구조

## PM 확인 필요
1. **`TableMatcher` 포트 대신 `MatchingService` 분기**: 포트로는 여러 회차 재배치·사유·실패를 담을 수 없어서. v2는 "호출 회차 + 매칭 시각 도래한 미매칭 회차"를 한 번에 실행하고, 이미 매칭된 회차로 다시 부르면 그 실행을 돌려준다
2. 계정 제한 예외 = `SAFETY_REPORT`, 프로필 없음 = `DATA_MISSING`(WAITING 유지)
3. deferred는 M4a만, `respond_by` = 1순위 회차 시작 시각, 테이블 없는 대상 회차도 `MATCHED`
4. ERD `match_placement_logs`: decision `DEFERRED`·`EXCLUDED`, 컬럼 `from_slot_id`·`to_slot_id`·`cause_code` 반영 요청

## V1에게 전달할 것
- **마이그레이션 V3 사용함** (`V3__matching_pipeline.sql`). V1은 V4부터
- 2.4 스케줄러는 지금처럼 `MatchingService.runForSlot(slotId, null)`을 부르면 된다 (operatorId null = 스케줄러). S1·S2가 같은 시각이어도 한 번만 실행됨
- demo-v1 시드에서 지역·회차 생성 후 `DemoVenueSeed.seed(성수, 홍대합정, 강남 regionId, {"S1".."S5": slotId})`와 `ConversationContentSeeder.seedIfEmpty()` 호출, 프로필 `price_range`에 `UNDER_20K`/`20K_40K`/`OVER_40K` 값 넣기
- `Application`에 `match_fail_reason` 쓰기 메서드(예: `recordMatchFailReason(String)`) 추가 부탁. 지금은 [임시]로 flush 뒤 SQL 갱신 중이고, 메서드가 생기면 바꾼다
- `FlowSkeletonIntegrationTest`에 `siktak.matching.engine=simple`을 지정했다 (가짜 매칭 동작 검증용)

## 사용자 작업 요청
- 없음 (DB 테스트는 Neon dev에 임시 스키마를 만들어 돌린 뒤 삭제했다. public 스키마는 그대로)
