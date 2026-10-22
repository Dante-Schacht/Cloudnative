package com.cloudorders.orders.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * AUTENTICACIÓN: cómo se obtiene y valida el JWT (firma con las claves públicas de Entra ID, issuer,
 * expiración y audience). La AUTORIZACIÓN por scope vive en {@link SecurityConfig}.
 */
@Configuration
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationConfig.class);

    /** Cabecera donde API Gateway deja el token original cuando reemplaza Authorization por su propio token. */
    static final String FORWARDED_AUTHORIZATION = "X-Forwarded-Authorization";

    @Bean
    public JwtDecoder jwtDecoder(JwtSecurityProperties props) {
        if (!props.isConfigured()) {
            log.warn("Entra ID no está configurado (ENTRA_TENANT_ID / ENTRA_API_AUDIENCE): "
                    + "todas las peticiones protegidas serán rechazadas con 401.");
            return token -> {
                throw new BadJwtException("La validación de tokens no está configurada.");
            };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.resolvedJwkSetUri()).build();
        decoder.setJwtValidator(tokenValidator(props.resolvedIssuer(), props.audience()));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator(audience));
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "El token no está dirigido a esta API.", null);
        return jwt -> {
            List<String> audiences = jwt.getAudience();
            return audiences != null && audiences.contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(error);
        };
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver forwarded = new DefaultBearerTokenResolver();
        forwarded.setBearerTokenHeaderName(FORWARDED_AUTHORIZATION);
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> {
            String token = forwarded.resolve(request);
            return token != null ? token : standard.resolve(request);
        };
    }
}
