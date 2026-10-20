package com.siktak.chat.domain;

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

/** 테이블당 1개인 앱 내 단체방 (INF-04). dining_table_id 유니크로 중복 생성을 막는다. */
@Getter
@Entity
@Table(name = "chat_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dining_table_id", nullable = false, unique = true)
    private Long diningTableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_status", nullable = false)
    private RoomStatus roomStatus;

    /** 테이블 해체로 방을 닫는다 (메시지는 남는다). */
    public void close() {
        this.roomStatus = RoomStatus.CLOSED;
    }

    public static ChatRoom open(Long diningTableId) {
        ChatRoom room = new ChatRoom();
        room.diningTableId = diningTableId;
        room.roomStatus = RoomStatus.ACTIVE;
        return room;
    }

    public enum RoomStatus {
        ACTIVE,
        CLOSED
    }
}
