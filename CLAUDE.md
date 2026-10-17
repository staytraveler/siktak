# CLAUDE.md — siktak (우연한 식탁)

소셜다이닝 서비스 '우연한 식탁'의 운영 자동화 MVP.
참가자 신청 → 결제 → 자동 매칭 → 자동 재배치 → 자동 확정 → 식당·단체방 안내 → 참석 → 피드백 → 다음 매칭까지
**정상 흐름은 운영자 개입 없이 끝나고, 운영자는 자동 해결되지 않은 예외만 처리한다.**

- 해커톤 제출: 2026-10-21 (기능 완성 목표 10-18, 기능 동결 10-20 밤)
- 이후 2026-12 ~ 2027-01 실서비스 전환 예정 → MVP라도 확장 가능한 구조로 만든다

---

## 1. 반드시 먼저 읽을 문서

| 문서 | 내용 |
|---|---|
| `docs/prd.md` | PRD v0.2 원문. **모든 판단의 최우선 기준** |
| `docs/prd-analysis.md` | PRD·심사 기준 분석, 미정의 항목 |
| `docs/domain/states.md` | 신청·결제·매칭·참석 상태 전이표 |
| `docs/domain/matching.md` | 매칭 알고리즘 명세 |
| `docs/erd.md` | 데이터 모델 |
| `docs/decisions.md` | 결정 로그 |
| `docs/progress.md` | 세션별 진행 기록 |

---

## 2. 의사결정 규칙

1. **PRD가 최우선이다.** 자료 우선순위: `docs/prd.md` → `docs/domain/*` 명세 → 기타.
2. **PRD와 충돌하거나 PRD에 없는 내용은 구현 전에 사용자에게 확인한다.** 추측으로 채우지 않는다.
3. `docs/domain/` 명세와 다르게 구현해야 하면 **먼저 확인**받고, 확정되면 명세와 `docs/decisions.md`를 함께 수정한다.
4. 애매하면 운영 원칙으로 판단한다: "정상 흐름은 자동, 운영자는 예외만".
5. 기능 작업 시작 시 **해당 PRD ID(예: MAT-08)를 명시**한다.
6. 추측으로 작성한 부분은 코드 주석이나 응답에 반드시 표시한다.

---

## 3. 범위 규칙

- **P0 먼저, 끝까지.** PRD 18장 완료 조건(12단계 시나리오)이 돌기 전에는 P1을 시작하지 않는다.
- 화면 수보다 end-to-end 시연 가능 여부가 우선이다.
- 기능 하나는 **API + 화면 + 테스트**까지를 한 단위로 완성한다.
- 외부 연동은 전체 하루 이내. 한 연동에서 1시간 이상 막히면 Mock으로 확정하고 사용자에게 보고한다.
- 30분 이상 막히면 혼자 끌지 말고 범위 축소안을 먼저 제안한다.

---

## 4. 기술 스택 (변경 시 사용자 확인 필수)

### Backend (`/backend`)
- Java 21, Spring Boot (최신 안정판), Gradle
- Spring Data JPA, Flyway, PostgreSQL
- Spring Security + JWT (Authorization 헤더 방식, 쿠키 사용 안 함)
- `@Scheduled` + ShedLock
- springdoc-openapi (Swagger UI)
- JUnit 5

### Frontend (`/frontend`)
- React + **TypeScript** + Vite
- React Router, TanStack Query, axios, Tailwind CSS
- vite-plugin-pwa (모바일 우선 PWA)
- API 타입은 백엔드 OpenAPI에서 자동 생성 (`src/api/generated/`, 직접 수정 금지)

### Infra
- Frontend: **Vercel**
- Backend: **Render** (Docker, 인스턴스 타입 Starter 이상 — Free 금지, 리전 Singapore)
- DB: **Neon** PostgreSQL (리전 Singapore, 운영/개발 브랜치 분리)
- CI: GitHub Actions (테스트 통과 확인)
- 결제: 토스페이먼츠 테스트 모드

---

## 5. 저장소 구조

```
siktak/
├── backend/
├── frontend/
├── docs/
│   ├── prd.md
│   ├── prd-analysis.md
│   ├── domain/        # states.md, matching.md
│   ├── erd.md
│   ├── decisions.md
│   ├── integrations.md
│   ├── demo-scenario.md
│   └── progress.md
├── .claude/
├── CLAUDE.md
└── README.md
```

### Backend 패키지 (도메인 단위)
`com.siktak.{common, user, profile, slot, application, payment, matching, venue, chat, notification, content, feedback, safety, admin}`

각 도메인 패키지 안에 `api`(controller, dto) / `application`(service) / `domain`(entity, enum, 순수 로직) / `infra`(repository, 외부 연동 구현)를 둔다.

### Frontend 라우트
- `/app/*` 참가자 앱 (모바일 우선)
- `/admin/*` 운영자 웹 (데스크톱 우선)

---

## 6. 도메인 규칙 (절대 원칙)

### 상태
- 신청 `application_status`, 결제 `payment_status`, 매칭 `match_status`, 참석 `attendance_status`는 **각각 별도 컬럼**으로 저장한다.
- 상태값은 PRD 12장 enum **그대로** 사용한다. 임의로 추가·변경하지 않는다.
- 상태 변경은 **도메인 서비스 메서드에서만** 한다. 컨트롤러·리포지토리에서 직접 상태를 바꾸지 않는다.
- 모든 상태 변경은 audit log(시각, 주체, 원인, 이전 값, 새 값)를 남긴다.

### 멱등성
- 매칭 실행, 테이블 생성, 단체방 생성, 결제·환불, 알림 발송은 **두 번 실행돼도 결과가 같아야 한다.**
- 외부 호출에는 멱등 키를 쓰고, 요청·결과·재시도 횟수를 저장한다.

