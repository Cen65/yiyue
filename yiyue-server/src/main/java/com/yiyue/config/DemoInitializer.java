package com.yiyue.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.domain.*;
import com.yiyue.mapper.*;
import com.yiyue.scheduling.ScheduleService;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "yiyue.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoInitializer implements ApplicationRunner {
  private final TenantMapper tenants;
  private final AccountMapper accounts;
  private final CampusMapper campuses;
  private final DepartmentMapper departments;
  private final DoctorMapper doctors;
  private final ScheduleService schedules;
  private final PasswordEncoder encoder;
  private final Clock clock;

  @Value("${yiyue.demo.admin-password}")
  private String adminPassword;

  @Value("${yiyue.demo.patient-password}")
  private String patientPassword;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (adminPassword.length() < 12 || patientPassword.length() < 12)
      throw new IllegalStateException("演示密码至少12位，请设置环境变量");
    // 仅显式启用时创建全虚构演示数据，不覆盖已有机构或账号密码。
    seed("hospital", "医约示范医院", true);
    seed("clinic", "青禾示范诊所", false);
  }

  private void seed(String code, String name, boolean hospital) {
    if (tenants.exists(Wrappers.<Tenant>lambdaQuery().eq(Tenant::getCode, code))) return;
    Tenant tenant = new Tenant();
    tenant.setCode(code);
    tenant.setName(name);
    tenants.insert(tenant);
    Account admin = account(tenant.getId(), "admin", "机构管理员", "ADMIN", adminPassword);
    account(tenant.getId(), "patient", "演示就诊人", "PATIENT", patientPassword);
    Department department = new Department();
    department.setTenantId(tenant.getId());
    department.setName(hospital ? "心血管内科" : "全科门诊");
    departments.insert(department);
    for (int n = 0; n < (hospital ? 2 : 1); n++) {
      Campus campus = new Campus();
      campus.setTenantId(tenant.getId());
      campus.setName(hospital ? (n == 0 ? "中心院区" : "滨江院区") : "青禾门诊");
      campus.setAddress("虚构演示地址");
      campuses.insert(campus);
      Doctor doctor = new Doctor();
      doctor.setTenantId(tenant.getId());
      doctor.setDepartmentId(department.getId());
      doctor.setName(n == 0 ? "林知远" : "陈若宁");
      doctor.setTitle("主任医师");
      doctor.setSpecialty(hospital ? "心血管疾病咨询与随访" : "常见病咨询与健康管理");
      doctors.insert(doctor);
      for (int day = 1; day <= 3; day++) {
        LocalDateTime start = LocalDate.now(clock).plusDays(day).atTime(9, 0);
        schedules.create(
            new SessionUser(admin.getId(), tenant.getId(), admin.getDisplayName(), "ADMIN"),
            new ScheduleService.Create(
                campus.getId(), doctor.getId(), start, start.plusHours(3), 30, hospital ? 5 : 2));
      }
    }
  }

  private Account account(
      long tenantId, String username, String name, String role, String password) {
    Account a = new Account();
    a.setTenantId(tenantId);
    a.setUsername(username);
    a.setDisplayName(name);
    a.setRole(role);
    a.setPasswordHash(encoder.encode(password));
    accounts.insert(a);
    return a;
  }
}
