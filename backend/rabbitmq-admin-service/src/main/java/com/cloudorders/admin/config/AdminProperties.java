package com.cloudorders.admin.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("cloudorders.admin")
public record AdminProperties(
        @NotBlank String managedPrefix,
        @NotBlank String username,
        @NotBlank @Size(min = 12, message = "la contraseña de la API de administración debe tener al menos 12 caracteres")
        String password) {
}
