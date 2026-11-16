package com.yiyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyue.model.entity.TenantDO;

public interface TenantMapper extends BaseMapper<TenantDO> {
  Long lock(@org.apache.ibatis.annotations.Param("id") long id);
}
