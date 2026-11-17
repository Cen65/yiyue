package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.entity.*;
import java.util.List;

public interface WaitlistService {
  WaitlistEntryDO join(SessionUser u, long slotId, Long patientId);

  List<WaitlistEntryDO> mine(SessionUser u);

  AppointmentDO accept(SessionUser u, long id);

  void cancel(SessionUser u, long id);

  void reconcile(long tenantId, long slotId);

  void poll();

  com.yiyue.model.resp.WaitlistResp detail(SessionUser u, long id);

  List<InboxMessageDO> inbox(SessionUser u);

  void read(SessionUser u, long id);
}
