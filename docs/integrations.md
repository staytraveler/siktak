# 외부 연동 (integrations.md)

> 원칙(CLAUDE.md 8장): 모든 외부 연동은 인터페이스(포트) 뒤에 두고 Mock 구현을 항상 함께 만든다.
> 구현 선택은 설정값으로 전환하며, 데모 직전 불안정하면 설정 하나로 Mock으로 돌린다.

## 요약

| 연동 | 포트 | 설정 키 | 구현 | 상태 |
|---|---|---|---|---|
| 결제·환불 | `PaymentGateway` | `siktak.payment.provider` | `mock` / `toss` | 미구현 |
| 알림 | `NotificationSender` | `siktak.notification.channel` | `in-app` | 미구현 |

---

## 1. Mock 결제

- 포트: `PaymentGateway`
- 설정: `siktak.payment.provider=mock`
- 용도: 로컬 개발, 테스트, 데모 기본값. PRD PAY-01·PAY-03은 해커톤에서 모의 결제·모의 환불을 허용한다.
- 동작: (구현 시 작성) 승인·거절·환불 성공·환불 실패를 재현하는 방법
- 멱등성: (구현 시 작성) 멱등 키 처리 방식

## 2. 토스페이먼츠 (테스트 모드)

- 포트: `PaymentGateway`
- 설정: `siktak.payment.provider=toss`
- 환경변수: `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY` (테스트 키, 값은 `.env`에만)
- 관련 PRD: PAY-04(P1 실결제 연동)
- 사용 API: (구현 시 작성) 결제 승인, 결제 취소(전체·부분)
- 실패 처리: (구현 시 작성) 재시도 정책, `ExceptionCase` 전환 조건
- Mock 전환 방법: `siktak.payment.provider=mock`으로 변경 후 재배포

## 3. 앱 내 알림

- 포트: `NotificationSender`
- 설정: `siktak.notification.channel=in-app`
- 용도: PRD 14장 알림 시나리오, INF-01·INF-07·INF-08
- 동작: (구현 시 작성) 저장 방식, 단체방 시스템 메시지와의 관계
- 재시도: `policy.notification-retry-max`(기본 3) 후 반복 실패만 `ExceptionCase` 전환
- 이후 확장: 웹 푸시, 알림톡 (MVP 범위 밖)
