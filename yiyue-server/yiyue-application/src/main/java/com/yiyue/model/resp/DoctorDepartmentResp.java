package com.yiyue.model.resp;

import com.yiyue.model.entity.DoctorDepartmentDO;

public record DoctorDepartmentResp(Long id, Long tenantId, Long doctorId, Long departmentId) {
  public static DoctorDepartmentResp from(DoctorDepartmentDO data) {
    return new DoctorDepartmentResp(
        data.getId(), data.getTenantId(), data.getDoctorId(), data.getDepartmentId());
  }
}
