package com.yiyue.auth;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  HttpSessionSecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      HttpSessionSecurityContextRepository repository,
      com.yiyue.mapper.AccountMapper accounts,
      com.yiyue.mapper.TenantMapper tenants)
      throws Exception {
    // 同源 Cookie 会话配合 CSRF 请求头，避免把长期令牌放在浏览器存储中。
    http.securityContext(c -> c.securityContextRepository(repository))
        .csrf(
            c ->
                c.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/csrf", "/api/auth/login", "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/platform/**")
                    .hasRole("PLATFORM_ADMIN")
                    .requestMatchers("/api/staff/**")
                    .hasAnyRole("ADMIN", "RECEPTION", "DOCTOR")
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/patient/**")
                    .hasRole("PATIENT")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                    .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, a) -> res.setStatus(204)));
    http.addFilterAfter(
        new ActiveAccountFilter(accounts, tenants),
        org.springframework.security.web.context.SecurityContextHolderFilter.class);
    return http.build();
  }
}
