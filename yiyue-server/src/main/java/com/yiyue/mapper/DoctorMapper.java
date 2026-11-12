package com.yiyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyue.domain.Doctor;
import org.apache.ibatis.annotations.Param;

public interface DoctorMapper extends BaseMapper<Doctor> {
  Long lock(@Param("tenantId") long tenantId, @Param("id") long id);
}
