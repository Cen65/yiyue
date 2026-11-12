package com.yiyue.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("appointment")
public class Appointment {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long userId;
  private Long slotId;
  private String requestKey;
  private String status;
  private LocalDateTime createdAt;
}
