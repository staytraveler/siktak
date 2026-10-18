# V → PM

> 작성: 2026-10-18 · V1 · 지금 하는 블레이버스 태스크: **없음 (7.1 완료)**

## 결과 요약
- 7.1 완료·main 머지·push (`3f205bb`). 체크인(T2)·행사 종료 작업 EVENT_END(노쇼 후보·예외 처리함·만남 이력)·피드백 확장(재참여·추천, 체크인자만)·사람별 선호(비공개)·데모 버튼 3종·화면
- 테스트: 전체 `./gradlew test` 통과(DB 포함), 7.1 통합 테스트(시드 → J1 → 체크인 → J5 → 피드백·AVOID → 2회차 → J6)에서 AVOID 쌍이 S4 다른 테이블
- **배포(Render) 직접 확인 완료** (curl+토큰): 시드 초기화 → J1 → 체크인 시간 전 409 → J4 후 user01 체크인(재호출 멱등) → 체크인 일괄(18명, user13 제외) → J5 → 참석 20명 중 19명 ATTENDED·user13 노쇼 후보, 예외 처리함에 VENUE_UNAVAILABLE + NO_SHOW_CANDIDATE → user13 피드백 409 → user01 피드백 201·동석자 4명 이름만 조회·AVOID 선택 → 피드백 일괄 18명 → 2회차 투입 15명 → J6 → user01과 AVOID 대상(준호)이 S4 서로 다른 테이블
- 배포 DB는 확인 후 시드 초기화해 둠

## PM 확인 필요
1. `exception_cases.case_type`에 `NO_SHOW_CANDIDATE` 추가 (demo 12단계 "예외 처리함에 노쇼 후보" 충족용, erd.md 반영)
2. 회차 상태 `DONE`을 "EVENT_END 처리 완료" 표시로 사용 (별도 컬럼 없이 멱등 보장)
3. Flyway `out-of-order: true` 추가 — V6(V1)이 V2의 V7 뒤에 들어오기 때문. 2.2 V2가 지적한 V4/V5 순서 문제도 같이 해소
4. V2 영역 최소 수정: `EngineMatchingService`·`MatchingService`에 확정 이벤트 발행 한 줄씩 (지시서 허용 범위)
5. `RefundConcurrencyIntegrationTest`가 3.2(프로필 필수) 이후 깨져 있어 프로필 삽입으로 고침 (V2 테스트)
6. **DB 통합 테스트가 느림**(Neon 왕복): 클래스당 5~10분, 전체 약 1시간. V1·V2가 동시에 돌리면 TRUNCATE 락·시드 삭제로 서로 실패시킴 → 전체 테스트는 한 세션씩

## 사용자 작업 요청
- 없음. 다음 지시는 `/clear` 후 받겠습니다
