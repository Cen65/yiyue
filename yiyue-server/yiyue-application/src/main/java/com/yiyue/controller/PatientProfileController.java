package com.yiyue.controller;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.PatientProfileReq;
import com.yiyue.model.resp.PatientProfileResp;
import com.yiyue.service.PatientProfileService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patient/profiles")
@RequiredArgsConstructor
public class PatientProfileController {
  private final PatientProfileService service;

  @GetMapping
  List<PatientProfileResp> list(@AuthenticationPrincipal SessionUser u) {
    return service.list(u).stream().map(PatientProfileResp::from).toList();
  }

  @PostMapping
  PatientProfileResp create(
      @AuthenticationPrincipal SessionUser u, @Valid @RequestBody PatientProfileReq r) {
    return PatientProfileResp.from(service.save(u, null, r));
  }

  @PutMapping("/{id}")
  PatientProfileResp update(
      @AuthenticationPrincipal SessionUser u,
      @PathVariable long id,
      @Valid @RequestBody PatientProfileReq r) {
    return PatientProfileResp.from(service.save(u, id, r));
  }

  @PostMapping("/{id}/revoke")
  void revoke(@AuthenticationPrincipal SessionUser u, @PathVariable long id) {
    service.revoke(u, id);
  }
}
