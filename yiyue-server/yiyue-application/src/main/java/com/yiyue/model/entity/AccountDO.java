package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("app_user")
public class AccountDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private String username;
  private String passwordHash;
  private String displayName;
  private String role;
  private Boolean enabled = true;
}
