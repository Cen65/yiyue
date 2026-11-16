package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.BookingReq;
import com.yiyue.model.resp.AppointmentPageResp;
import com.yiyue.model.resp.AppointmentResp;
import com.yiyue.service.AppointmentQueryService;
import com.yiyue.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookingController {
  private final BookingService service;
  private final AppointmentQueryService queries;

  @PostMapping("/api/patient/appointments")
  AppointmentResp book(
      @AuthenticationPrincipal SessionUser user, @Valid @RequestBody BookingReq input) {
    return AppointmentResp.from(
        service.book(user, input.slotId(), input.requestKey(), input.patientId()));
  }

  @PostMapping("/api/patient/appointments/{id}/cancel")
  void cancel(@AuthenticationPrincipal SessionUser user, @PathVariable long id) {
    service.cancel(user, id);
  }

  @GetMapping("/api/patient/appointments")
  AppointmentPageResp mine(
      @AuthenticationPrincipal SessionUser user, @RequestParam(defaultValue = "1") long page) {
    return queries.list(user, false, page);
  }

  @GetMapping("/api/admin/appointments")
  AppointmentPageResp all(
      @AuthenticationPrincipal SessionUser user, @RequestParam(defaultValue = "1") long page) {
    return queries.list(user, true, page);
  }

  @PostMapping("/api/admin/appointments/{id}/{action}")
  void transition(
      @AuthenticationPrincipal SessionUser user,
      @PathVariable long id,
      @PathVariable String action) {
    service.transition(user, id, action);
  }
}
