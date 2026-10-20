# 우연한 식탁 — 제출 요약 (submission.md)

> 작성: 2026-10-20 · 심사위원·제출 폼용 한 장 요약. 상세 시연은 [demo-guide.md](demo-guide.md), 기능 명세는 [prd.md](prd.md).

## 1. 서비스

처음 보는 4~6명을 성향이 맞는 한 테이블로 연결하는 소셜다이닝 '우연한 식탁'의 **운영 자동화 MVP**.

> **정상 흐름은 자동, 운영자는 예외만.**
> 신청 → 결제 → 자동 매칭 → 자동 재배치 → 식당 배정 → 자동 확정·단체방 → 리마인드 → 체크인 → 피드백 → 다음 매칭까지 운영자 개입 없이 흐르고, 자동으로 풀리지 않은 건만 예외 처리함에 올라온다.

## 2. 링크·테스트 계정

| 대상 | 주소 |
|---|---|
| 참가자 앱 (모바일 우선) | https://siktak-rho.vercel.app/app |
| 운영자 웹 | https://siktak-rho.vercel.app/admin |
| **시연 가이드 (12단계 안내)** | https://siktak-rho.vercel.app/guide |
| API 문서 (Swagger) | https://siktak.onrender.com/swagger-ui.html |
| GitHub | 비공개 저장소 — 요청 시 초대 |

| 구분 | ID | 비밀번호 | 용도 |
|---|---|---|---|
| 운영자 | `admin` | `siktak-admin!` | 운영자 웹, 데모 도구(`/admin/demo`) |
| 시연 참가자 | `demo01` | `siktak1234` | 프로필 입력 완료·신청 전. 신청·모의 결제·체크인·피드백 |
| 일반 참가자 | `user01` ~ `user29` | `siktak1234` | 시드 참가자 ([demo-scenario.md](demo-scenario.md) 3장) |

**테스트용 시간 (헤더 가운데 점선 칩)**: 눌러서 날짜·시간을 앞으로 옮기면 서버의 시간이 바뀌고, 그 시각까지 도래한 자동 작업(매칭·확정·안내·리마인드·콘텐츠 게시)이 실행된다. **시간은 앞으로만 갈 수 있고, 서버 전체가 공유한다(모든 사용자에게 적용).** 처음으로 돌리려면 같은 창의 "실제 현재 시각으로 초기화"(시간과 데이터가 시드 상태로 복원)를 누른다.

테스트 전용 계정이며 결제는 모의(Mock). 첫 접속 시 백엔드(Render Free)가 깨어나느라 최대 1분 걸릴 수 있다 — 먼저 https://siktak.onrender.com/api/v1/health 를 열어 두면 된다.

## 3. PRD 기능 ID ↔ 구현 매핑 (P0)

✅ 구현 · △ 부분/모의 · ✂️ 제외. 참가자 화면은 `/app` 아래, 운영자 화면은 `/admin` 아래. API는 `/api/v1` 아래.

### 계정·신청·결제

| ID | 구현 | 화면 | API | 비고 |
|---|---|---|---|---|
| ACC-01 | △ | `/login` | `POST /auth/login`, `GET /auth/me` | 테스트 계정 로그인(PRD가 허용). 휴대전화·소셜 로그인은 미구현 |
| ACC-02 | ✅ | `/app/profile` | `GET·PUT /me/profile` | |
| ACC-03 | ✅ | `/app/profile` | 〃 | MBTI·관심사·본인 성향·원하는 상대 성향 복수 선택 |
| ACC-04 | ✅ | `/app/profile` | 〃 | 가격대·못 먹는 음식·알레르기 |
| ACC-05 | ✅ | `/app/profile` | 〃 | 동의 없이는 신청 불가 |
| APP-01 | ✅ | `/app`, `/app/slots` | `GET /slots` | 날짜·지역·마감 표시 |
| APP-02 | ✅ | `/app/slots` | `POST /applications` | 날짜·지역 복수 후보 |
| APP-03 | ✅ | `/app/slots` | 〃 | 필수 항목 검증 |
| APP-04 | △ | `/app/slots` | 〃 | 1회권 선택은 구현, 구독 이용권은 미구현 (결제 연동 범위 축소) |
| PAY-01 | △ | `/app/applications/:id` | `POST /applications/{id}/payments/mock-approve` | 결제 상태 6종 표시·전이는 구현, **결제 자체는 Mock** |
| PAY-02 | ✅ | 결제 전·신청 완료 화면 | — | 2일 전 이후 환불 제한, 당일 취소·노쇼 정책 |
| PAY-03 | △ | `/app/applications/:id` | `POST /me/applications/{id}/resolution` | 환불 선택 시 운영자 승인 없이 자동 처리. **환불 상태 전환은 모의** (PRD가 허용) |

### 상태·매칭

