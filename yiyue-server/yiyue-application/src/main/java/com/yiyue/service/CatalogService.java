package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.entity.ScheduleDO;
import com.yiyue.model.entity.SlotDO;
import com.yiyue.model.resp.CatalogResp;
import java.util.*;

public interface CatalogService {
  com.yiyue.model.resp.SchedulePageResp page(
      SessionUser user, com.yiyue.model.query.ScheduleQuery query);

  CatalogResp catalog(SessionUser user);

  List<ScheduleDO> schedules(SessionUser user);

  List<SlotDO> slots(SessionUser user, long scheduleId);
}
