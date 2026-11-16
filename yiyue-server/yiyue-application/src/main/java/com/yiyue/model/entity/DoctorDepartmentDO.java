package com.yiyue.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("doctor_department")
public class DoctorDepartmentDO {
  @TableId(type = IdType.AUTO)
  private Long id;

  private Long tenantId;
  private Long doctorId;
  private Long departmentId;
}
