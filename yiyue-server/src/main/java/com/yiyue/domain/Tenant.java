package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("tenant")
public class Tenant {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String code;
  private String name;
}
