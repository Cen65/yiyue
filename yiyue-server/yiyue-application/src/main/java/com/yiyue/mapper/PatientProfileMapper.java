package com.yiyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyue.model.entity.PatientProfileDO;

public interface PatientProfileMapper extends BaseMapper<PatientProfileDO> {
  Long lock(
      @org.apache.ibatis.annotations.Param("tenantId") long tenantId,
      @org.apache.ibatis.annotations.Param("ownerId") long ownerId,
      @org.apache.ibatis.annotations.Param("id") long id);
}
