package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("payment_order")
public class PaymentOrderDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long userId;
  private Long patientId;
  private Long slotId;
  private String requestKey;
  private Integer amountCents;
  private String status;
  private LocalDateTime expiresAt;
  private Long appointmentId;
  private LocalDateTime createdAt;
}
