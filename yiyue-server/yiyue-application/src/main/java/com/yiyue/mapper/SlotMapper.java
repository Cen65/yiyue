package com.yiyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyue.model.entity.SlotDO;
import org.apache.ibatis.annotations.Param;

public interface SlotMapper extends BaseMapper<SlotDO> {
  int reserve(@Param("tenantId") long tenantId, @Param("id") long id);

  int release(@Param("tenantId") long tenantId, @Param("id") long id);
}
