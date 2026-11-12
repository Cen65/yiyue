package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("doctor")
public class Doctor {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long departmentId;
  private String name;
  private String title;
  private String specialty;
}
