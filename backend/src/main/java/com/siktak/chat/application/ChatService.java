package com.siktak.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

import lombok.RequiredArgsConstructor;

/** 앱 내 단체방 (INF-04·05). 생성·초대·시스템 메시지는 모두 멱등이다. */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final Clock clock;

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

    @Transactional(readOnly = true)
    public java.util.Optional<ChatRoom> findByTable(Long diningTableId) {
        return chatRoomRepository.findByDiningTableId(diningTableId);
    }
}
