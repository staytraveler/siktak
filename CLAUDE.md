# CLAUDE.md — siktak (우연한 식탁)

소셜다이닝 서비스 '우연한 식탁'의 운영 자동화 MVP.
참가자 신청 → 결제 → 자동 매칭 → 자동 재배치 → 자동 확정 → 식당·단체방 안내 → 참석 → 피드백 → 다음 매칭까지
**정상 흐름은 운영자 개입 없이 끝나고, 운영자는 자동 해결되지 않은 예외만 처리한다.**

- 해커톤 제출: 2026-10-21 (기능 완성 목표 10-18, 기능 동결 10-20 밤)
- 이후 2026-12 ~ 2027-01 실서비스 전환 예정 → MVP라도 확장 가능한 구조로 만든다

---

## 1. 문서 읽기 규칙 (사용량 절약)

**전부 읽지 않는다.** 이 파일 + 지시서 + 지시서 "참조"에 적힌 문서·장만 읽는다. PRD 전문(`docs/prd.md`)은 필요한 PRD ID·장만 찾아 본다. `progress.md`는 최근 항목만.

| 문서 | 내용 |
|---|---|
| `docs/prd.md` | PRD v0.2 원문. **모든 판단의 최우선 기준** (필요한 부분만) |
| `docs/prd-analysis.md` | PRD·심사 기준 분석 |
| `docs/domain/states.md` | 상태 전이표 |
| `docs/domain/matching.md` | 매칭 알고리즘 명세 |
| `docs/erd.md` | 데이터 모델 |
| `docs/decisions.md` | 결정 로그 |
| `docs/progress.md` | 진행 기록 |

---|---|
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
- Backend: **Render** (Docker, **Free 인스턴스 + cron-job.org 10분 핑**으로 슬립 방지, 리전 Singapore. 재시작 대비 기동 시 catch-up)
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
- 스케줄러·워커는 DB를 주기적으로 폴링하지 않는다 (decisions.md 참고).

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

## 11. 세션 규칙 (PM클로드 ↔ V클로드)

### 역할
이 프로젝트는 두 Claude 세션과 사용자가 함께 진행한다. 두 세션은 서로 직접 대화할 수 없다.

| 이름 | 누구 | 하는 일 |
|---|---|---|
| **사용자** | 개발자 본인 | 두 세션 사이 메시지 전달, 계정·대시보드 작업(Neon·Render·Vercel·토스 등), 최종 판단 |
| **PM클로드** | 기획 세션 (Claude 앱) | 작업지시서 작성, V클로드 결과 검토, 전체 진행도 관리 |
| **V클로드** | 이 세션 (VS Code Claude Code) | 지시서대로 구현·테스트·커밋, 완료 보고 작성 |

### 소통 방식 (메시지 파일)
두 세션은 아래 두 파일로 대화한다. 사용자는 내용을 옮기지 않고 **신호만** 전달한다.

| 파일 | 쓰는 쪽 | 내용 |
|---|---|---|
| `docs/handoff/pm-to-v.md` | PM클로드 | 다음에 할 일, 검토 결과, 결정 사항 |
| `docs/handoff/v-to-pm.md` | V클로드 | 작업 결과 요약, PM 확인 필요 사항, 사용자 작업 요청 |

- 사용자가 **"PM 확인"**이라고 하면: `docs/handoff/pm-to-v.md`를 읽고 그대로 진행한다.
- V클로드는 작업이 끝나거나 PM 판단이 필요하면 `docs/handoff/v-to-pm.md`를 **최신 내용으로 덮어쓰고**, 사용자에게 "PM에게 전달해주세요" 한 줄로 알린다.
- 각 파일 맨 위에는 작성 시각을 적는다. 이전 내용은 남기지 않는다 (이력은 git과 `docs/progress.md`에 남는다).
- 작업 단위 지시는 `docs/tasks/`의 **작업지시서**, 결과는 지시서 맨 아래 **완료 보고**에도 남긴다.
- V → PM: 지시서 맨 아래 **완료 보고**와 `docs/progress.md`. PM클로드는 이 폴더를 직접 읽을 수 있으므로, 사용자에게는 짧게 요약만 하면 된다.
- PM클로드는 `docs/` 아래 문서만 수정하고 코드는 건드리지 않는다. V클로드는 `docs/tasks/`의 지시서 본문과 `docs/domain/` 명세를 PM 확인 없이 바꾸지 않는다.
- 판단이 필요한 질문은 V클로드가 혼자 정하지 말고 완료 보고나 응답의 **"PM 확인 필요"**에 적는다. 사용자가 PM클로드에게 전달한다.
- 사용자가 직접 해야 하는 작업(계정 생성, 키 발급, 대시보드 설정)이 나오면 멈추고, 무엇을 해서 어떤 값을 달라는지 정확히 요청한다.

### 작업지시서
- 기획 세션은 워크 단위 작업지시서를 `docs/tasks/<아젠다>.<워크>-<이름>.md`로 작성한다.
- 사용자는 `/task <번호>`로 실행을 요청한다 (`.claude/commands/task.md`).
- 규칙은 `docs/tasks/README.md`를 따른다. 지시서의 "사용자가 직접 할 일"은 대신하지 않는다.
- 작업이 끝나면 지시서의 "완료 보고"를 채운다.

### 커밋·push 권한 (사용자 위임)
- 사용자는 이 프로젝트의 커밋·push 판단을 PM클로드에게 위임했다. **지시서·`pm-to-v*.md`에 있는 작업은 커밋·push까지 승인된 것으로 본다.** 개인 전역 규칙("사용자가 말할 때만 커밋")보다 이 항목이 우선한다. 사용자에게 커밋 승인을 묻지 않는다.
- 여전히 금지: `main` 강제 push, 적용된 마이그레이션 수정. `main` 머지는 지시서에 적힌 경우만.

### 문서 위치 (worktree 주의)
- `docs/handoff/`, `docs/tasks/`의 보고·완료 보고·명세 수정은 **항상 원래 폴더 `C:\Users\Admin\Desktop\siktak\docs\`의 파일에** 쓴다. worktree(`siktak-matching`) 안의 docs 사본에 쓰면 PM이 보지 못한다.

### 마이그레이션 번호
- 현재 최대 V5. 다음부터 **V1은 짝수(V6, V8, …), V2는 홀수(V7, V9, …)**. V4는 쓰지 않는다.

### 세션 운영 (사용량 절약)
- **지시서 1개 = 대화 1개.** 지시서를 끝내고 `v-to-pm.md`(또는 `v2-to-pm.md`)를 쓴 뒤 사용자에게 "PM에게 전달해주세요. 다음 지시는 `/clear` 후 받겠습니다"라고 안내한다.
- 블레이버스 태스크 하나가 끝날 때마다 커밋한다.
- 같은 오류로 3번 넘게 재시도하지 않는다. 막히면 멈추고 "PM 확인 필요"에 적는다.
- 테스트는 바꾼 영역만 돌리고, 전체 테스트(특히 DB 테스트)는 커밋 직전 1회.
- 큰 파일·로그를 통째로 출력하지 않는다.
- **이 CLAUDE.md의 PM 작성 부분(1장, 11장)을 커밋 과정에서 되돌리지 않는다.** 충돌 시 PM 쪽 내용을 살린다.

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
