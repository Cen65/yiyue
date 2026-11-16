package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.resp.AppointmentPageResp;

/** 医约业务接口，事务和数据访问由实现层承担。 */
public interface AppointmentQueryService {
  AppointmentPageResp list(SessionUser user, boolean admin, long current);
}
