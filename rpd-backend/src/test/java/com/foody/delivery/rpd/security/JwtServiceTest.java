package com.foody.delivery.rpd.security;

import com.foody.delivery.rpd.infra.security.JwtService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JwtServiceTest {

    private static final String SECRET = "xz0gwmBP1hVaxvC0gTwJdDqRzL929HGhOCc1sXwMSur";
    private static final String OTHER_SECRET = "5oZl064p74JTetBInd49qyA7DuV59YdADqoxvo5wQbJ";
    private static final long EXPIRATION_SECONDS = 3600L;

    private JwtService jwtService;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        final SecretKey key = secretKey(SECRET);
        final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = decoderFor(key);

        jwtService = new JwtService(encoder);
        ReflectionTestUtils.setField(jwtService, "expiration", EXPIRATION_SECONDS);
    }

    @Test
    void shouldGenerateTokenWithSubjectExpirationAndScope() {
        final Authentication auth = new UsernamePasswordAuthenticationToken(
                "teste@teste.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        final String token = jwtService.generate(auth);
        final Jwt jwt = decoder.decode(token);

        assertEquals("teste@teste.com", jwt.getSubject());
        assertEquals("ROLE_USER", jwt.getClaimAsString("scope"));
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertEquals(EXPIRATION_SECONDS,
                Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds());
    }

    @Test
    void shouldJoinMultipleAuthoritiesInScopeSeparatedBySpace() {
        final Authentication auth = new UsernamePasswordAuthenticationToken(
                "teste@teste.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));

        final Jwt jwt = decoder.decode(jwtService.generate(auth));

        assertEquals("ROLE_USER ROLE_ADMIN", jwt.getClaimAsString("scope"));
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        final Authentication auth = new UsernamePasswordAuthenticationToken(
                "teste@teste.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        final String token = jwtService.generate(auth);

        final JwtDecoder otherDecoder = decoderFor(secretKey(OTHER_SECRET));

        assertThrows(JwtException.class, () -> otherDecoder.decode(token));
    }

    private static SecretKey secretKey(final String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static JwtDecoder decoderFor(final SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
