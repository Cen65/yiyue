package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("campus")
public class CampusDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String name;
  private String address;
  private Boolean enabled = true;
}
