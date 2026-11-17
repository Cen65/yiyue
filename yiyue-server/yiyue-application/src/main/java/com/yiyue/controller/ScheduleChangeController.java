package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.ScheduleChangeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScheduleChangeController {
  private final ScheduleChangeService service;

  @PostMapping("/api/admin/schedules/{id}/change")
  ScheduleChangeResp create(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody ScheduleChangeReq r) {
    return service.create(u, id, r);
  }

  @GetMapping("/api/admin/changes")
  List<ScheduleChangeResp> list(@AuthenticationPrincipal SessionUser u) {
    return service.list(u);
  }

  @GetMapping("/api/admin/changes/{id}/items")
  List<ChangeItemResp> items(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    return service.items(u, id);
  }

  @PostMapping("/api/admin/changes/{id}/process")
  void process(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    service.process(u, id);
  }

  @PostMapping("/api/admin/changes/{id}/retry")
  void retry(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    service.retry(u, id);
  }

  @PostMapping("/api/admin/changes/items/{id}/proposal")
  void propose(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody ChangeProposalReq r) {
    service.propose(u, id, r.scheduleId());
  }

  @GetMapping("/api/patient/changes")
  List<ChangeItemResp> mine(@AuthenticationPrincipal SessionUser u) {
    return service.mine(u);
  }

  @PostMapping("/api/patient/changes/{id}/accept")
  AppointmentResp accept(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody ChangeAcceptReq r) {
    return service.accept(u, id, r.slotId());
  }

  @PostMapping("/api/patient/changes/{id}/decline")
  void decline(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    service.decline(u, id);
  }
}
