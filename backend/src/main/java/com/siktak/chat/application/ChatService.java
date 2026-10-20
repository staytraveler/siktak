package com.siktak.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siktak.chat.domain.ChatMember;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.chat.domain.ChatRoom;
import com.siktak.chat.infra.ChatMemberRepository;
import com.siktak.chat.infra.ChatMessageRepository;
import com.siktak.chat.infra.ChatRoomRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.user.domain.AccountStatus;
import com.siktak.user.domain.User;
import com.siktak.user.infra.UserRepository;

import lombok.RequiredArgsConstructor;

/** 앱 내 단체방 (INF-04·05). 생성·초대·시스템 메시지는 모두 멱등이다. */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public static final int MAX_MESSAGE_LENGTH = 500;

    /** 테이블의 단체방을 열고 멤버를 초대한다. 이미 있으면 기존 방을 쓰고 빠진 멤버만 추가한다. */
    @Transactional
    public ChatRoom openForTable(Long diningTableId, List<Long> userIds) {
        ChatRoom room = chatRoomRepository.findByDiningTableId(diningTableId)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.open(diningTableId)));
        Instant now = Instant.now(clock);
        for (Long userId : userIds) {
            if (!chatMemberRepository.existsByChatRoomIdAndUserId(room.getId(), userId)) {
                chatMemberRepository.save(ChatMember.join(room.getId(), userId, now));
            }
        }
        return room;
    }

    /** 시스템 메시지를 게시한다. 같은 messageKey는 한 번만 게시된다. 새로 게시하면 true. */
    @Transactional
    public boolean postSystemMessage(Long chatRoomId, ChatMessage.MessageType type, String body, String messageKey,
            Map<String, Object> payload) {
        if (chatMessageRepository.existsByMessageKey(messageKey)) {
            return false;
        }
        chatMessageRepository.save(ChatMessage.system(chatRoomId, type, body, messageKey, payload));
        return true;
    }

    /** 방 멤버만 메시지를 볼 수 있다. */
    @Transactional(readOnly = true)
    public List<ChatMessage> messagesFor(Long chatRoomId, Long userId) {
        if (!chatMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "단체방을 찾을 수 없습니다.");
        }
        return chatMessageRepository.findByChatRoomIdOrderByIdAsc(chatRoomId);
    }

    /** 운영자용: 멤버 확인 없이 방의 모든 메시지. */
    @Transactional(readOnly = true)
    public List<ChatMessage> messagesOfRoom(Long chatRoomId) {
        return chatMessageRepository.findByChatRoomIdOrderByIdAsc(chatRoomId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ChatRoom> findByTable(Long diningTableId) {
        return chatRoomRepository.findByDiningTableId(diningTableId);
    }

    /** 참석 취소로 단체방에서 나간다 (취소한 사람만, 정책 허용 시점). */
    @Transactional
    public void removeMember(Long diningTableId, Long userId) {
        chatRoomRepository.findByDiningTableId(diningTableId)
                .ifPresent(room -> chatMemberRepository.deleteByChatRoomIdAndUserId(room.getId(), userId));
    }

    /** 테이블이 해체되면 단체방을 닫는다 (메시지는 남고 더 보낼 수 없다). */
    @Transactional
    public void closeRoom(Long diningTableId) {
        chatRoomRepository.findByDiningTableId(diningTableId).ifPresent(ChatRoom::close);
    }

    /** 참가자 메시지 전송. 해당 테이블 단체방 구성원만, 이용 제한·정지 계정은 막는다. */
    @Transactional
    public ChatMessage postUserMessage(Long diningTableId, Long userId, String content) {
        String body = normalize(content);
        ChatRoom room = chatRoomRepository.findByDiningTableId(diningTableId)
                .filter(r -> chatMemberRepository.existsByChatRoomIdAndUserId(r.getId(), userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "이 테이블의 단체방 구성원만 보낼 수 있습니다."));
        if (room.getRoomStatus() == ChatRoom.RoomStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CONFLICT, "닫힌 단체방에는 메시지를 보낼 수 없습니다.");
        }
        User sender = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (sender.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이용이 제한된 계정은 메시지를 보낼 수 없습니다.");
        }
        return chatMessageRepository.save(ChatMessage.fromUser(room.getId(), userId, body));
    }

    /** 앞뒤 공백을 지우고 1~500자인지 검사한다. */
    public static String normalize(String content) {
        String body = content == null ? "" : content.strip();
        if (body.isEmpty() || body.codePointCount(0, body.length()) > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "메시지는 1자 이상 " + MAX_MESSAGE_LENGTH + "자 이하로 입력해주세요.");
        }
        return body;
    }

    /** 사용자 메시지 보낸 사람의 표시용 이니셜(이름 첫 글자). 실명·연락처는 내보내지 않는다. */
    @Transactional(readOnly = true)
    public Map<Long, String> senderInitials(List<ChatMessage> messages) {
        Set<Long> ids = messages.stream().map(ChatMessage::getSenderId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> initials = new HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            String name = u.getName();
            initials.put(u.getId(), name == null || name.isBlank() ? "?"
                    : new String(Character.toChars(name.codePointAt(0))));
        }
        return initials;
    }
}
