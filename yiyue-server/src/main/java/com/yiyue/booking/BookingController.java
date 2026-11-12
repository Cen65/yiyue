package com.yiyue.booking;

import com.yiyue.auth.SessionUser;
import com.yiyue.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookingController {
  private final BookingService service;
  private final AppointmentQueryService queries;

  public record Booking(
      @NotNull @Positive Long slotId,
      @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String requestKey) {}

  @PostMapping("/api/patient/appointments")
  Appointment book(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody Booking input) {
    return service.book(user, input.slotId(), input.requestKey());
  }

  @PostMapping("/api/patient/appointments/{id}/cancel")
  void cancel(@AuthenticationPrincipal SessionUser user, @PathVariable long id) {
    service.cancel(user, id);
  }

  @GetMapping("/api/patient/appointments")
  AppointmentQueryService.Result mine(
      @AuthenticationPrincipal SessionUser user, @RequestParam(defaultValue = "1") long page) {
    return queries.list(user, false, page);
  }

  @GetMapping("/api/admin/appointments")
  AppointmentQueryService.Result all(
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
