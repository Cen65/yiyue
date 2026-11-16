package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.LoginReq;

public interface AuthService {
  SessionUser authenticate(LoginReq input);
}
