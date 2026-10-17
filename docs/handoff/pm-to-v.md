# PM → V

> 작성: 2026-10-17 13:40 · PM클로드 (13:35 버전에 '진행 방식'·'병렬 작업' 추가)
> 13:30 버전의 "Neon 상시 가동 감수" 결정은 **취소**. 아래가 최신이다.

## 승인
1. **push 승인.** `git push origin main` 진행해줘. 앞으로 테스트·빌드가 통과한 `main` push는 매번 묻지 않아도 돼. (강제 push는 계속 금지)
2. **2.2 계획 승인**, 단 아래 "DB 절전 원칙"을 반영해서 B2·B5를 조정해줘.

## 진행 방식 변경 (속도)
- 지시서 범위 안의 작업은 **확인 없이 진행**해. 멈추는 경우는 세 가지뿐: ① PRD·명세와 충돌 ② 사용자 계정·대시보드 작업 필요 ③ 되돌리기 어려운 작업(강제 push, 적용된 마이그레이션 수정, 데이터 삭제).
- 계획은 사용자에게 짧게 보여주고 바로 시작해도 된다.

## 병렬 작업 준비 (push 직후 먼저 해줘)
매칭 엔진을 별도 세션(V2)이 병렬로 만든다.
1. `docs/tasks/4.1-matching-engine-core.md`, `docs/handoff/pm-to-v2.md`, `docs/handoff/v2-to-pm.md`, `docs/tasks/README.md` 를 커밋·push.
2. worktree 생성: `git worktree add ../siktak-matching -b feat/matching-engine` (main 기준)
3. 사용자에게 안내: "VS Code 새 창으로 `C:\Users\Admin\Desktop\siktak-matching` 을 열고 Claude Code에 `C:\Users\Admin\Desktop\siktak\docs\handoff\pm-to-v2.md 읽고 진행` 이라고 보내세요."
4. **노트북 부담 줄이기**: `backend/gradle.properties`에 `org.gradle.jvmargs=-Xmx1g` 와 `org.gradle.workers.max=2` 를 넣고 커밋해줘 (worktree 만들기 전에). 세션은 V1·V2 두 개까지만 쓴다.
5. 너는 `com.siktak.matching` 패키지를 건드리지 마. V2 영역이다. `docs/handoff/*v2*` 파일은 V2가 수정하므로 변경이 보이면 그대로 커밋해줘.

## DB 절전 원칙 (사용자 결정)
Render 서버는 상시 가동(유료)하지만, **Neon DB는 실제 작업이 있을 때만 깨운다.** Neon은 5분 무활동 시 절전하고, 깨어나는 데 1초 내외라 서비스에 지장이 없다. 무료 사용량(월 100 CU-hours)을 아끼는 게 목적이다.

1. **헬스체크 분리**
   - `GET /api/v1/health` : DB를 조회하지 않는 liveness. Render Health Check Path는 이것으로 둔다.
   - `GET /api/v1/health/db` : `SELECT 1`로 DB 상태 확인. 프론트 `/app` 배포 확인 화면과 수동 점검용. (네가 대안으로 적은 안과 같음)
2. **스케줄러는 DB를 폴링하지 않는다**
   - 다음 실행 시각(회차 `match_run_at`, 리마인드, 행사 시작·종료 등)은 **메모리에 들고 있는다.** 1분 주기 틱은 메모리 시각만 비교하고, 실제로 도래했을 때만 DB에 접근한다.
   - 메모리 일정은 앱 기동 시 1회 DB에서 적재하고, 회차·예약 알림이 생성·수정될 때 갱신한다. 데모 시계 이동 시에도 즉시 재평가한다.
   - ShedLock은 작업이 실제로 실행될 때만 락을 잡는다 (틱마다 잡지 않음).
   - 알림 발송도 생성 시점에 즉시 시도하고, 실패 재시도는 메모리 일정에 다음 시도 시각을 올리는 방식으로 한다. DB 폴링 워커 금지.
   - 이 구조는 지금 2.2에서는 **원칙만 문서화**하고, 실제 스케줄 작업이 생기는 2.4·4.5·5.x에서 구현한다. 2.2에서 `@Scheduled` 틱을 새로 만들지 마.
3. **커넥션 풀**: Hikari `minimumIdle=0`, `idleTimeout` 짧게(예: 60초), keepalive 비활성. 놀고 있는 커넥션이 DB를 붙잡지 않게.
4. `docs/decisions.md`에 `[기획]`으로 기록: "Neon은 scale-to-zero 유지. 헬스체크 DB 분리, 스케줄러는 메모리 일정 기반으로 DB 폴링 금지, 커넥션 풀 최소 유지 0 — 무료 사용량 절약, Neon 재기동 1초 내외로 지장 없음"
5. `CLAUDE.md` 6장 "시간" 아래에 한 줄 추가해줘: "스케줄러·워커는 DB를 주기적으로 폴링하지 않는다 (decisions.md 참고)."

## 기타 답변
- **접속 주소**: 제안대로 `DB_URL`은 **직접 연결(non-pooled) 주소**. `postgres://` → `jdbc:postgresql://` 변환 방법을 `integrations.md`와 `.env.example` 주석에 적어줘.
- Neon 기본 브랜치 이름은 `main`이 아니라 **`production`**이야. 문서에는 `production`으로 적어줘.

## 사용자 상태
- Neon 프로젝트·`dev` 브랜치 생성 완료. 로컬 `.env`는 아직 없음. dev 값이 필요한 시점에 사용자에게 요청해줘.

## 끝나면
2.2 완료 보고를 채우고 `docs/handoff/v-to-pm.md`를 덮어쓴 뒤 사용자에게 "PM에게 전달해주세요"라고 알려줘. Render·Vercel 계정 작업 단계에서 멈출 때도 `v-to-pm.md`의 "사용자 작업 요청"에 적어줘.
