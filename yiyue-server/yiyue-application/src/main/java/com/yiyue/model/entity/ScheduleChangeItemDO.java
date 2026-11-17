package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("schedule_change_item")
public class ScheduleChangeItemDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long changeId;
  private Long appointmentId;
  private Long targetScheduleId;
  private Long newAppointmentId;
  private String status;
  private String decision;
  private Integer attempts;
  private String lastError;
}
