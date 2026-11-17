package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("inbox_message")
public class InboxMessageDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long userId;
  private Long eventId;
  private String message;
  private LocalDateTime readAt;
  private LocalDateTime createdAt;
}
