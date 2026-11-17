package com.yiyue.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yiyue.auth.SessionUser;
import com.yiyue.mapper.ScheduleChangeMapper;
import com.yiyue.model.entity.ScheduleChangeDO;
import com.yiyue.service.ScheduleChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "yiyue.change.worker-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ChangeTaskRunner {
  private final ScheduleChangeMapper changes;
  private final ScheduleChangeService service;

  @Scheduled(fixedDelayString = "${yiyue.change.poll-ms:5000}")
  public void process() {
    // 每轮最多10个任务，每任务20条；任务和重试信息均在数据库，不依赖进程内队列。
    var jobs =
        changes
            .selectPage(
                new Page<>(1, 10),
                Wrappers.<ScheduleChangeDO>lambdaQuery()
                    .eq(ScheduleChangeDO::getStatus, "PROCESSING")
                    .orderByAsc(ScheduleChangeDO::getId))
            .getRecords();
    for (var c : jobs) {
      try {
        service.process(
            new SessionUser(c.getActorId(), c.getTenantId(), "排班变更任务", "ADMIN"), c.getId());
      } catch (RuntimeException ex) {
        log.warn("排班变更任务{}处理异常：{}", c.getId(), ex.getClass().getSimpleName());
      }
    }
  }
}
