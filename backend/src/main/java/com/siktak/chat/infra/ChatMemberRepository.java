package com.siktak.chat.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.chat.domain.ChatMember;

public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {

    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);
}