| ID | 구현 | 화면 | API | 비고 |
|---|---|---|---|---|
| STA-01 | ✅ | `/app`, `/app/applications/:id` | `GET /me/applications` | 신청·결제·매칭·참석 상태 별도 컬럼 |
| STA-02 | ✅ | `/app/applications/:id` | 〃 | 매칭 발표 예정 시각 |
| STA-03 | ✅ | 〃 | `GET /me/applications/{id}/match-explanation` | 같은 날짜·지역 범위 안에서만 재배치 |
| STA-04 | ✅ | 〃 | `…/resolution` | 실패 사유 + 대체 일정 추천 |
| STA-05 | ✅ | 〃 | 〃 | 이동 / 이용권 보관 / 환불 |
| STA-06 | ✅ | 〃 | 〃 | 결제 유지한 채 새 회차 매칭 대기로 전환 |
| MAT-01 | ✅ | `/admin/matching` | (스케줄러), `POST /admin/demo/jump/{code}` | 마감 시각 도래 시 `@Scheduled` + ShedLock으로 자동 실행. 시연은 데모 시계 점프 |
| MAT-02·03 | ✅ | `/admin/tables/:id` | `GET /admin/tables/{id}` | 4~6명·날짜·지역·나이 차 8살 이내 (완화 없음) |
| MAT-04 | ✅ | 〃 | 〃 | 연령대→성비→MBTI→관심사→원하는 성향 점수화 |
| MAT-05 | ✅ | — | `POST /me/blocks`, `POST /me/reports` | 차단·신고·비선호 관계 같은 테이블 배제 |
| MAT-06 | ✅ | — | — | 이전 만남 이력 반영 (새로운 사람 우선) |
| MAT-07 | ✅ | — | — | 확정 전 필수 조건 재검증 (순수 함수 엔진 + 테스트) |
| MAT-08 | ✅ | `/admin/matching` | `GET /admin/match-runs/{id}` | 재배치 단계별 배치 로그 |
| MAT-09 | ✅ | 〃 | 〃 | 7인 이상 분리·미완성 테이블 병합 |
| MAT-10 | ✅ | `/admin/tables/:id` | — | 운영자 승인 없이 자동 확정 |
| MAT-11 | ✅ | `/app/applications/:id` | — | 실패 시 `ALTERNATIVE_OFFERED` |
| MAT-12 | ✅ | `/app/applications/:id/table`, `/admin/matching` | `…/match-explanation` | 다른 참가자의 상세 점수·정보는 노출하지 않음 |
| MAT-13 | ✅ | `/admin/exceptions` | `GET /admin/exceptions`, `POST /admin/exceptions/{id}/…` | 식당 부족·결제 오류·노쇼 후보·신고만 노출 |

### 안내·콘텐츠·사후

| ID | 구현 | 화면 | API | 비고 |
|---|---|---|---|---|
| INF-01 | △ | `/app/notifications` | `GET /me/notifications` | **앱 내 알림 구현, 모바일 푸시는 미구현** (`NotificationSender` 포트로 교체 가능) |
| INF-02 | ✅ | `/admin/venues`, `/admin/exceptions` | `/admin/venues`, `…/assign-venue` | 지역·시간·인원에 맞는 식당 자동 배정, 없을 때만 예외 |
| INF-03 | ✅ | `/app/applications/:id/table` | `GET /me/applications/{id}/table` | 식당명·주소·지도 링크·예약명 |
| INF-04 | ✅ | 〃 | `GET·POST /tables/{id}/messages` | 확정과 동시에 단체방 생성·전원 초대 (멱등) |
| INF-05 | ✅ | 〃 | — | 시간·장소·큐레이션·취소 정책 시스템 메시지 |
| INF-06 | ✅ | 〃 | — | 동석자는 이름만 노출 |
| INF-07 | ✅ | `/app/notifications` | — | 행사 1일 전 알림 + 단체방 (`REMINDER_D1`) |
| INF-08 | ✅ | `/admin/exceptions` | `…/resend-notification` | 자동 재시도 후 반복 실패만 예외 |
| CON-01~03 | ✅ | `/app/applications/:id/table` | `GET /me/applications/{id}/contents` | 대화 주제 3개 이상·아이스브레이커, 행사 시작 시각에 자동 게시 (`CONTENT_POST`). 템플릿 기반 |
| FDB-01 | ✅ | `/app/applications/:id` | `POST /me/applications/{id}/check-in` | 종료 시각까지 미체크인 → 노쇼 후보, 운영자는 후보만 확인 |
| FDB-02·03 | ✅ | 〃 | `POST /feedbacks` | 분위기·대화·식당 + 재참여·추천 의향 |
| FDB-04 | ✅ | 〃 | `PUT /me/applications/{id}/person-preferences` | 비공개, 다음 매칭에 반영 |
| SAF-01 | ✅ | 〃 | `POST /me/reports`, `POST /me/blocks` | 신고 내용은 상대에게 비공개, 즉시 분리 |
| SAF-02 | ✅ | `/admin/reports` | `POST /admin/reports/{id}/resolve` | 경고·참가 제한·영구 제한 |
| RET-01 | ✅ | `/app/history` | `GET /me/history` | |
| RET-02 | △ | `/app/history` | `GET /me/recommendations` | 구현은 했으나 시연 시드에서는 추천이 비어 보일 수 있음 (10.2 리허설 확인) |

