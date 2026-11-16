package com.yiyue.model.resp;

import com.yiyue.model.entity.PatientProfileDO;

public record PatientProfileResp(
    Long id,
    Long tenantId,
    Long ownerId,
    String name,
    String relationship,
    Boolean authorized,
    Boolean selfProfile) {
  public static PatientProfileResp from(PatientProfileDO data) {
    return new PatientProfileResp(
        data.getId(),
        data.getTenantId(),
        data.getOwnerId(),
        data.getName(),
        data.getRelationship(),
        data.getAuthorized(),
        data.getSelfProfile());
  }
}
