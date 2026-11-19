package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("clinic_schedule")
public class ScheduleDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long campusId;
  private Long doctorId;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private Long departmentId;
  private String status = "OPEN";
  private String stopReason;
  private Integer feeCents = 0;
}
