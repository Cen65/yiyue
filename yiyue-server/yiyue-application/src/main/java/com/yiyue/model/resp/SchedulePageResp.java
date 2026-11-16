package com.yiyue.model.resp;

import java.util.List;

public record SchedulePageResp(List<ScheduleResp> records, long total, long current, long size) {}
