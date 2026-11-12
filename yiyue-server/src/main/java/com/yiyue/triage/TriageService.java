package com.yiyue.triage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.domain.Department;
import com.yiyue.mapper.DepartmentMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TriageService {
  private final DepartmentMapper departments;
  private final DeepSeekClient client;
  private final TriageRateLimiter limiter;
  private final TriageProperties properties;
  private static final Set<String> URGENCIES =
      Set.of("EMERGENCY", "PROMPT", "UNDETERMINED", "ROUTINE");
  private static final Map<String, String> QUESTIONS =
      Map.of(
          "DURATION",
          "症状出现多久了？是突然出现还是逐渐加重？",
          "SEVERITY",
          "不适程度如何？是否影响呼吸、意识或日常活动？",
          "AGE_GROUP",
          "就诊人属于儿童、成年人还是老年人？",
          "LOCATION",
          "主要不适在身体什么部位？",
          "ASSOCIATED_SYMPTOMS",
          "还有哪些同时出现的不适？");

  public record Recommendation(long id, String name) {}

  public record Result(
      String urgency, List<Recommendation> departments, List<String> questions, String message) {}

  public boolean available() {
    return properties.isAvailable();
  }

  public Result recommend(SessionUser user, String symptoms) {
    if (!available())
      throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "智能导诊尚未启用，请直接选择科室或咨询人工导诊");
    var options =
        departments
            .selectList(
                Wrappers.<Department>lambdaQuery().eq(Department::getTenantId, user.tenantId()))
            .stream()
            .map(d -> new DeepSeekClient.DepartmentOption(d.getId(), d.getName()))
            .toList();
    if (options.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT, "本机构尚未配置科室，请咨询人工导诊");
    limiter.acquire(user);
    return validate(client.decide(symptoms.strip(), options), options);
  }

  Result validate(JsonNode result, List<DeepSeekClient.DepartmentOption> options) {
    String urgency = result.path("urgency").asText();
    JsonNode ids = result.path("departmentIds"), questions = result.path("questions");
    if (!URGENCIES.contains(urgency)
        || !ids.isArray()
        || ids.size() > 3
        || !questions.isArray()
        || questions.size() > 3) throw invalid();
    Map<Long, String> allowed = new HashMap<>();
    options.forEach(d -> allowed.put(d.id(), d.name()));
    List<Recommendation> recommendations = new ArrayList<>();
    Set<Long> seen = new HashSet<>();
    for (var id : ids) {
      // 模型输出是不可信外部输入；拒绝虚构科室、跨机构ID和错误类型。
      if (!id.isIntegralNumber() || !id.canConvertToLong() || !allowed.containsKey(id.longValue()))
        throw invalid();
      if (seen.add(id.longValue()))
        recommendations.add(new Recommendation(id.longValue(), allowed.get(id.longValue())));
    }
    List<String> followUps = new ArrayList<>();
    for (var question : questions) {
      if (!question.isTextual() || !QUESTIONS.containsKey(question.asText())) throw invalid();
      String text = QUESTIONS.get(question.asText());
      if (!followUps.contains(text)) followUps.add(text);
    }
    // 紧急/信息不足时不展示预约入口，也不把模型自由文本直接交给患者。
    if ("EMERGENCY".equals(urgency))
      return new Result(urgency, List.of(), List.of(), "可能需要紧急医疗帮助。请立即联系当地急救服务或前往急诊，不要等待在线导诊或挂号。");
    if ("UNDETERMINED".equals(urgency))
      return new Result(urgency, List.of(), followUps, "目前信息不足，暂不能匹配科室。请补充描述或咨询人工导诊。");
    String message =
        recommendations.isEmpty()
            ? "当前机构未匹配到合适科室，请咨询人工导诊，不要因等待匹配而延误就医。"
            : "PROMPT".equals(urgency)
                ? "建议及时由医护人员线下评估。以下为本机构科室匹配参考，请勿因预约等待延误就医。"
                : "以下仅为科室匹配参考，不代表诊断，也不能排除急症；不适持续或加重时请及时线下就医。";
    return new Result(urgency, recommendations, followUps, message);
  }

  private BusinessException invalid() {
    return new BusinessException(HttpStatus.BAD_GATEWAY, "无法核实导诊结果，请咨询人工导诊");
  }
}
