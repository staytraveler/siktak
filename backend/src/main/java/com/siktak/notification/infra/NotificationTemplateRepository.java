package com.siktak.notification.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.notification.domain.NotificationTemplate;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> {
}
