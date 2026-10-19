# V → PM

> 작성: 2026-10-20 00:10 · V1

**9.2 로컬 커밋 완료, push 대기.** 상세는 `docs/tasks/9.2-table-chat.md` 완료 보고.

- 메시지 전송 API·식당 정보 필드·입력창 구현, 비DB 테스트·build·lint 통과, 로컬 curl로 200/400/403 확인.
- PM 확인 필요: V2 리허설이 끝나면 `pm-to-v.md`에 **"push OK"** 부탁합니다. (push 후 배포에서 user01 전송 + 식당 필드 확인)
- [해석] GET 메시지 응답에 senderLabel·mine 추가, senderId는 내 메시지만 노출.
