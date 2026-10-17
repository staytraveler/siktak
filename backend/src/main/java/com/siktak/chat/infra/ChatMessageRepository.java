package com.siktak.chat.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoomIdOrderByIdAsc(Long chatRoomId);

    boolean existsByMessageKey(String messageKey);
}
