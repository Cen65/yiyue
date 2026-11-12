package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("clinic_schedule")
public class Schedule {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long campusId;
  private Long doctorId;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
}
