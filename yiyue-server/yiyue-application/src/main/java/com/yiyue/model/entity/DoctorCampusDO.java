package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("doctor_campus")
public class DoctorCampusDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long doctorId;
  private Long campusId;
}
