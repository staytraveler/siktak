package com.siktak.chat.domain;

import java.time.Instant;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "chat_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    public static ChatMember join(Long chatRoomId, Long userId, Instant joinedAt) {
        ChatMember member = new ChatMember();
        member.chatRoomId = chatRoomId;
        member.userId = userId;
        member.joinedAt = joinedAt;
        return member;
    }
}
