package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("department")
public class DepartmentDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String name;
  private Boolean enabled = true;
}
