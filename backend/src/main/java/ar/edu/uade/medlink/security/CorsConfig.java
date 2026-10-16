package ar.edu.uade.medlink.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(@Value("${MEDLINK_CORS_ALLOWED_ORIGINS:}") String origins) {
        CorsConfiguration config=new CorsConfiguration();
        if (!origins.isBlank()) {
            java.util.List<String> allowed=new java.util.ArrayList<>();
            for (String raw : origins.split(",")) {
                String origin=raw.trim();
                if (!origin.isEmpty()) allowed.add(origin);
            }
            config.setAllowedOrigins(allowed);
        }
        config.setAllowedMethods(java.util.List.of("GET","POST","PUT","OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("Authorization","Content-Type","X-Correlation-Id","Idempotency-Key"));
        config.setExposedHeaders(java.util.List.of("X-Correlation-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**",config);
        return source;
    }
}
