package ar.edu.uade.medlink.security;

import ar.edu.uade.medlink.common.error.ErrorResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    // The Bearer filter belongs only to the Spring Security chain, never the
    // servlet container chain; double registration would clear auth prematurely.
    @Bean
    FilterRegistrationBean<BearerAuthenticationFilter> bearerServletRegistration(BearerAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerAuthenticationFilter bearer,
                                            ErrorResponses errors) throws Exception {
        http.csrf(csrf -> csrf.disable()) // Stateless Bearer header; no cookie credentials
            .cors(Customizer.withDefaults()) // Only explicitly configured browser origins
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(login -> login.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, PublicApiRoutes.GET_PATTERNS).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, exception) ->
                    errors.write(request,response,401,"INVALID_TOKEN","Se requiere una sesión válida."))
                .accessDeniedHandler((request, response, exception) ->
                    errors.write(request,response,403,"FORBIDDEN","No tenés permisos para realizar esta operación.")))
            .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
