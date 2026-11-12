package com.yiyue.triage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.domain.Department;
import com.yiyue.mapper.DepartmentMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;

class TriageServiceTest {
  final ObjectMapper json = new ObjectMapper();
  final DepartmentMapper departments = mock(DepartmentMapper.class);
  final DeepSeekClient client = mock(DeepSeekClient.class);
  final TriageRateLimiter limiter = mock(TriageRateLimiter.class);
  final TriageProperties properties = new TriageProperties();
  final TriageService service = new TriageService(departments, client, limiter, properties);
  final SessionUser user = new SessionUser(7, 2, "测试就诊人", "PATIENT");
  final List<DeepSeekClient.DepartmentOption> options =
      List.of(new DeepSeekClient.DepartmentOption(10, "全科门诊"));

  @Test
  void acceptsOnlyKnownDepartmentAndUsesServerOwnedNames() throws Exception {
    var result =
        service.validate(
            json.readTree(
                """
            {"urgency":"ROUTINE","departmentIds":[10,10],"questions":["DURATION"]}
            """),
            options);
    assertThat(result.departments()).containsExactly(new TriageService.Recommendation(10, "全科门诊"));
    assertThat(result.questions()).containsExactly("症状出现多久了？是突然出现还是逐渐加重？");
  }

  @Test
  void rejectsInventedOrOtherTenantDepartment() throws Exception {
    var response =
        json.readTree("{\"urgency\":\"ROUTINE\",\"departmentIds\":[999],\"questions\":[]}");
    assertThatThrownBy(() -> service.validate(response, options))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void emergencyAndUncertainDoNotOfferBooking() throws Exception {
    for (String urgency : List.of("EMERGENCY", "UNDETERMINED")) {
      var result =
          service.validate(
              json.readTree(
                  "{\"urgency\":\""
                      + urgency
                      + "\",\"departmentIds\":[10],\"questions\":[\"DURATION\"]}"),
              options);
      assertThat(result.departments()).isEmpty();
      if (urgency.equals("EMERGENCY")) {
        assertThat(result.questions()).isEmpty();
        assertThat(result.message()).contains("不要等待");
      }
    }
  }

  @Test
  void rejectsUntrustedFreeTextAndInvalidTypes() throws Exception {
    for (String body :
        List.of(
            "{\"urgency\":\"SAFE\",\"departmentIds\":[],\"questions\":[]}",
            "{\"urgency\":\"ROUTINE\",\"departmentIds\":[\"10\"],\"questions\":[]}",
            "{\"urgency\":\"ROUTINE\",\"departmentIds\":[],\"questions\":[\"take-medicine\"]}",
            "{\"urgency\":\"ROUTINE\",\"departmentIds\":[],\"questions\":[\"DURATION\",\"SEVERITY\",\"LOCATION\",\"AGE_GROUP\"]}")) {
      var response = json.readTree(body);
      assertThatThrownBy(() -> service.validate(response, options))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Test
  void disabledDoesNotCallProviderOrRedis() {
    assertThatThrownBy(() -> service.recommend(user, "测试症状描述"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    verifyNoInteractions(client, limiter, departments);
  }

  @Test
  void tenantCatalogIsPassedToProviderAndRateLimitIsEnforced() throws Exception {
    properties.setEnabled(true);
    properties.setApiKey("unit-test-placeholder");
    Department department = new Department();
    department.setId(10L);
    department.setTenantId(2L);
    department.setName("全科门诊");
    when(departments.selectList(any()))
        .thenAnswer(
            invocation -> {
              com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Department> query =
                  invocation.getArgument(0);
              assertThat(query.getSqlSegment()).contains("tenant_id");
              assertThat(query.getParamNameValuePairs()).containsValue(2L);
              return List.of(department);
            });
    when(client.decide("测试症状描述", options))
        .thenReturn(
            json.readTree("{\"urgency\":\"ROUTINE\",\"departmentIds\":[10],\"questions\":[]}"));
    // 初始化 Lambda 元数据与数据库无关，仅供独立 Mapper mock 测试使用。
    var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
        new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""), Department.class);
    assertThat(service.recommend(user, " 测试症状描述 ").departments()).hasSize(1);
    verify(limiter).acquire(user);
    doThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "频率限制"))
        .when(limiter)
        .acquire(user);
    assertThatThrownBy(() -> service.recommend(user, "测试症状描述"))
        .isInstanceOf(BusinessException.class);
    verify(client, times(1)).decide(anyString(), anyList());
  }
}
