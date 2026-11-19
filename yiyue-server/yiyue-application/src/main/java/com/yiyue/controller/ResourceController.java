package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.*;
import com.yiyue.service.ResourceService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ResourceController {
  private final ResourceService service;

  @GetMapping("/api/resources")
  public List<ResourceResp> list(@AuthenticationPrincipal SessionUser u) {
    return service.list(u);
  }

  @PostMapping("/api/admin/resources")
  public ResourceResp create(
      @AuthenticationPrincipal SessionUser u, @Valid @RequestBody ResourceSaveReq r) {
    return service.save(u, null, r);
  }

  @PutMapping("/api/admin/resources/{id}")
  public ResourceResp update(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody ResourceSaveReq r) {
    return service.save(u, id, r);
  }

  @GetMapping("/api/schedules/{id}/resources")
  public List<ResourceResp> assigned(
      @AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    return service.assigned(u, id);
  }
}
