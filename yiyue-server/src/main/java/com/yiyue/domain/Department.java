package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("department")
public class Department {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String name;
}
