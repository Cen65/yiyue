package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("tenant")
public class TenantDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String code;
  private String name;
  private Boolean enabled = true;
}
