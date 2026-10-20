package com.siktak.application.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siktak.application.domain.PassType;
import com.siktak.application.infra.ApplicationRepository;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.payment.application.PaymentService;
import com.siktak.profile.application.ProfileService;
import com.siktak.slot.domain.EventSlot;
import com.siktak.slot.domain.Region;
import com.siktak.slot.infra.EventSlotRepository;

/** 3.2 신청 검증: 프로필 미완성, 회차 0개·6개, 마감 회차 (APP-02·03). */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceValidationTest {

    private static final Instant NOW = Instant.parse("2026-10-17T03:00:00Z");

    @Mock private ApplicationRepository applicationRepository;
    @Mock private EventSlotRepository eventSlotRepository;
    @Mock private StatusTransitionService transitionService;
    @Mock private PaymentService paymentService;
    @Mock private com.siktak.payment.application.UserPassService userPassService;
    @Mock private com.siktak.exception.application.ExceptionCaseService exceptionCaseService;
    @Mock private ProfileService profileService;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, eventSlotRepository, transitionService, paymentService,
                userPassService, exceptionCaseService, profileService, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    private BusinessException submitFailure(List<Long> ids) {
        return (BusinessException) catchThrowable(() -> service.submit(1L, ids, PassType.SINGLE));
    }

    @Test
    void 회차를_0개_고르면_거절한다() {
        assertThat(submitFailure(List.of()).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 회차를_6개_고르면_거절한다() {
        assertThat(submitFailure(List.of(1L, 2L, 3L, 4L, 5L, 6L)).getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 프로필이_미완성이면_PROFILE_INCOMPLETE로_거절한다() {
        when(profileService.isComplete(1L)).thenReturn(false);
        assertThat(submitFailure(List.of(1L)).getErrorCode()).isEqualTo(ErrorCode.PROFILE_INCOMPLETE);
    }

    @Test
    void 마감이_지난_회차가_있으면_409로_거절한다() {
        when(profileService.isComplete(1L)).thenReturn(true);
        Instant startsAt = NOW.plus(Duration.ofDays(3));
        EventSlot closed = EventSlot.create(Region.create("테스트"), startsAt, startsAt.plus(Duration.ofHours(2)), 30,
                NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(1)));
        when(eventSlotRepository.findByIdIn(List.of(7L))).thenReturn(List.of(closed));
        BusinessException e = submitFailure(List.of(7L));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }
}
