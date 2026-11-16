package com.yiyue.triage;

import java.util.List;

/** 由业务应用提供机构科室白名单，AI 模块不依赖医疗业务数据库。 */
public interface AiDepartmentCatalog {
  List<DeepSeekClient.DepartmentOption> available(long tenantId);
}
