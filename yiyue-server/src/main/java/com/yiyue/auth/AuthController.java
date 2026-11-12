package com.yiyue.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.common.BusinessException;
import com.yiyue.domain.*;
import com.yiyue.mapper.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final TenantMapper tenants;
  private final AccountMapper accounts;
  private final PasswordEncoder encoder;
  private final HttpSessionSecurityContextRepository repository;

  public record Login(
      @NotBlank @Size(max = 40) String tenantCode,
      @NotBlank @Size(max = 50) String username,
      @NotBlank @Size(max = 100) String password) {}

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  SessionUser me(@AuthenticationPrincipal SessionUser user) {
    return user;
  }

  @PostMapping("/login")
  SessionUser login(
      @Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
    Tenant tenant =
        tenants.selectOne(Wrappers.<Tenant>lambdaQuery().eq(Tenant::getCode, input.tenantCode()));
    Account account =
        tenant == null
            ? null
            : accounts.selectOne(
                Wrappers.<Account>lambdaQuery()
                    .eq(Account::getTenantId, tenant.getId())
                    .eq(Account::getUsername, input.username()));
    if (account == null || !encoder.matches(input.password(), account.getPasswordHash()))
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "机构、账号或密码不正确");
    var user =
        new SessionUser(
            account.getId(), account.getTenantId(), account.getDisplayName(), account.getRole());
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
