package com.yiyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyue.model.entity.DoctorDO;
import org.apache.ibatis.annotations.Param;

public interface DoctorMapper extends BaseMapper<DoctorDO> {
  Long lock(@Param("tenantId") long tenantId, @Param("id") long id);
}
