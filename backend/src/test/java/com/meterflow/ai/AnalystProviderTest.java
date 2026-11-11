package com.meterflow.ai;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnalystProviderTest {
  @Test
  void sdkSendsMinimizedFactsAndAcceptsOnlyAllowedIds() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var request = new AtomicReference<String>();
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          request.set(
              new String(
                  exchange.getRequestBody().readAllBytes(),
                  java.nio.charset.StandardCharsets.UTF_8));
          var json = new ObjectMapper();
          String output =
              json.writeValueAsString(
                  Map.of("evidenceIds", List.of("comparison"), "explanation", "Bill increased"));
          byte[] body =
              json.writeValueAsBytes(
                  Map.of(
                      "id",
                      "test",
                      "object",
                      "chat.completion",
                      "created",
                      1,
                      "model",
                      "gpt-4.1-mini",
                      "choices",
                      List.of(
                          Map.of(
                              "index",
                              0,
                              "finish_reason",
                              "stop",
                              "message",
                              Map.of("role", "assistant", "content", output)))));
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var provider =
          new AnalystProvider(
              "synthetic-test-token",
              "gpt-4.1-mini",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
              new ObjectMapper());
      assertThat(
              provider.explain(
                  Set.of("COMPARISON"),
                  Map.of("comparison", "Bill increased"),
                  Map.of("usage", 100)))
          .hasValue("Bill increased");
      assertThat(request.get()).contains("\"store\":false").doesNotContain("synthetic-test-token");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void providerFailureDoesNotBreakAnalytics() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        e -> {
          e.sendResponseHeaders(503, -1);
          e.close();
        });
    server.start();
    try {
      var provider =
          new AnalystProvider(
              "synthetic-test-token",
              "gpt-4.1-mini",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
              new ObjectMapper());
      assertThat(
              provider.explain(
                  Set.of("COMPARISON"), Map.of("comparison", "Bill increased"), Map.of()))
          .isEmpty();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void noCredentialMeansNoExternalProcessing() {
    assertThat(
            new AnalystProvider("", "unused", "https://api.openai.com/v1", new ObjectMapper())
                .configured())
        .isFalse();
  }

  @Test
  void modelCannotSwapVerifiedAmountsInDisplayedExplanation() throws Exception {
    String evidence = "Current bill is EUR 10.00. Previous bill was EUR 20.00.";
    var output =
        Map.of(
            "evidenceIds",
            List.of("comparison"),
            "explanation",
            "Current bill is EUR 20.00. Previous bill was EUR 10.00.");
    assertThat(response(output, evidence)).hasValue(evidence);
  }

  @Test
  void rejectsUnknownEvidenceAndInventedAmounts() throws Exception {
    String evidence = "Current bill is EUR 10.00.";
    assertThat(
            response(Map.of("evidenceIds", List.of("unknown"), "explanation", evidence), evidence))
        .isEmpty();
    assertThat(
            response(
                Map.of(
                    "evidenceIds",
                    List.of("comparison"),
                    "explanation",
                    "Current bill is EUR 999.00."),
                evidence))
        .isEmpty();
    assertThat(
            response(
                Map.of("evidenceIds", List.of("comparison", "comparison"), "explanation", evidence),
                evidence))
        .isEmpty();
  }

  private Optional<String> response(Map<String, Object> output, String evidence) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var json = new ObjectMapper();
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] body =
              json.writeValueAsBytes(
                  Map.of(
                      "id",
                      "test",
                      "object",
                      "chat.completion",
                      "created",
                      1,
                      "model",
                      "test-model",
                      "choices",
                      List.of(
                          Map.of(
                              "index",
                              0,
                              "finish_reason",
                              "stop",
                              "message",
                              Map.of(
                                  "role",
                                  "assistant",
                                  "content",
                                  json.writeValueAsString(output))))));
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      return new AnalystProvider(
              "synthetic-test-token",
              "test-model",
              "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
              json)
          .explain(Set.of("COMPARISON"), Map.of("comparison", evidence), Map.of());
    } finally {
      server.stop(0);
    }
  }
}
