package com.yiyue.model.resp;

import com.yiyue.model.entity.DoctorCampusDO;

public record DoctorCampusResp(Long id, Long tenantId, Long doctorId, Long campusId) {
  public static DoctorCampusResp from(DoctorCampusDO data) {
    return new DoctorCampusResp(
        data.getId(), data.getTenantId(), data.getDoctorId(), data.getCampusId());
  }
}
