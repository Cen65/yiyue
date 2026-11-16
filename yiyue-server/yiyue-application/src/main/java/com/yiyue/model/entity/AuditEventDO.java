package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("audit_event")
public class AuditEventDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long actorId;
  private String action;
  private Long targetId;
  private LocalDateTime createdAt;
}
