package com.yiyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyue.domain.Slot;
import org.apache.ibatis.annotations.Param;

public interface SlotMapper extends BaseMapper<Slot> {
  int reserve(@Param("tenantId") long tenantId, @Param("id") long id);

  int release(@Param("tenantId") long tenantId, @Param("id") long id);
}
