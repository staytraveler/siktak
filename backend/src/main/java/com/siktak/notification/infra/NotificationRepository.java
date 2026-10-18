package com.siktak.notification.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.notification.domain.Notification;
import com.siktak.notification.domain.NotificationStatus;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByDedupKey(String dedupKey);

    List<Notification> findByUserIdAndSendStatusOrderByIdDesc(Long userId, NotificationStatus sendStatus);

    List<Notification> findBySendStatusOrderByIdAsc(NotificationStatus sendStatus);

    List<Notification> findByUserIdInOrderByIdAsc(java.util.Collection<Long> userIds);
}
