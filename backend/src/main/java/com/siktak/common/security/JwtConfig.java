package com.siktak.common.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import lombok.extern.slf4j.Slf4j;

/**
 * JWT(HS256) 서명 키·인코더·디코더. 키는 {@code siktak.jwt.secret}(환경변수 JWT_SECRET, 32바이트 이상).
 * 로컬·테스트에서 비어 있으면 기동마다 임의 키를 만든다 (재기동 시 기존 토큰 무효). prod는 application-prod.yml에서 필수.
 *
 * <p>토큰 만료는 데모 시계가 아니라 <b>실제 시각</b>으로 판단한다 — 데모 도구로 시계를 며칠 옮겨도 로그인이 풀리지 않게 하기 위해서다
 * (decisions.md [CC] 3.1).
 */
@Slf4j
@Configuration
public class JwtConfig {

    static final String ISSUER = "siktak";
    static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final int MIN_SECRET_BYTES = 32;

    /** 토큰 발급·검증 전용 실제 시각. 도메인 시각은 ClockConfig의 Clock을 쓴다. */
    private static final Clock REAL_CLOCK = Clock.systemUTC();

    private final SecretKey key;

    public JwtConfig(@Value("${siktak.jwt.secret:}") String secret) {
        this.key = new SecretKeySpec(secretBytes(secret), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(REAL_CLOCK);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        return decoder;
    }

    @Bean
    public JwtTokenService jwtTokenService(JwtEncoder jwtEncoder) {
        return new JwtTokenService(jwtEncoder, REAL_CLOCK);
    }

    private static byte[] secretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("siktak.jwt.secret is empty; using a random key (tokens are invalidated on restart)");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("siktak.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return bytes;
    }
}
