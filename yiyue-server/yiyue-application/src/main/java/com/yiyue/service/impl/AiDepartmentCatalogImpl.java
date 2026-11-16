package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.mapper.DepartmentMapper;
import com.yiyue.model.entity.DepartmentDO;
import com.yiyue.triage.AiDepartmentCatalog;
import com.yiyue.triage.DeepSeekClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiDepartmentCatalogImpl implements AiDepartmentCatalog {
  private final DepartmentMapper departments;

  public List<DeepSeekClient.DepartmentOption> available(long tenantId) {
    return departments
        .selectList(
            Wrappers.<DepartmentDO>lambdaQuery()
                .eq(DepartmentDO::getTenantId, tenantId)
                .eq(DepartmentDO::getEnabled, true))
        .stream()
        .map(d -> new DeepSeekClient.DepartmentOption(d.getId(), d.getName()))
        .toList();
  }
}
