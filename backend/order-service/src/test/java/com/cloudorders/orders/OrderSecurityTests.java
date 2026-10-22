package com.cloudorders.orders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudorders.orders.config.JwtValidationConfig;
import com.cloudorders.orders.messaging.OrderEventPublisher;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la lógica de validación JWT (firma, issuer, expiración, audience) y de autorización por scope.
 * Los tokens se firman con una clave RSA generada en la prueba: NO sustituyen la verificación con tokens reales
 * de Microsoft Entra ID, que se documenta aparte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderSecurityTests {

    private static final String ISSUER = "https://login.microsoftonline.com/test-tenant/v2.0";
    private static final String AUDIENCE = "api://cloudorders-test";
    private static final String VALID_ORDER = """
            {"customerName":"Ana","product":"Teclado","quantity":1,"notes":""}""";

    private static final RSAKey TRUSTED_KEY = newKey();
    private static final RSAKey UNTRUSTED_KEY = newKey();

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() throws JOSEException {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TRUSTED_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidationConfig.tokenValidator(ISSUER, AUDIENCE));
            return decoder;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderEventPublisher eventPublisher;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenSignedWithUntrustedKey() throws Exception {
        String token = token(UNTRUSTED_KEY, ISSUER, AUDIENCE, Duration.ofMinutes(30), "orders.read");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = token(TRUSTED_KEY, ISSUER, AUDIENCE, Duration.ofHours(-1), "orders.read");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithWrongAudience() throws Exception {
        String token = token(TRUSTED_KEY, ISSUER, "api://otra-api", Duration.ofMinutes(30), "orders.read");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithWrongIssuer() throws Exception {
        String token = token(TRUSTED_KEY, "https://login.microsoftonline.com/otro-tenant/v2.0", AUDIENCE,
                Duration.ofMinutes(30), "orders.read");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidTokenWithSufficientScopes() throws Exception {
        String token = token(TRUSTED_KEY, ISSUER, AUDIENCE, Duration.ofMinutes(30), "orders.read orders.write");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsValidTokenWithoutRequiredScope() throws Exception {
        String readOnly = token(TRUSTED_KEY, ISSUER, AUDIENCE, Duration.ofMinutes(30), "orders.read");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + readOnly))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + readOnly)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isForbidden());

        String noScopes = token(TRUSTED_KEY, ISSUER, AUDIENCE, Duration.ofMinutes(30), "otro.scope");
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer " + noScopes))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthDoesNotRequireToken() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void readsOriginalTokenForwardedByApiGateway() throws Exception {
        String token = token(TRUSTED_KEY, ISSUER, AUDIENCE, Duration.ofMinutes(30), "orders.read");
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer token-de-google-no-valido-para-esta-api")
                        .header("X-Forwarded-Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static String token(RSAKey key, String issuer, String audience, Duration validFor, String scopes)
            throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("usuario-de-prueba")
                .issueTime(Date.from(now.minus(Duration.ofHours(2))))
                .expirationTime(Date.from(now.plus(validFor)))
                .claim("scp", scopes)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key-" + System.nanoTime()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
