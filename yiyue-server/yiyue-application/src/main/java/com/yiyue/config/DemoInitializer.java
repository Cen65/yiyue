package com.yiyue.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.mapper.AccountMapper;
import com.yiyue.mapper.CampusMapper;
import com.yiyue.mapper.DepartmentMapper;
import com.yiyue.mapper.DoctorMapper;
import com.yiyue.mapper.TenantMapper;
import com.yiyue.model.entity.AccountDO;
import com.yiyue.model.entity.CampusDO;
import com.yiyue.model.entity.DepartmentDO;
import com.yiyue.model.entity.DoctorDO;
import com.yiyue.model.entity.TenantDO;
import com.yiyue.model.req.ScheduleCreateReq;
import com.yiyue.service.ScheduleService;
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
  private final com.yiyue.mapper.DoctorDepartmentMapper doctorDepartments;
  private final com.yiyue.mapper.DoctorCampusMapper doctorCampuses;

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
    if (tenants.exists(Wrappers.<TenantDO>lambdaQuery().eq(TenantDO::getCode, code))) return;
    TenantDO tenant = new TenantDO();
    tenant.setCode(code);
    tenant.setName(name);
    tenants.insert(tenant);
    AccountDO admin = account(tenant.getId(), "admin", "机构管理员", "ADMIN", adminPassword);
    account(tenant.getId(), "patient", "演示就诊人", "PATIENT", patientPassword);
    DepartmentDO department = new DepartmentDO();
    department.setTenantId(tenant.getId());
    department.setName(hospital ? "心血管内科" : "全科门诊");
    departments.insert(department);
    for (int n = 0; n < (hospital ? 2 : 1); n++) {
      CampusDO campus = new CampusDO();
      campus.setTenantId(tenant.getId());
      campus.setName(hospital ? (n == 0 ? "中心院区" : "滨江院区") : "青禾门诊");
      campus.setAddress("虚构演示地址");
      campuses.insert(campus);
      DoctorDO doctor = new DoctorDO();
      doctor.setTenantId(tenant.getId());
      doctor.setDepartmentId(department.getId());
      doctor.setName(n == 0 ? "林知远" : "陈若宁");
      doctor.setTitle("主任医师");
      doctor.setSpecialty(hospital ? "心血管疾病咨询与随访" : "常见病咨询与健康管理");
      doctors.insert(doctor);
      // 新库在迁移后才初始化演示数据，也必须写入医生关联。
      var depLink = new com.yiyue.model.entity.DoctorDepartmentDO();
      depLink.setTenantId(tenant.getId());
      depLink.setDoctorId(doctor.getId());
      depLink.setDepartmentId(department.getId());
      doctorDepartments.insert(depLink);
      var campusLink = new com.yiyue.model.entity.DoctorCampusDO();
      campusLink.setTenantId(tenant.getId());
      campusLink.setDoctorId(doctor.getId());
      campusLink.setCampusId(campus.getId());
      doctorCampuses.insert(campusLink);

      for (int day = 1; day <= 3; day++) {
        LocalDateTime start = LocalDate.now(clock).plusDays(day).atTime(9, 0);
        schedules.create(
            new SessionUser(admin.getId(), tenant.getId(), admin.getDisplayName(), "ADMIN"),
            new ScheduleCreateReq(
                campus.getId(), doctor.getId(), start, start.plusHours(3), 30, hospital ? 5 : 2));
      }
    }
  }

  private AccountDO account(
      long tenantId, String username, String name, String role, String password) {
    AccountDO a = new AccountDO();
    a.setTenantId(tenantId);
    a.setUsername(username);
    a.setDisplayName(name);
    a.setRole(role);
    a.setPasswordHash(encoder.encode(password));
    accounts.insert(a);
    return a;
  }
}
