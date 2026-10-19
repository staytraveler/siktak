package com.siktak.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.chat.domain.ChatRoom;
import com.siktak.chat.infra.ChatMemberRepository;
import com.siktak.chat.infra.ChatMessageRepository;
import com.siktak.chat.infra.ChatRoomRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.user.domain.AccountStatus;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/** 9.2 단체방 메시지 전송 검증 (구성원·길이·이용 제한). */
class ChatServiceMessageTest {

    private final ChatRoomRepository rooms = mock(ChatRoomRepository.class);
    private final ChatMemberRepository members = mock(ChatMemberRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatService service = new ChatService(rooms, members, messages, users, Clock.systemUTC());

    private void room() {
        ChatRoom room = ChatRoom.open(7L);
        ReflectionTestUtils.setField(room, "id", 70L);
        when(rooms.findByDiningTableId(7L)).thenReturn(Optional.of(room));
    }

    private User user(AccountStatus status) {
        User u = User.create("user01", UserRole.PARTICIPANT, "김참가", "010-1234-5678");
        ReflectionTestUtils.setField(u, "id", 1L);
        ReflectionTestUtils.setField(u, "accountStatus", status);
        when(users.findById(1L)).thenReturn(Optional.of(u));
        return u;
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void 구성원이_보내면_공백을_뗀_TEXT_USER_메시지로_저장한다() {
        room();
        user(AccountStatus.ACTIVE);
        when(members.existsByChatRoomIdAndUserId(70L, 1L)).thenReturn(true);
        when(messages.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatMessage saved = service.postUserMessage(7L, 1L, "  안녕하세요  ");

        assertThat(saved.getBody()).isEqualTo("안녕하세요");
        assertThat(saved.getSenderType()).isEqualTo(ChatMessage.SenderType.USER);
        assertThat(saved.getMessageType()).isEqualTo(ChatMessage.MessageType.TEXT);
        assertThat(saved.getSenderId()).isEqualTo(1L);
        assertThat(saved.getChatRoomId()).isEqualTo(70L);
    }

    @Test
    void 구성원이_아니거나_단체방이_없으면_403() {
        room();
        when(members.existsByChatRoomIdAndUserId(70L, 1L)).thenReturn(false);
        assertCode(() -> service.postUserMessage(7L, 1L, "hi"), ErrorCode.FORBIDDEN);
        assertCode(() -> service.postUserMessage(99L, 1L, "hi"), ErrorCode.FORBIDDEN);
        verify(messages, never()).save(any());
    }

    @Test
    void 이용_제한_계정은_403() {
        room();
        user(AccountStatus.RESTRICTED);
        when(members.existsByChatRoomIdAndUserId(70L, 1L)).thenReturn(true);
        assertCode(() -> service.postUserMessage(7L, 1L, "hi"), ErrorCode.FORBIDDEN);
        verify(messages, never()).save(any());
    }

    @Test
    void 빈_값과_501자는_400_500자는_통과() {
        assertCode(() -> ChatService.normalize(null), ErrorCode.INVALID_REQUEST);
        assertCode(() -> ChatService.normalize("   "), ErrorCode.INVALID_REQUEST);
        assertCode(() -> ChatService.normalize("가".repeat(501)), ErrorCode.INVALID_REQUEST);
        assertThat(ChatService.normalize("가".repeat(500))).hasSize(500);
    }

    @Test
    void 보낸_사람은_이름_첫_글자_이니셜만_내려간다() {
        User u = user(AccountStatus.ACTIVE);
        when(users.findAllById(Set.of(1L))).thenReturn(List.of(u));
        ChatMessage m = ChatMessage.fromUser(70L, 1L, "hi");
        assertThat(service.senderInitials(List.of(m))).containsEntry(1L, "김");
    }
}
