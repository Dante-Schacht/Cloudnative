package com.cloudorders.orders.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * AUTORIZACIÓN: qué scope exige cada endpoint. Leer pedidos requiere el scope de lectura y crearlos el de
 * escritura (claim scp del access token de Entra ID, que Spring expone como SCOPE_*).
 */
@Configuration
public class SecurityConfig {

    private static final String SCOPE_PREFIX = "SCOPE_";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtSecurityProperties props,
            BearerTokenResolver bearerTokenResolver) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders").hasAuthority(SCOPE_PREFIX + props.readScope())
                        .requestMatchers(HttpMethod.POST, "/api/orders").hasAuthority(SCOPE_PREFIX + props.writeScope())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(withDefaults()))
                .build();
    }
}
