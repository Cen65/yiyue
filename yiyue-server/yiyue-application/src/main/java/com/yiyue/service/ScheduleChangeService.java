package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import java.util.List;

public interface ScheduleChangeService {
  ScheduleChangeResp create(SessionUser u, long scheduleId, ScheduleChangeReq r);

  List<ScheduleChangeResp> list(SessionUser u);

  List<ChangeItemResp> items(SessionUser u, long id);

  void process(SessionUser u, long id);

  void retry(SessionUser u, long id);

  void propose(SessionUser u, long itemId, long targetScheduleId);

  List<ChangeItemResp> mine(SessionUser u);

  AppointmentResp accept(SessionUser u, long itemId, long slotId);

  void decline(SessionUser u, long itemId);
}
