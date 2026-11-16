package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("patient_profile")
public class PatientProfileDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long ownerId;
  private String name;
  private String relationship;
  private Boolean authorized;
  private Boolean selfProfile;
}
