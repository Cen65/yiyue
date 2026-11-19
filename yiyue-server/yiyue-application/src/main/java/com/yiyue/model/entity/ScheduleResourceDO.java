package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("schedule_resource")
public class ScheduleResourceDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long resourceId;
  private Long scheduleId;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private String status;
}
