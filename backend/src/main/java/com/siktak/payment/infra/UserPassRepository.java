package com.siktak.payment.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.siktak.payment.domain.PassStatus;
import com.siktak.payment.domain.UserPass;
import com.siktak.payment.domain.UserPassType;

public interface UserPassRepository extends JpaRepository<UserPass, Long> {

    Optional<UserPass> findByPassTypeAndSourceApplicationId(UserPassType passType, Long sourceApplicationId);

    Optional<UserPass> findFirstByUserIdAndPassTypeAndPassStatusOrderByIdAsc(Long userId, UserPassType passType,
            PassStatus passStatus);

    List<UserPass> findByUserIdAndPassStatusOrderByIdDesc(Long userId, PassStatus passStatus);

    List<UserPass> findByUserIdAndPassTypeAndPassStatus(Long userId, UserPassType passType, PassStatus passStatus);
}
