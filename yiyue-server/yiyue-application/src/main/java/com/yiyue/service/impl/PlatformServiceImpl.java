package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.TenantResp;
import com.yiyue.service.PlatformService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformServiceImpl implements PlatformService {
  private final TenantMapper tenants;
  private final AccountMapper accounts;
  private final PasswordEncoder encoder;

  private void requirePlatform(SessionUser u) {
    if (!"PLATFORM_ADMIN".equals(u.role()))
      throw new BusinessException(HttpStatus.FORBIDDEN, "仅平台管理员可以管理机构");
  }

  public List<TenantResp> list(SessionUser u) {
    requirePlatform(u);
    return tenants.selectList(Wrappers.<TenantDO>lambdaQuery().orderByAsc(TenantDO::getId)).stream()
        .map(TenantResp::from)
        .toList();
  }

  @Transactional
  public TenantResp create(SessionUser u, TenantCreateReq r) {
    requirePlatform(u);
    com.yiyue.common.PasswordPolicy.validate(r.adminPassword());
    if ("platform".equals(r.code())) throw BusinessException.conflict("该机构编码为平台保留");
    var t = new TenantDO();
    t.setCode(r.code());
    t.setName(r.name().strip());
    tenants.insert(t);
    var a = new AccountDO();
    a.setTenantId(t.getId());
    a.setUsername(r.adminUsername());
    a.setPasswordHash(encoder.encode(r.adminPassword()));
    a.setDisplayName("机构管理员");
    a.setRole("ADMIN");
    accounts.insert(a);
    return TenantResp.from(t);
  }

  @Transactional
  public TenantResp update(SessionUser u, long id, TenantUpdateReq r) {
    requirePlatform(u);
    if (id == u.tenantId() && !r.enabled()) throw BusinessException.conflict("不能停用平台自身机构");
    tenants.lock(id);
    var t = tenants.selectById(id);
    if (t == null) throw BusinessException.missing();
    t.setName(r.name().strip());
    t.setEnabled(r.enabled());
    tenants.updateById(t);
    return TenantResp.from(t);
  }
}
