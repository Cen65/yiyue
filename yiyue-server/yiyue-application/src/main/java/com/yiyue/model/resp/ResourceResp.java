package com.yiyue.model.resp;

import com.yiyue.model.entity.ClinicResourceDO;

public record ResourceResp(Long id, Long campusId, String kind, String name, Boolean enabled) {
  public static ResourceResp from(ClinicResourceDO r) {
    return new ResourceResp(r.getId(), r.getCampusId(), r.getKind(), r.getName(), r.getEnabled());
  }
}
