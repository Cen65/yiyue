package com.yiyue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yiyue.model.entity.ScheduleChangeItemDO;

public interface ScheduleChangeItemMapper extends BaseMapper<ScheduleChangeItemDO> {
  com.baomidou.mybatisplus.core.metadata.IPage<ScheduleChangeItemDO> selectOwnedPage(
      com.baomidou.mybatisplus.extension.plugins.pagination.Page<ScheduleChangeItemDO> page,
      @org.apache.ibatis.annotations.Param("tenantId") long tenantId,
      @org.apache.ibatis.annotations.Param("userId") long userId);
}
