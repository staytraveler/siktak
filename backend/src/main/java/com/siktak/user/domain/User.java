package com.siktak.user.domain;

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

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auth_provider", nullable = false)
    private String authProvider = "LOCAL";

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    /** 참가 제한 (SAF-02). 이미 정지된 계정은 낮추지 않는다. */
    public void restrict() {
        if (accountStatus == AccountStatus.ACTIVE) {
            accountStatus = AccountStatus.RESTRICTED;
        }
    }

    public void ban() {
        accountStatus = AccountStatus.BANNED;
    }

    public static User create(String loginId, UserRole role, String name, String phone) {
        return create(loginId, role, name, phone, null);
    }

    public static User create(String loginId, UserRole role, String name, String phone, String passwordHash) {
        User user = new User();
        user.passwordHash = passwordHash;
        user.loginId = loginId;
        user.role = role;
        user.name = name;
        user.phone = phone;
        return user;
    }
}
