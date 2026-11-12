package com.yiyue.triage;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yiyue.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeepSeekClientTest {
  final ObjectMapper json = new ObjectMapper();
  MockRestServiceServer server;
  DeepSeekClient client;

  @BeforeEach
  void setup() {
    var builder = RestClient.builder().baseUrl("https://api.deepseek.com");
    server = MockRestServiceServer.bindTo(builder).build();
    var properties = new TriageProperties();
    properties.setApiKey("unit-test-placeholder");
    client = new DeepSeekClient(builder.build(), json, properties);
  }

  String envelope(String content, String finish) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "choices",
            List.of(Map.of("finish_reason", finish, "message", Map.of("content", content)))));
  }

  @Test
  void requestsJsonAndSendsOnlySymptomsAndAllowedCatalog() throws Exception {
    server
        .expect(requestTo("https://api.deepseek.com/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer unit-test-placeholder"))
        .andExpect(jsonPath("$.response_format.type").value("json_object"))
        .andExpect(jsonPath("$.thinking.type").value("disabled"))
        .andExpect(
            jsonPath("$.messages[1].content")
                .value(org.hamcrest.Matchers.containsString("availableDepartments")))
        .andRespond(
            withSuccess(
                envelope(
                    "{\"urgency\":\"UNDETERMINED\",\"departmentIds\":[],\"questions\":[]}", "stop"),
                MediaType.APPLICATION_JSON));
    assertThat(client.decide("虚构测试症状", List.of()).path("urgency").asText())
        .isEqualTo("UNDETERMINED");
    server.verify();
  }

  @Test
  void blankAndTruncatedOutputsFailClosed() throws Exception {
    server
        .expect(anything())
        .andRespond(withSuccess(envelope(" ", "stop"), MediaType.APPLICATION_JSON));
    server
        .expect(anything())
        .andRespond(withSuccess(envelope("{\"urgency\":", "length"), MediaType.APPLICATION_JSON));
    assertThatThrownBy(() -> client.decide("测试症状", List.of()))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> client.decide("测试症状", List.of()))
        .isInstanceOf(BusinessException.class);
    server.verify();
  }

  @Test
  void providerFailureDoesNotLeakResponseOrCredentials() {
    server
        .expect(anything())
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private provider response"));
    assertThatThrownBy(() -> client.decide("测试症状", List.of()))
        .isInstanceOf(BusinessException.class)
        .hasMessageNotContaining("private provider response")
        .hasMessageNotContaining("unit-test-placeholder");
  }

  @Test
  void connectionFailureHasClearTimeoutResponse() {
    server
        .expect(anything())
        .andRespond(withException(new java.net.SocketTimeoutException("internal network details")));
    assertThatThrownBy(() -> client.decide("测试症状", List.of()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> {
              assertThat(ex.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
              assertThat(ex.getMessage()).doesNotContain("internal network details");
            });
  }
}
