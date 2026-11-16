package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.ScheduleCreateReq;
import com.yiyue.model.resp.CatalogResp;
import com.yiyue.model.resp.ScheduleResp;
import com.yiyue.model.resp.SlotResp;
import com.yiyue.service.CatalogService;
import com.yiyue.service.ScheduleService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CatalogController {
  private final CatalogService catalogService;
  private final ScheduleService scheduleService;

  @GetMapping("/api/catalog")
  CatalogResp catalog(@AuthenticationPrincipal SessionUser user) {
    return catalogService.catalog(user);
  }

  @GetMapping("/api/schedules")
  List<ScheduleResp> schedules(@AuthenticationPrincipal SessionUser user) {
    return catalogService.schedules(user).stream().map(ScheduleResp::from).toList();
  }

  @GetMapping("/api/slots")
  List<SlotResp> slots(@AuthenticationPrincipal SessionUser user, @RequestParam long scheduleId) {
    return catalogService.slots(user, scheduleId).stream().map(SlotResp::from).toList();
  }

  @PostMapping("/api/admin/schedules")
  ScheduleResp create(
      @AuthenticationPrincipal SessionUser user, @Valid @RequestBody ScheduleCreateReq input) {
    return ScheduleResp.from(scheduleService.create(user, input));
  }

  @GetMapping("/api/schedules/page")
  com.yiyue.model.resp.SchedulePageResp page(
      @AuthenticationPrincipal SessionUser user,
      @Valid @ModelAttribute com.yiyue.model.query.ScheduleQuery query) {
    return catalogService.page(user, query);
  }
}
