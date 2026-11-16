package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.entity.AppointmentDO;

/** 医约业务接口，事务和数据访问由实现层承担。 */
public interface BookingService {
  AppointmentDO book(SessionUser user, long slotId, String key);

  AppointmentDO book(SessionUser user, long slotId, String key, Long patientId);

  void cancel(SessionUser user, long id);

  void transition(SessionUser user, long id, String action);
}
