package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.*;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.ResourceService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {
  private final ClinicResourceMapper resources;
  private final ScheduleResourceMapper reservations;
  private final TenantMapper tenants;
  private final CampusMapper campuses;
  private final ScheduleMapper schedules;
  private final AuditEventMapper audits;
  private final Clock clock;

  public List<ResourceResp> list(SessionUser u) {
    return resources
        .selectList(
            Wrappers.<ClinicResourceDO>lambdaQuery()
                .eq(ClinicResourceDO::getTenantId, u.tenantId())
                .orderByAsc(ClinicResourceDO::getId))
        .stream()
        .map(ResourceResp::from)
        .toList();
  }

  @Transactional
  public ResourceResp save(SessionUser u, Long id, ResourceSaveReq r) {
    if (!"ADMIN".equals(u.role())) throw new BusinessException(HttpStatus.FORBIDDEN, "仅管理员可维护资源");
    tenants.lock(u.tenantId());
    if (!List.of("ROOM", "EQUIPMENT").contains(r.kind()) || r.name().isBlank())
      throw BusinessException.conflict("资源类型或名称无效");
    if (!campuses.exists(
        Wrappers.<CampusDO>lambdaQuery()
            .eq(CampusDO::getTenantId, u.tenantId())
            .eq(CampusDO::getId, r.campusId())
            .eq(CampusDO::getEnabled, true))) throw BusinessException.conflict("院区不存在或已停用");
    var resource =
        id == null
            ? new ClinicResourceDO()
            : resources.selectOne(
                Wrappers.<ClinicResourceDO>lambdaQuery()
                    .eq(ClinicResourceDO::getTenantId, u.tenantId())
                    .eq(ClinicResourceDO::getId, id));
    if (resource == null) throw BusinessException.missing();
    // 有未来或正在进行的占用时不改变档案语义，防止排班引用失真。
    if (id != null
        && reservations.exists(
            Wrappers.<ScheduleResourceDO>lambdaQuery()
                .eq(ScheduleResourceDO::getTenantId, u.tenantId())
                .eq(ScheduleResourceDO::getResourceId, id)
                .eq(ScheduleResourceDO::getStatus, "ACTIVE")
                .gt(ScheduleResourceDO::getEndAt, LocalDateTime.now(clock))))
      throw BusinessException.conflict("资源有有效排班占用，请先处理排班");
    resource.setTenantId(u.tenantId());
    resource.setCampusId(r.campusId());
    resource.setKind(r.kind());
    resource.setName(r.name().strip());
    resource.setEnabled(r.enabled());
    if (id == null) resources.insert(resource);
    else resources.updateById(resource);
    var audit = new AuditEventDO();
    audit.setTenantId(u.tenantId());
    audit.setActorId(u.id());
    audit.setAction("RESOURCE_SAVE");
    audit.setTargetId(resource.getId());
    audit.setCreatedAt(LocalDateTime.now(clock));
    audits.insert(audit);
    return ResourceResp.from(resource);
  }

  @Transactional
  public void reserve(SessionUser u, ScheduleDO schedule, List<Long> ids) {
    tenants.lock(u.tenantId());
    if (ids == null || ids.isEmpty()) return;
    if (ids.size() > 20
        || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size())
      throw BusinessException.conflict("资源最多20个且不能重复");
    // 所有写入共用机构锁；按ID排序，医生校验及资源占用处于原排班事务内。
    for (var id : ids.stream().sorted().toList()) {
      var resource =
          resources.selectOne(
              Wrappers.<ClinicResourceDO>lambdaQuery()
                  .eq(ClinicResourceDO::getTenantId, u.tenantId())
                  .eq(ClinicResourceDO::getId, id)
                  .eq(ClinicResourceDO::getCampusId, schedule.getCampusId())
                  .eq(ClinicResourceDO::getEnabled, true));
      if (resource == null) throw BusinessException.conflict("资源不存在、已停用或不属于该院区");
      if (reservations.exists(
          Wrappers.<ScheduleResourceDO>lambdaQuery()
              .eq(ScheduleResourceDO::getTenantId, u.tenantId())
              .eq(ScheduleResourceDO::getResourceId, id)
              .eq(ScheduleResourceDO::getStatus, "ACTIVE")
              .lt(ScheduleResourceDO::getStartAt, schedule.getEndAt())
              .gt(ScheduleResourceDO::getEndAt, schedule.getStartAt())))
        throw BusinessException.conflict("资源已被其他排班占用：" + resource.getName());
      var row = new ScheduleResourceDO();
      row.setTenantId(u.tenantId());
      row.setResourceId(id);
      row.setScheduleId(schedule.getId());
      row.setStartAt(schedule.getStartAt());
      row.setEndAt(schedule.getEndAt());
      row.setStatus("ACTIVE");
      reservations.insert(row);
    }
  }

  @Transactional
  public void release(long tenant, long schedule) {
    tenants.lock(tenant);
    reservations.update(
        Wrappers.<ScheduleResourceDO>lambdaUpdate()
            .eq(ScheduleResourceDO::getTenantId, tenant)
            .eq(ScheduleResourceDO::getScheduleId, schedule)
            .eq(ScheduleResourceDO::getStatus, "ACTIVE")
            .set(ScheduleResourceDO::getStatus, "RELEASED"));
  }

  public List<Long> ids(long tenant, long schedule) {
    return reservations
        .selectList(
            Wrappers.<ScheduleResourceDO>lambdaQuery()
                .eq(ScheduleResourceDO::getTenantId, tenant)
                .eq(ScheduleResourceDO::getScheduleId, schedule)
                .orderByAsc(ScheduleResourceDO::getResourceId))
        .stream()
        .map(ScheduleResourceDO::getResourceId)
        .toList();
  }

  public List<ResourceResp> assigned(SessionUser u, long schedule) {
    if (!schedules.exists(
        Wrappers.<ScheduleDO>lambdaQuery()
            .eq(ScheduleDO::getTenantId, u.tenantId())
            .eq(ScheduleDO::getId, schedule))) throw BusinessException.missing();
    var ids = ids(u.tenantId(), schedule);
    if (ids.isEmpty()) return List.of();
    return resources
        .selectList(
            Wrappers.<ClinicResourceDO>lambdaQuery()
                .eq(ClinicResourceDO::getTenantId, u.tenantId())
                .in(ClinicResourceDO::getId, ids))
        .stream()
        .map(ResourceResp::from)
        .toList();
  }
}