P1 중에는 MAT-15(가중치·최소 점수 설정, `/admin/settings`)와 운영자 관리 화면(회차·식당·신청자 CSV·피드백 요약)을 구현했다. 그 외 P1·P2(PAY-04 실결제, STA-07 대기자 전환, MAT-14·16, CON-04, RET-03 등)는 범위 밖.

## 4. 자동화 지표 (시연 시나리오 기준)

정의는 대시보드(`/admin`) 구현 기준 ([decisions.md](decisions.md) 8.1 지표 정의). 값은 시드 초기화 → J1 직후 배포 환경 측정과 10.2 리허설 결과.

| 지표 | 정의 | 시연 값 |
|---|---|---|
| 자동 확정률 | 해산되지 않은 테이블 중 예외가 한 번도 붙지 않은 테이블 비율 | 3/4 (75%) — 식당 부족 예외 1건이 붙은 테이블 1개 제외 |
| 자동 재배치 성공률 | 재배치 대상 신청 중 재배치로 배치된 비율 | 1/2 (50%) — user15 재배치 성공, user14는 같은 날 배치 불가로 대체 일정 안내 |
| 자동 환불 완료율 | 환불을 선택한 신청 중 환불 실패 예외 없이 환불 완료된 비율 | 1/1 (user23, 운영자 승인 없음) |
| 운영자 개입률 | 운영자가 처리한 예외 ÷ 제출 신청 | 12단계 완주 시 처리한 예외는 식당 수용량 부족 1건뿐 (제출 신청 대비 약 4%). 자동 확정 시점에는 0 |

> 지표 정의(특히 재배치 성공률의 분모)는 PRD 3장의 해석이며 PM 확인 대기 항목이다.

## 5. 확장성 (실서비스 전환 대비)

- **포트/어댑터 3종**: `PaymentGateway`(Mock / Toss), `NotificationSender`(InApp / 푸시·알림톡), `ContentGenerator`(Template / LLM). 구현은 설정값(`siktak.payment.provider=mock|toss` 등)으로 전환하고, 불안정하면 설정 하나로 Mock으로 되돌린다. 전환 방법은 [integrations.md](integrations.md).
- **매칭 엔진은 순수 함수**: DB·시간·외부 의존 없이 입력→결과로 테스트. 실행마다 `MatchRun`(시각·대상·결과·알고리즘 버전)을 기록하고, 가중치는 운영자 화면에서 조정(MAT-15).
- **멱등성**: 매칭 실행(run_key)·테이블·단체방·결제·환불·알림 모두 두 번 실행해도 결과 동일. 외부 호출은 멱등 키와 재시도 횟수를 저장.
- **상태·감사**: 신청·결제·매칭·참석 상태를 각각 별도 컬럼으로 두고, 전이는 도메인 서비스 한 곳에서만 하며 모든 변경을 이력에 남긴다.
- **DemoClock**: `Clock` 빈 주입 + 데모용 시간 이동. 실서비스에서는 오프셋 0으로 실제 시각과 같다.
- **Neon 절전 설계**: 스케줄러가 DB를 주기 폴링하지 않고 메모리의 다음 작업 시각만 본다(도래 시에만 DB 접근). 헬스체크는 DB를 깨우지 않는 liveness와 분리.
- **Render Free + 깨우기**: 10분 주기 핑으로 슬립을 막고, 재시작되어도 기동 시 놓친 작업을 따라잡는다(catch-up, ShedLock으로 중복 실행 방지).
- **스키마**: Flyway 마이그레이션만 사용, OpenAPI에서 프론트 타입 생성.

## 6. 알려진 제약

- **Render Free 첫 요청 지연**: 슬립에서 깨어나는 첫 요청은 최대 1분. 시연 전 health 호출 필요.
- **결제·환불은 Mock**: 토스페이먼츠 연동은 포트 구조만 두고 제외(PRD가 모의 허용). 구독 이용권도 미구현.
- **알림은 앱 내 알림 중심**: 모바일 푸시·알림톡 미구현.
- **로그인은 테스트 계정 기반**: 휴대전화 인증·소셜 로그인 미구현.
- **DB 통합 테스트는 CI 의존**: 외부 Neon 왕복이 느려(클래스당 5~10분) 로컬 전체 실행을 생략했다. 비DB 테스트는 로컬 전체 통과, DB 포함 결과는 GitHub Actions(Postgres 서비스 컨테이너)로 확인해야 한다.
- **브라우저 리허설 범위**: 12단계는 배포 사이트에서 API 수준으로 검증했고, 화면 렌더링은 시연 전 사람이 한 번 더 확인한다.
- 일부 알림 본문이 식당 재배정 전의 "장소: 확정 후 안내" 문구로 남을 수 있다(단체방과 테이블 화면에는 갱신됨).

## 7. 시연 진행표

[docs/demo-guide.md](demo-guide.md) — 시작 전 체크, 12단계 진행표, 예상 화면, PRD 대응.
