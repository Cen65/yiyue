package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("schedule_change")
public class ScheduleChangeDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long scheduleId;
  private Long replacementScheduleId;
  private String kind;
  private String reason;
  private String status;
  private Long actorId;
  private LocalDateTime createdAt;
}
