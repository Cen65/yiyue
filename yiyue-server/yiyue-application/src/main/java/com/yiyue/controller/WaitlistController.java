package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.entity.*;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.WaitlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient")
public class WaitlistController {
  private final WaitlistService service;

  @PostMapping("/waitlist")
  public WaitlistResp join(
      @AuthenticationPrincipal SessionUser u, @Valid @RequestBody WaitlistJoinReq r) {
    return service.detail(u, service.join(u, r.slotId(), r.patientId()).getId());
  }

  @GetMapping("/waitlist")
  public List<WaitlistResp> mine(@AuthenticationPrincipal SessionUser u) {
    return service.mine(u).stream().map(e -> service.detail(u, e.getId())).toList();
  }

  @PostMapping("/waitlist/{id}/accept")
  public com.yiyue.model.resp.AppointmentResp accept(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody WaitlistConsentReq r) {
    return com.yiyue.model.resp.AppointmentResp.from(service.accept(u, id));
  }

  @PostMapping("/waitlist/{id}/cancel")
  public void cancel(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    service.cancel(u, id);
  }

  @GetMapping("/inbox")
  public List<InboxResp> inbox(@AuthenticationPrincipal SessionUser u) {
    return service.inbox(u).stream().map(InboxResp::from).toList();
  }

  @PostMapping("/inbox/{id}/read")
  public void read(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    service.read(u, id);
  }
}
