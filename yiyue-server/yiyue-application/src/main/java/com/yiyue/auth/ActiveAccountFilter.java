package com.yiyue.auth;

import com.yiyue.mapper.AccountMapper;
import com.yiyue.mapper.TenantMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class ActiveAccountFilter extends OncePerRequestFilter {
  private final AccountMapper accounts;
  private final TenantMapper tenants;

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof SessionUser u) {
      var a = accounts.selectById(u.id());
      var t = tenants.selectById(u.tenantId());
      if (a == null
          || t == null
          || !Boolean.TRUE.equals(a.getEnabled())
          || !Boolean.TRUE.equals(t.getEnabled())
          || !a.getTenantId().equals(u.tenantId())
          || !a.getRole().equals(u.role())) {
        SecurityContextHolder.clearContext();
        var session = req.getSession(false);
        if (session != null) session.invalidate();
        res.sendError(401);
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
