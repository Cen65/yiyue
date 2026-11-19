package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.entity.ScheduleDO;
import com.yiyue.model.req.ResourceSaveReq;
import com.yiyue.model.resp.ResourceResp;
import java.util.*;

public interface ResourceService {
  List<ResourceResp> list(SessionUser u);

  ResourceResp save(SessionUser u, Long id, ResourceSaveReq r);

  void reserve(SessionUser u, ScheduleDO schedule, List<Long> ids);

  void release(long tenant, long schedule);

  List<Long> ids(long tenant, long schedule);

  List<ResourceResp> assigned(SessionUser u, long schedule);
}
