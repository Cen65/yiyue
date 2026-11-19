package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("clinic_resource")
public class ClinicResourceDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long campusId;
  private String kind;
  private String name;
  private Boolean enabled;
}
