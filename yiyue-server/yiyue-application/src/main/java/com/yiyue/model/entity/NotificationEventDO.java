package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("notification_event")
public class NotificationEventDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long userId;
  private String eventKey;
  private String message;
  private Boolean delivered;
  private LocalDateTime createdAt;
}
