package com.example.devforge.security;

import jakarta.servlet.http.Cookie;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/users/register",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/oauth2/ticket",
                        "/auth/oauth2/signup/pending",
                        "/auth/oauth2/signup/complete",
                        "/github/webhooks",
                        "/internal/provisioning/jobs/*/user-token",
                        "/internal/provisioning/jobs/*/claim")
                    .permitAll()
                    .requestMatchers(HttpMethod.PUT, "/internal/provisioning/jobs/*/repository")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/internal/templates/artifacts/*")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/auth/oauth2/authorize/*",
                        "/auth/oauth2/callback",
                        "/github/callback")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .bearerTokenResolver(bearerTokenResolver())
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterBefore(new CsrfHeaderFilter(), BearerTokenAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Map<String, Object> realmAccess = jwt.getClaim("realm_access");
          if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
          }
          return roles.stream()
              .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
              .toList();
        });
    return converter;
  }

  @Value("${devforge.cors.allowed-origins}")
  String allowedOrigins;

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    var cfg = new CorsConfiguration();

    cfg.setAllowedOrigins(List.of(allowedOrigins));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    cfg.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cfg);
    return source;
  }

  @Bean
  BearerTokenResolver bearerTokenResolver() {
    var header = new DefaultBearerTokenResolver();
    return request -> {
      String fromHeader = header.resolve(request);
      if (fromHeader != null) return fromHeader;
      String uri = request.getRequestURI();
      if (uri.contains("/auth/")
          || uri.endsWith("/github/callback")
          || uri.endsWith("/github/webhooks")
          || uri.contains("/internal/")
          || request.getCookies() == null) return null;
      for (Cookie c : request.getCookies()) {
        if (AuthCookies.ACCESS.equals(c.getName())) return c.getValue();
      }
      return null;
    };
  }
}
