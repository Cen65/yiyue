package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("appointment_slot")
public class Slot {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long scheduleId;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private Integer capacity;
  private Integer reserved;
}
