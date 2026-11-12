package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("campus")
public class Campus {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String name;
  private String address;
}
