package com.cloudorders.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validación de tokens emitidos por Microsoft Entra ID (cloudorders.security.jwt.*). */
@ConfigurationProperties(prefix = "cloudorders.security.jwt")
public record JwtSecurityProperties(
        String tenantId,
        String audience,
        String issuer,
        String jwkSetUri,
        String readScope,
        String writeScope) {

    public boolean isConfigured() {
        return notBlank(audience) && (notBlank(tenantId) || (notBlank(issuer) && notBlank(jwkSetUri)));
    }

    public String resolvedIssuer() {
        return notBlank(issuer) ? issuer : "https://login.microsoftonline.com/" + tenantId + "/v2.0";
    }

    public String resolvedJwkSetUri() {
        return notBlank(jwkSetUri) ? jwkSetUri
                : "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
