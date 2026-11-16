package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import com.yiyue.service.OperationsService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class OperationsController {
  private final OperationsService service;

  @GetMapping
  public Map<String, Object> overview(@AuthenticationPrincipal SessionUser u) {
    return service.overview(u);
  }

  @PostMapping("/{kind}")
  public Object create(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable String kind,
      @Valid @RequestBody OperationsReq r) {
    return service.save(u, kind, null, r);
  }

  @PutMapping("/{kind}/{id}")
  public Object update(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable String kind,
      @PathVariable long id,
      @Valid @RequestBody OperationsReq r) {
    return service.save(u, kind, id, r);
  }

  @PostMapping("/staff")
  public void staff(@AuthenticationPrincipal SessionUser u, @Valid @RequestBody StaffCreateReq r) {
    service.createStaff(u, r);
  }
}
