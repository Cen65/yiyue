package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.TenantResp;
import java.util.List;

public interface PlatformService {
  List<TenantResp> list(SessionUser u);

  TenantResp create(SessionUser u, TenantCreateReq r);

  TenantResp update(SessionUser u, long id, TenantUpdateReq r);
}
