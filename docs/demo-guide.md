# 시연 진행표 (demo-guide.md)

> 작성: 2026-10-18 · 기준: `docs/demo-scenario.md` 12단계 · 사용: 시연 진행자(손에 들고 따라가는 표)
> 시나리오·시드·예상 결과의 정본은 `demo-scenario.md`. 이 문서는 **무엇을 누르고, 무엇이 보여야 하고, 어떤 PRD 항목인지**만 정리한다.

## 접속 정보

| 대상 | 주소 | 계정 |
|---|---|---|
| 참가자 앱 (모바일 화면 폭) | https://siktak-rho.vercel.app/app | `demo01` / `siktak1234` |
| 운영자 웹 | https://siktak-rho.vercel.app/admin | `admin` / `siktak-admin!` |
| 데모 도구 | https://siktak-rho.vercel.app/admin/demo | admin |
| API 문서 | https://siktak.onrender.com/swagger-ui.html | — |

참가자 앱과 운영자 웹은 브라우저 창 두 개(또는 시크릿 창)로 나란히 띄운다. 로그인은 `/login`.

---

## 시작 전 체크 (시연 10분 전)

1. **Render 깨우기**: `https://siktak.onrender.com/api/v1/health` 를 열어 `{"status":"UP"}` 확인. Free 인스턴스라 첫 응답이 최대 1분 걸린다.
2. **시드 초기화**: admin 로그인 → `/admin/demo` → **시드 초기화**. 데이터와 데모 시계가 처음 상태(T0 = 지금)로 돌아간다. 20~40초 걸리며 끝나면 화면에 완료 상태가 뜬다.
   - 초기화 후 `/admin/slots`에 S1~S5 회차 5개가 모두 `OPEN`이어야 한다. 이미 `MATCHED`가 있으면 초기화가 안 된 것이다.
3. **데모 시계 확인**: `/admin/demo` 상단의 시계 이동량이 `0일 0시간 0분`인지 확인한다.
4. **참가자 상태 확인**: `demo01` 로그인 → 홈의 "내 신청"이 비어 있고 "모집 중인 식탁"에 S1~S5가 보여야 한다.
5. 화면 두 개 배치: 왼쪽 참가자 앱(모바일 폭), 오른쪽 운영자 웹.

> 시계는 앞으로만 간다. 같은 단계를 다시 보여줘야 하면 **시드 초기화**부터 다시 한다.

---

## 12단계 진행표

화면 경로의 `/app`은 참가자 앱, `/admin`은 운영자 웹이다. 운영자 화면 경로: 대시보드 `/admin`, 매칭 모니터 `/admin/matching`, 테이블 상세 `/admin/tables/{id}`, 예외 처리함 `/admin/exceptions`.

