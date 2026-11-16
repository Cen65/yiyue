package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.resp.AppointmentPageResp;
import com.yiyue.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/appointments")
@RequiredArgsConstructor
public class StaffController {
  private final AppointmentQueryService queries;
  private final BookingService bookings;

  @GetMapping
  AppointmentPageResp list(
      @AuthenticationPrincipal SessionUser u, @RequestParam(defaultValue = "1") long page) {
    return queries.list(u, true, page);
  }

  @PostMapping("/{id}/{action}")
  void transition(
      @AuthenticationPrincipal SessionUser u, @PathVariable long id, @PathVariable String action) {
    bookings.transition(u, id, action);
  }
}