### 예외
- 자동 처리 실패는 로그로 끝내지 않고 `ExceptionCase`로 저장해 운영자 예외 처리함에 노출한다.
- 재시도 가능한 실패(알림·식당·결제)는 자동 재시도 후, 반복 실패만 예외로 전환한다.

### 개인정보
- 매칭 전에는 다른 참가자 정보를 노출하지 않는다. 확정 후에도 제한된 소개만 보여준다.
- 사람별 선호, 차단·신고 내용은 상대에게 절대 노출하지 않는다.
- 다른 참가자의 상세 매칭 점수는 노출하지 않는다.

### 시간
- `now()`를 직접 호출하지 않는다. **`Clock` 빈을 주입**해서 쓴다 (데모용 시간 제어).
- 서버 시간대는 Asia/Seoul. 시각은 `Instant`/`timestamptz`로 저장한다.
- 금액은 원 단위 `long`.

---

## 7. 매칭 엔진 규칙

- 매칭 로직은 **순수 함수**(입력 → 결과)로 분리한다. DB·시간·외부 의존 금지.
- 필수 조건(4~6명, 날짜·지역 일치, 나이 차 8살 이내, 차단·신고·비선호 관계 없음)은 **어떤 경우에도 완화하지 않는다.**
- 선호 조건은 점수로 합산한다. 우선순위와 가중치는 `docs/domain/matching.md`를 따른다.
- 재배치는 PRD 7.4 순서를 따르고, 사용자가 선택한 날짜·지역 범위를 벗어나지 않는다.
- 결과에는 PRD 7.2 표시 항목과 배치·재배치·실패 사유를 함께 담는다.
- 매칭 실행마다 `MatchRun`(실행 시각, 대상, 결과, 알고리즘 버전)을 기록한다.
- **매칭 엔진은 테스트 없이 머지하지 않는다.** 필수 케이스: 7인 후보군, 나이 차 초과, 차단 관계, 4명 미만 고립, 재실행 시 중복 없음.

---

## 8. 외부 연동 규칙

- 모든 외부 연동은 **인터페이스(포트) 뒤에** 둔다. Mock 구현을 항상 함께 만든다.
  - `PaymentGateway` (Mock / Toss)
  - `NotificationSender` (InApp / 이후 Push·알림톡)
  - `ContentGenerator` (Template / 선택: LLM)
- 구현 선택은 설정값으로 전환한다 (예: `siktak.payment.provider=mock|toss`).
- 데모 직전 불안정하면 설정 하나로 Mock 전환이 가능해야 한다.
- 연동 정보와 전환 방법은 `docs/integrations.md`에 기록한다.

---

## 9. API 규칙

- 경로: 참가자 `/api/v1/...`, 운영자 `/api/v1/admin/...`
- 에러 응답 형식 통일: `{ "code": "...", "message": "...", "details": ... }`
- 운영자 API는 권한 검사 필수.
- 모든 API는 OpenAPI 문서에 노출되어야 한다 (프론트 타입 생성의 기준).

---

## 10. 테스트·Git·배포

### 테스트
- 매칭 엔진, 상태 전이, 결제·환불은 테스트 필수.
- 테스트가 깨진 상태로 커밋하지 않는다.

### Git
- `main`은 항상 배포 가능한 상태.
- 작업 브랜치: `feat/…`, `fix/…`, `docs/…`
- 커밋 메시지: `feat(matching): 4명 미만 테이블 재배치` 형식 (type(scope): 한글 설명)
- 하루 최소 1회 main 머지 및 배포 확인.

### 배포·환경
- 시크릿은 절대 커밋하지 않는다. 필요한 환경변수는 `.env.example`에 키만 기록한다.
- 백엔드 CORS는 Vercel 도메인만 허용한다.
- Vercel은 SPA 라우팅 rewrite 설정을 둔다.
- 시드 데이터와 테스트 계정은 스크립트 한 번으로 재생성 가능해야 한다.

### DB
- 스키마 변경은 Flyway 마이그레이션으로만 한다.
- **이미 적용된 마이그레이션 파일은 수정하지 않는다.** 새 버전 파일을 추가한다.

---

## 11. 세션 규칙 (기획 세션과의 동기화)

기획은 별도 세션에서 진행되며, 두 세션이 공유하는 것은 **`docs/` 문서뿐**이다.

### 세션 시작 시
1. 이 파일과 `docs/progress.md` 최근 기록을 읽는다.
2. 작업할 기능의 PRD ID와 관련 `docs/domain/` 명세를 확인한다.
3. 큰 작업(새 도메인, 매칭 로직, 상태 변경)은 계획을 먼저 보여주고 승인받는다.

### 세션 종료 시
1. `docs/progress.md`에 기록한다:
   ```
   ## YYYY-MM-DD HH:MM
   - 완료: (PRD ID 포함)
   - 변경: (명세와 달라진 점)
   - 이슈: (막힌 것, 확인 필요)
   - 다음: 
   ```
2. 설계를 바꿨다면 `docs/decisions.md`에 `[CC]` 태그로 기록한다:
   ```
   - YYYY-MM-DD [CC] 결정 내용 — 이유
   ```
3. 명세 문서도 함께 수정한다.

---

## 12. 금지 사항

- 필수 매칭 조건 완화
- PRD에 없는 상태값 추가
- 사용자 확인 없는 기술 스택·구조 변경
- 시크릿 커밋
- 적용된 마이그레이션 수정
- `main` 강제 푸시
- 테스트 깨진 채로 커밋
- `src/api/generated/` 직접 수정
- 다른 참가자 개인정보·선호·신고 내용 노출
