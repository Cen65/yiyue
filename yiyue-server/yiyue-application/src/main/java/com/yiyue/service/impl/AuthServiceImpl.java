package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.AccountMapper;
import com.yiyue.mapper.TenantMapper;
import com.yiyue.model.entity.AccountDO;
import com.yiyue.model.entity.TenantDO;
import com.yiyue.model.req.LoginReq;
import com.yiyue.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
  private final TenantMapper tenants;
  private final AccountMapper accounts;
  private final PasswordEncoder encoder;

  public SessionUser authenticate(LoginReq input) {
    TenantDO tenant =
        tenants.selectOne(
            Wrappers.<TenantDO>lambdaQuery().eq(TenantDO::getCode, input.tenantCode()));
    AccountDO account =
        tenant == null
            ? null
            : accounts.selectOne(
                Wrappers.<AccountDO>lambdaQuery()
                    .eq(AccountDO::getTenantId, tenant.getId())
                    .eq(AccountDO::getUsername, input.username()));
    if (tenant == null
        || !Boolean.TRUE.equals(tenant.getEnabled())
        || account == null
        || !Boolean.TRUE.equals(account.getEnabled())
        || !encoder.matches(input.password(), account.getPasswordHash()))
      throw new BusinessException(HttpStatus.UNAUTHORIZED, "机构、账号或密码不正确");
    var user =
        new SessionUser(
            account.getId(), account.getTenantId(), account.getDisplayName(), account.getRole());
    return user;
  }
}
