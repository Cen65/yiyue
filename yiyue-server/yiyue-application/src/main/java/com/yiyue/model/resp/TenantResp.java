package com.yiyue.model.resp;

import com.yiyue.model.entity.TenantDO;

public record TenantResp(Long id, String code, String name, Boolean enabled) {
  public static TenantResp from(TenantDO data) {
    return new TenantResp(data.getId(), data.getCode(), data.getName(), data.getEnabled());
  }
}
