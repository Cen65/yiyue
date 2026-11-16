package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.*;
import com.yiyue.model.resp.TenantResp;
import com.yiyue.service.PlatformService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class PlatformController {
  private final PlatformService service;

  @GetMapping
  List<TenantResp> list(@AuthenticationPrincipal SessionUser u) {
    return service.list(u);
  }

  @PostMapping
  TenantResp create(@AuthenticationPrincipal SessionUser u, @Valid @RequestBody TenantCreateReq r) {
    return service.create(u, r);
  }

  @PutMapping("/{id}")
  TenantResp update(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody TenantUpdateReq r) {
    return service.update(u, id, r);
  }
}
