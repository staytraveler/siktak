package com.siktak.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siktak.common.error.BusinessException;
import com.siktak.profile.application.ContactService;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/** ACC-02: 이름·연락처 입력·수정 검증 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private ContactService service;

    @Test
    void 이름과_연락처를_수정한다() {
        User user = User.create("u1", UserRole.PARTICIPANT, "기존", "010-0000-0000");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ContactService.Contact saved = service.save(1L, " 새이름 ", "010-1234-5678");

        assertThat(saved.name()).isEqualTo("새이름");
        assertThat(user.getPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    void 이름이_비었거나_연락처_형식이_틀리면_거절한다() {
        assertThatThrownBy(() -> service.save(1L, " ", "010-1234-5678")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.save(1L, "홍길동", "abc")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.save(1L, "홍길동", null)).isInstanceOf(BusinessException.class);
    }
}
