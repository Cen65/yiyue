package com.yiyue.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "yiyue.platform.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PlatformInitializer implements ApplicationRunner {
  private final TenantMapper tenants;
  private final AccountMapper accounts;
  private final PasswordEncoder encoder;

  @Value("${yiyue.platform.password:}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (password.length() < 12 || password.length() > 100)
      throw new IllegalStateException("平台管理员初始化密码须为12至100位");
    com.yiyue.common.PasswordPolicy.validate(password);
    // 平台账号仅在显式启用且首次初始化时创建，不覆盖已有账号或轮换密码。
    var t = tenants.selectOne(Wrappers.<TenantDO>lambdaQuery().eq(TenantDO::getCode, "platform"));
    if (t == null) {
      t = new TenantDO();
      t.setCode("platform");
      t.setName("医约平台管理");
      tenants.insert(t);
    }
    if (accounts.exists(
        Wrappers.<AccountDO>lambdaQuery()
            .eq(AccountDO::getTenantId, t.getId())
            .eq(AccountDO::getUsername, "platform_admin"))) return;
    var a = new AccountDO();
    a.setTenantId(t.getId());
    a.setUsername("platform_admin");
    a.setDisplayName("平台管理员");
    a.setRole("PLATFORM_ADMIN");
    a.setPasswordHash(encoder.encode(password));
    accounts.insert(a);
  }
}
