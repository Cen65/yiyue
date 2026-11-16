package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.entity.PatientProfileDO;
import com.yiyue.model.req.PatientProfileReq;
import java.util.List;

public interface PatientProfileService {
  List<PatientProfileDO> list(SessionUser user);

  PatientProfileDO save(SessionUser user, Long id, PatientProfileReq req);

  PatientProfileDO requireOwned(SessionUser user, Long id);

  void revoke(SessionUser user, long id);
}
