package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.LoginReq;
import com.yiyue.service.AuthService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  @org.springframework.beans.factory.annotation.Value("${yiyue.payment.mock-enabled:true}")
  private boolean mockPayments;

  private final AuthService authService;
  private final com.yiyue.auth.LoginRateLimiter rateLimiter;
  private final HttpSessionSecurityContextRepository repository;

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/capabilities")
  Map<String, Boolean> capabilities() {
    return Map.of("mockPayments", mockPayments);
  }

  @GetMapping("/me")
  SessionUser me(@AuthenticationPrincipal SessionUser user) {
    return user;
  }

  @PostMapping("/login")
  SessionUser login(
      @Valid @RequestBody LoginReq input,
      HttpServletRequest request,
      HttpServletResponse response) {
    rateLimiter.acquire(request.getRemoteAddr(), input.tenantCode(), input.username());
    var user = authService.authenticate(input);
    // 登录成功后轮换会话 ID，防止会话固定攻击，并显式持久化认证上下文。
    request.getSession();
    request.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
    SecurityContextHolder.setContext(context);
    repository.saveContext(context, request, response);
    return user;
  }
}
