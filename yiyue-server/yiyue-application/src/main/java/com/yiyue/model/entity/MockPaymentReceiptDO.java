package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mock_payment_receipt")
public class MockPaymentReceiptDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long orderId;
  private String transactionKey;
  private Integer amountCents;
  private Boolean refunded;
  private LocalDateTime createdAt;
}
