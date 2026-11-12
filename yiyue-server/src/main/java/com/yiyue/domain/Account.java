package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("app_user")
public class Account {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String username;
  private String passwordHash;
  private String displayName;
  private String role;
}
