package com.yiyue.model.resp;

import com.yiyue.model.entity.CampusDO;

public record CampusResp(Long id, Long tenantId, String name, String address, Boolean enabled) {
  public static CampusResp from(CampusDO data) {
    return new CampusResp(
        data.getId(), data.getTenantId(), data.getName(), data.getAddress(), data.getEnabled());
  }
}
