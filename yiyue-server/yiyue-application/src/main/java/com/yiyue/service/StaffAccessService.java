package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import java.util.List;

public interface StaffAccessService {
  List<Long> doctorIds(SessionUser u);

  void checkTransition(SessionUser u, long appointmentId, String action);
}
