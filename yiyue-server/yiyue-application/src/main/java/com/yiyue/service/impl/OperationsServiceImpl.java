package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.OperationsService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationsServiceImpl implements OperationsService {
  private final TenantMapper tenants;
  private final CampusMapper campuses;
  private final DepartmentMapper departments;
  private final DoctorMapper doctors;
  private final ScheduleMapper schedules;
  private final DoctorDepartmentMapper doctorDepartments;
  private final DoctorCampusMapper doctorCampuses;
  private final AccountMapper accounts;
  private final PasswordEncoder encoder;
  private final Clock clock;

  public Map<String, Object> overview(SessionUser u) {
    var staff =
        accounts
            .selectList(
                Wrappers.<AccountDO>lambdaQuery()
                    .eq(AccountDO::getTenantId, u.tenantId())
                    .in(AccountDO::getRole, List.of("DOCTOR", "RECEPTION")))
            .stream()
            .map(
                a ->
                    Map.of(
                        "id",
                        a.getId(),
                        "username",
                        a.getUsername(),
                        "name",
                        a.getDisplayName(),
                        "role",
                        a.getRole()))
            .toList();
    return Map.of(
        "tenant",
        TenantResp.from(tenants.selectById(u.tenantId())),
        "campuses",
        campuses
            .selectList(Wrappers.<CampusDO>lambdaQuery().eq(CampusDO::getTenantId, u.tenantId()))
            .stream()
            .map(CampusResp::from)
            .toList(),
        "departments",
        departments
            .selectList(
                Wrappers.<DepartmentDO>lambdaQuery().eq(DepartmentDO::getTenantId, u.tenantId()))
            .stream()
            .map(DepartmentResp::from)
            .toList(),
        "doctors",
        doctors
            .selectList(Wrappers.<DoctorDO>lambdaQuery().eq(DoctorDO::getTenantId, u.tenantId()))
            .stream()
            .map(DoctorResp::from)
            .toList(),
        "doctorDepartments",
        doctorDepartments
            .selectList(
                Wrappers.<DoctorDepartmentDO>lambdaQuery()
                    .eq(DoctorDepartmentDO::getTenantId, u.tenantId()))
            .stream()
            .map(DoctorDepartmentResp::from)
            .toList(),
        "doctorCampuses",
        doctorCampuses
            .selectList(
                Wrappers.<DoctorCampusDO>lambdaQuery()
                    .eq(DoctorCampusDO::getTenantId, u.tenantId()))
            .stream()
            .map(DoctorCampusResp::from)
            .toList(),
        "staff",
        staff);
  }

  @Transactional
  public Object save(SessionUser u, String kind, Long id, OperationsReq r) {
    // 同一机构的配置和排班发布共用行锁，检查与写入保持原子性。
    tenants.lock(u.tenantId());
    String name = r.name().strip();
    if (name.isBlank()) throw BusinessException.conflict("名称不能为空");
    return switch (kind) {
      case "tenant" -> saveTenant(u, id, r, name);
      case "campuses" -> saveCampus(u, id, r, name);
      case "departments" -> saveDepartment(u, id, r, name);
      case "doctors" -> saveDoctor(u, id, r, name);
      default -> throw BusinessException.missing();
    };
  }

  private Object saveTenant(SessionUser u, Long id, OperationsReq r, String name) {
    if (id == null || id != u.tenantId()) throw BusinessException.missing();
    if (!r.enabled()) throw BusinessException.conflict("机构停用须由平台运维处理，不能停用当前机构");
    tenants.update(
        Wrappers.<TenantDO>lambdaUpdate()
            .eq(TenantDO::getId, u.tenantId())
            .set(TenantDO::getName, name));
    return TenantResp.from(tenants.selectById(u.tenantId()));
  }

  private Object saveCampus(SessionUser u, Long id, OperationsReq r, String name) {
    var c =
        id == null
            ? new CampusDO()
            : campuses.selectOne(
                Wrappers.<CampusDO>lambdaQuery()
                    .eq(CampusDO::getTenantId, u.tenantId())
                    .eq(CampusDO::getId, id));
    if (c == null) throw BusinessException.missing();
    if (campuses.exists(
        Wrappers.<CampusDO>lambdaQuery()
            .eq(CampusDO::getTenantId, u.tenantId())
            .eq(CampusDO::getName, name)
            .ne(id != null, CampusDO::getId, id))) throw BusinessException.conflict("院区名称已存在");
    if (!r.enabled()
        && id != null
        && schedules.exists(
            Wrappers.<ScheduleDO>lambdaQuery()
                .eq(ScheduleDO::getTenantId, u.tenantId())
                .eq(ScheduleDO::getStatus, "OPEN")
                .eq(ScheduleDO::getCampusId, id)
                .gt(ScheduleDO::getEndAt, LocalDateTime.now(clock))))
      throw BusinessException.conflict("院区仍有未结束排班，不能停用");
    c.setTenantId(u.tenantId());
    c.setName(name);
    c.setAddress(r.address() == null ? "" : r.address().strip());
    c.setEnabled(r.enabled());
    if (id == null) campuses.insert(c);
    else campuses.updateById(c);
    return CampusResp.from(c);
  }

  private Object saveDepartment(SessionUser u, Long id, OperationsReq r, String name) {
    var d =
        id == null
            ? new DepartmentDO()
            : departments.selectOne(
                Wrappers.<DepartmentDO>lambdaQuery()
                    .eq(DepartmentDO::getTenantId, u.tenantId())
                    .eq(DepartmentDO::getId, id));
    if (d == null) throw BusinessException.missing();
    if (departments.exists(
        Wrappers.<DepartmentDO>lambdaQuery()
            .eq(DepartmentDO::getTenantId, u.tenantId())
            .eq(DepartmentDO::getName, name)
            .ne(id != null, DepartmentDO::getId, id))) throw BusinessException.conflict("科室名称已存在");
    if (!r.enabled() && id != null) {
      if (doctors.exists(
          Wrappers.<DoctorDO>lambdaQuery()
              .eq(DoctorDO::getTenantId, u.tenantId())
              .eq(DoctorDO::getDepartmentId, id)
              .eq(DoctorDO::getEnabled, true)))
        throw BusinessException.conflict("科室仍有关联的启用医生，不能停用");
      var links =
          doctorDepartments.selectList(
              Wrappers.<DoctorDepartmentDO>lambdaQuery()
                  .eq(DoctorDepartmentDO::getTenantId, u.tenantId())
                  .eq(DoctorDepartmentDO::getDepartmentId, id));
      if (!links.isEmpty()
          && doctors.exists(
              Wrappers.<DoctorDO>lambdaQuery()
                  .eq(DoctorDO::getTenantId, u.tenantId())
                  .in(DoctorDO::getId, links.stream().map(DoctorDepartmentDO::getDoctorId).toList())
                  .eq(DoctorDO::getEnabled, true)))
        throw BusinessException.conflict("科室仍有关联的启用医生，不能停用");
    }
    d.setTenantId(u.tenantId());
    d.setName(name);
    d.setEnabled(r.enabled());
    if (id == null) departments.insert(d);
    else departments.updateById(d);
    return DepartmentResp.from(d);
  }

  private Object saveDoctor(SessionUser u, Long id, OperationsReq r, String name) {
    var d =
        id == null
            ? new DoctorDO()
            : doctors.selectOne(
                Wrappers.<DoctorDO>lambdaQuery()
                    .eq(DoctorDO::getTenantId, u.tenantId())
                    .eq(DoctorDO::getId, id));
    if (d == null) throw BusinessException.missing();
    if (id != null) doctors.lock(u.tenantId(), id);
    var depIds =
        r.departmentIds() == null
            ? List.<Long>of()
            : r.departmentIds().stream().distinct().toList();
    var campusIds =
        r.campusIds() == null ? List.<Long>of() : r.campusIds().stream().distinct().toList();
    if (depIds.isEmpty() || campusIds.isEmpty() || depIds.size() > 50 || campusIds.size() > 50)
      throw BusinessException.conflict("医生需关联1至50个科室及院区");
    if (departments.selectCount(
                Wrappers.<DepartmentDO>lambdaQuery()
                    .eq(DepartmentDO::getTenantId, u.tenantId())
                    .in(DepartmentDO::getId, depIds)
                    .eq(DepartmentDO::getEnabled, true))
            != depIds.size()
        || campuses.selectCount(
                Wrappers.<CampusDO>lambdaQuery()
                    .eq(CampusDO::getTenantId, u.tenantId())
                    .in(CampusDO::getId, campusIds)
                    .eq(CampusDO::getEnabled, true))
            != campusIds.size()) throw BusinessException.conflict("院区或科室不存在、已停用或不属于当前机构");
    if (id != null) {
      var future =
          schedules.selectList(
              Wrappers.<ScheduleDO>lambdaQuery()
                  .eq(ScheduleDO::getTenantId, u.tenantId())
                  .eq(ScheduleDO::getStatus, "OPEN")
                  .eq(ScheduleDO::getDoctorId, id)
                  .gt(ScheduleDO::getEndAt, LocalDateTime.now(clock)));
      if (!future.isEmpty()
          && (!r.enabled()
              || future.stream()
                  .anyMatch(
                      s ->
                          !depIds.contains(s.getDepartmentId())
                              || !campusIds.contains(s.getCampusId()))))
        throw BusinessException.conflict("医生存在未结束排班，不能停用或移除排班关联");
    }
    d.setTenantId(u.tenantId());
    d.setName(name);
    d.setDepartmentId(depIds.getFirst());
    d.setTitle(r.title() == null ? "医师" : r.title().strip());
    d.setSpecialty(r.specialty() == null ? "" : r.specialty().strip());
    d.setEnabled(r.enabled());
    if (id == null) doctors.insert(d);
    else doctors.updateById(d);
    // 关联替换与医生更新同一事务，禁止跨机构关联和半更新状态。
    doctorDepartments.delete(
        Wrappers.<DoctorDepartmentDO>lambdaQuery()
            .eq(DoctorDepartmentDO::getTenantId, u.tenantId())
            .eq(DoctorDepartmentDO::getDoctorId, d.getId()));
    doctorCampuses.delete(
        Wrappers.<DoctorCampusDO>lambdaQuery()
            .eq(DoctorCampusDO::getTenantId, u.tenantId())
            .eq(DoctorCampusDO::getDoctorId, d.getId()));
    for (long dep : depIds) {
      var link = new DoctorDepartmentDO();
      link.setTenantId(u.tenantId());
      link.setDoctorId(d.getId());
      link.setDepartmentId(dep);
      doctorDepartments.insert(link);
    }
    for (long c : campusIds) {
      var link = new DoctorCampusDO();
      link.setTenantId(u.tenantId());
      link.setDoctorId(d.getId());
      link.setCampusId(c);
      doctorCampuses.insert(link);
    }
    return DoctorResp.from(d);
  }

  @Transactional
  public void createStaff(SessionUser u, StaffCreateReq r) {
    com.yiyue.common.PasswordPolicy.validate(r.password());
    tenants.lock(u.tenantId());
    DoctorDO doctor = null;
    if ("DOCTOR".equals(r.role())) {
      if (r.doctorId() == null) throw BusinessException.conflict("医生账号需绑定医生档案");
      doctor =
          doctors.selectOne(
              Wrappers.<DoctorDO>lambdaQuery()
                  .eq(DoctorDO::getTenantId, u.tenantId())
                  .eq(DoctorDO::getId, r.doctorId()));
      if (doctor == null || doctor.getAccountId() != null)
        throw BusinessException.conflict("医生档案不存在或已绑定账号");
      doctors.lock(u.tenantId(), doctor.getId());
    }
    var a = new AccountDO();
    a.setTenantId(u.tenantId());
    a.setUsername(r.username());
    a.setDisplayName(r.name().strip());
    a.setRole(r.role());
    a.setPasswordHash(encoder.encode(r.password()));
    accounts.insert(a);
    if (doctor != null)
      doctors.update(
          Wrappers.<DoctorDO>lambdaUpdate()
              .eq(DoctorDO::getTenantId, u.tenantId())
              .eq(DoctorDO::getId, doctor.getId())
              .set(DoctorDO::getAccountId, a.getId()));
  }
}
