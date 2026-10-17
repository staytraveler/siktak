# V → PM

> 작성: 2026-10-17 16:13 · V클로드 · **2.4 완료** (진행 중 블레이버스 태스크 없음 — 다음 지시 대기)

## 결과 요약
- **2.4 완료** — 상세는 `docs/tasks/2.4-demo-clock-seed.md` 완료 보고
  - 데모 시계(offset 메모리, 바꿀 때만 저장), DB 폴링 없는 작업 실행기(`MATCH_RUN`), demo-v1 시드(TRUNCATE→재투입, 빈 DB 자동 투입), `/admin/demo`(J1~J6·+1시간·+1일·시드 초기화)
  - **배포 환경에서 확인**: Render 시드 초기화 21초 → J1 → S1 테이블 5·5·4, S2 6 자동 생성. Vercel `/admin/demo` 200
  - catch-up·틱 무DB는 테스트로 증명. 전체 테스트 115건 통과
- 2.4 0번: V2 4.1(`9ad26fe`)·4.2(`89a5429`) main 머지 완료
- 2.2 완료 보고 마무리: URL 반영, 배포 curl 확인 (Actions 결과만 사용자 확인 대기)

## 블레이버스 체크할 태스크
- 2.4: [x] 가상 시계(Clock) 주입 구조 / [x] 어드민 시간 앞당기기 기능 / [x] 시드 데이터 투입·초기화 스크립트

## V2 전달
- `MatchingService.runForSlot`이 `operatorId == null`(스케줄러 자동 실행)을 받도록 최소 수정함 (`3461447`, 사용자 승인). 4.4에서 시그니처를 바꾸면 `slot.application.MatchRunJobHandler`도 같이 맞춰야 하니 알려줘
- **시드 확장 지점 준비 완료**: `com.siktak.common.seed.SeedContributor`(order·contribute) 빈을 만들면 demo-v1 초기화 때 같은 트랜잭션에서 호출된다. `SeedContext.slot("S1")`, `.user("user05")`, `.region("성수")`로 ID 조회. 식당·수용량·콘텐츠는 여기에 붙이면 된다
- 차단 관계(user05→user06, source REPORT)는 `user_blocks`가 있으면 V1 시드가 넣는다 (V3 머지 후 자동)
- 프로필 `price_range`는 `UNDER_20K`/`20K_40K`/`OVER_40K`를 순서대로 분배해 넣었다
- 새 시간 기반 작업(리마인드·콘텐츠 게시 등)은 `common.schedule.ScheduledJobHandler` 빈 + 생성 시 `ScheduledJobRegistry.schedule(...)` 호출로 붙인다
- 참고: **ShedLock이 바깥 트랜잭션을 커밋하던 버그**를 고쳤다(`SchedulingConfig`에 JPA 트랜잭션 매니저 지정). V2 테스트에서도 락을 쓰면 이 수정이 필요하니 main 머지 권장

## PM 확인 필요
1. **Render 인스턴스**: 실제는 Free + cron-job.org 10분 핑. CLAUDE.md 4장은 "Starter 이상(Free 금지)". 문구 갱신 여부
2. 배포 DB는 지금 데모 시각 J1 이후 상태. V2 4.4 머지 후 시드 초기화를 다시 해야 엔진 결과가 보인다
3. 시드 초기화가 느리다 (로컬 35초, Render 21초 — 신청 27건을 실제 서비스 흐름으로 생성). 시연 중 누를 계획이면 배치 쓰기로 줄일지
4. 가짜 매칭이라 지금은 연령 조건이 적용되지 않는다 (user14가 S1 테이블). 4.4로 해결 예정

## 사용자 작업 요청
- GitHub Actions 결과 확인 (2.2 마지막 체크) — 이번 push(`a158550` 이후)도 포함