| # | 하는 일 | 누를 버튼 / 호출 | 화면에서 보여야 할 것 | PRD |
|---|---|---|---|---|
| 1 | 참가자가 회차를 고르고 신청·결제 | `demo01` 로그인 → `/app` "모집 중인 식탁"에서 S1(성수) 선택 → 신청 제출 → 이용권 1회권 → 모의 결제 승인 | 신청 상태 "결제 완료 · 매칭 대기", 매칭 예정 시각, 환불 규정(2일 전 이후 제한) 표시. 운영자 `/admin/applicants`에 시드 신청자 + demo01 | APP-01~04, PAY-01·02, STA-01·02 |
| 2 | 매칭 마감 시각에 자동 매칭 | `/admin/demo` → **⏩ J1 1회차 매칭 마감** | 운영자가 매칭 시작 버튼을 누르지 않았는데 MatchRun 1건 생성 `/admin/matching` 매칭 모니터에서 확인 (실행 방식 "자동 실행") | MAT-01 |
| 3 | 필수 조건·우선순위로 4~6인 테이블 생성 | — | S1 3개·S2 1개 테이블. 연령 범위·성비·MBTI·공통 관심사·점수 표시, user05/user06 서로 다른 테이블 `/admin/matching`에서 실행을 열고 `/admin/tables/{id}`에서 테이블별 점수·구성 확인 | MAT-02~07, MAT-09 |
| 4 | 조건 미충족 해제 후 자동 재배치 | — | 배치 로그: user15 S2 `AGE_GAP` → S1 재배치, user14 대기 복귀(`DEFERRED`), user16 미결제 제외 `/admin/matching` 실행 상세의 배치 로그 | STA-03, MAT-08, MAT-12 |
| 5 | 운영자 승인 없이 자동 확정 | — | 전 테이블 `CONFIRMED`, `/admin` 대시보드의 운영자 개입률 0% | MAT-10 |
| 6 | 식당 자동 배정 · 단체방 생성 | — | 테이블별 식당·예약명·단체방. S1 테이블 1개는 `VENUE_UNAVAILABLE` 예외 → 예외 처리함 `/admin/exceptions` → 운영자가 `/admin/venues`에서 성수 그린키친의 S1 수용 테이블 수를 올리고 재배정 | INF-02·04, MAT-13 |
| 7 | 참가자가 확정 결과·식당·안내 확인 | `demo01` → `/app/notifications` 알림 → 신청 상세 `/app/applications/{id}` | "테이블이 확정됐어요", 식당 카드·집합 시간·예약명, 단체방의 큐레이션·취소 정책 공지, 다른 참가자 상세·점수는 비노출 | INF-01·03·05·06, MAT-12 |
| 8 | 미매칭자 대체 일정 · 이동·환불 | `/admin/demo` → **⏩ J2 S3 매칭 마감** → `user14` 로그인(신청 상세 "이번 일정은 매칭되지 않았어요") → 추천 일정 **S5 강남으로 이동** / `user23` → **환불** / `user24`는 응답하지 않음 | user14 새 신청 `WAITING`(결제 유지·추가 결제 없음), user23 `REFUNDED`(운영자 승인 없이), user24 `ALTERNATIVE_OFFERED` 유지. 운영자 `/admin/applicants`에서 상태 확인 | MAT-11, STA-04~06, PAY-03 |
| 9 | 리마인드 · 대화 콘텐츠 자동 게시 | `/admin/demo` → **⏩ J3 행사 1일 전** → **⏩ J4 행사 시작** | `/app/notifications` 리마인드(시간·장소·예약명·취소 정책), 단체방에 대화 주제 3개 + 아이스브레이킹 게시 | INF-07·08, CON-01~03 |
| 10 | 체크인 · 만족도 · 사람별 선호 | `demo01` 신청 상세에서 **체크인** → `/admin/demo` **체크인 일괄** → **⏩ J5 행사 종료** → `demo01` 피드백 입력(+같은 테이블 1명 "다시 만나고 싶지 않음") → **피드백 일괄** | user13 노쇼 후보 표시(운영자는 후보만 확인), 피드백 저장, 선호 선택은 상대에게 비노출. `/admin/feedback`에 만족도 요약 | FDB-01~04 |
| 11 | 다음 매칭에 피드백·이전 만남 반영 | `/admin/demo` **2회차 신청 투입** → (demo01은 S1 참석자라 이 투입에 자동 포함된다. 직접 신청하면 중복으로 거절된다) → **⏩ J6 2회차 매칭 마감** | S4: "피하고 싶은 사람" 쌍이 다른 테이블, 1회차 동석자 분산 `/admin/matching` 실행 상세·`/admin/tables/{id}`. user14가 S5에서 확정, user24 자동 보관(크레딧). 참가자 `/app/history`의 참가 이력·다음 식탁 추천 | MAT-05·06, RET-01·02 |
| 12 | 정상 흐름은 자동, 예외만 운영자에게 | — | 운영 대시보드의 자동 확정률·운영자 개입률, 예외 처리함에는 식당 부족(처리 완료)과 노쇼 후보만 존재 `/admin` 대시보드와 `/admin/exceptions` | MAT-13 |

보조 화면: `/admin/slots`(회차), `/admin/venues`(식당·회차별 수용), `/admin/settings`(매칭 가중치), `/admin/applicants`(신청자·CSV), `/admin/feedback`(피드백 요약), `/admin/reports`(신고).

---

## 문제가 생겼을 때

| 증상 | 조치 |
|---|---|
| 화면이 안 열리거나 로그인이 오래 걸림 | Render가 자는 중. `/api/v1/health` 를 열고 1분 기다린다. |
| 단계가 꼬임·중복 클릭·회차가 이미 `MATCHED` | `/admin/demo` **시드 초기화** 후 처음부터. |
| 점프 버튼을 두 번 눌렀다 | 매칭·테이블·단체방·알림은 멱등이라 결과가 중복되지 않는다. 그대로 진행해도 된다. |
| 실패한 자동 처리 (환불·알림·식당) | 자동 재시도(1분 간격 3회) 후에도 실패한 건만 예외 처리함에 올라온다. 운영자가 거기서 재처리한다. |
| 새 매칭 엔진이 불안정 | 백엔드 환경변수 `SIKTAK_MATCHING_ENGINE=simple`(2.3 순차 매칭)로 바꾸고 재배포. 연령 조건 등은 적용되지 않으므로 마지막 수단. |
| 결제가 불안정 | `SIKTAK_PAYMENT_PROVIDER=mock` (현재 기본값, 이미 모의 결제). |
| 알림 발송 실패를 일부러 보여주고 싶다 | `SIKTAK_NOTIFICATION_MOCK_FAIL=true` → 재시도 후 예외 처리함 전환(INF-08). 시연 후 `false`로 복구. |
| 환불 실패를 일부러 보여주고 싶다 | `SIKTAK_PAYMENT_MOCK_FAIL_CANCEL=true` → `REFUND_FAILED` + 예외. 시연 후 `false`로 복구. |
| 시계를 잘못 앞당김 | 시계는 되돌릴 수 없다. 시드 초기화로 복귀. |

환경변수 변경은 Render 대시보드에서 하며 재배포(재시작)가 필요하다. 상세: `docs/integrations.md`.
