package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.entity.ScheduleDO;
import com.yiyue.model.req.ScheduleCreateReq;

/** 医约业务接口，事务和数据访问由实现层承担。 */
public interface ScheduleService {
  ScheduleDO create(SessionUser user, ScheduleCreateReq input);
}
