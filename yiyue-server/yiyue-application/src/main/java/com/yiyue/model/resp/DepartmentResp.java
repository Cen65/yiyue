package com.yiyue.model.resp;

import com.yiyue.model.entity.DepartmentDO;

public record DepartmentResp(Long id, Long tenantId, String name, Boolean enabled) {
  public static DepartmentResp from(DepartmentDO data) {
    return new DepartmentResp(data.getId(), data.getTenantId(), data.getName(), data.getEnabled());
  }
}
