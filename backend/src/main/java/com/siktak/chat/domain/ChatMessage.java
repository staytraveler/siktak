package com.siktak.chat.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.siktak.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 단체방 메시지. 시스템 메시지는 message_key 유니크로 중복 게시를 막는다 (INF-05). */
@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private SenderType senderType;

    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    @Column(nullable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Column(name = "message_key", unique = true)
    private String messageKey;

    public static ChatMessage system(Long chatRoomId, MessageType type, String body, String messageKey,
            Map<String, Object> payload) {
        ChatMessage message = new ChatMessage();
        message.chatRoomId = chatRoomId;
        message.senderType = SenderType.SYSTEM;
        message.messageType = type;
        message.body = body;
        message.messageKey = messageKey;
        message.payload = payload;
        return message;
    }

    public enum SenderType {
        USER,
        SYSTEM
    }

    public enum MessageType {
        TEXT,
        NOTICE,
        CURATION,
        REMINDER,
        CONTENT
    }
}
