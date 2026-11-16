package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("appointment")
public class AppointmentDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long userId;
  private Long slotId;
  private String requestKey;
  private String status;
  private LocalDateTime createdAt;
  private Long patientId;
}
