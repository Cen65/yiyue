package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import java.util.Map;

public interface OperationsService {
  Map<String, Object> overview(SessionUser user);

  Object save(SessionUser user, String kind, Long id, OperationsReq req);

  void createStaff(SessionUser user, StaffCreateReq req);
}
