package com.yiyue.model.resp;

import com.yiyue.model.entity.DoctorDO;

public record DoctorResp(
    Long id,
    Long tenantId,
    Long departmentId,
    String name,
    String title,
    String specialty,
    Boolean enabled,
    Long accountId) {
  public static DoctorResp from(DoctorDO data) {
    return new DoctorResp(
        data.getId(),
        data.getTenantId(),
        data.getDepartmentId(),
        data.getName(),
        data.getTitle(),
        data.getSpecialty(),
        data.getEnabled(),
        data.getAccountId());
  }
}
