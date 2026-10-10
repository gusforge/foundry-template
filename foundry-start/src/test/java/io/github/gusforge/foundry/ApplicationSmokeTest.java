package io.github.gusforge.foundry;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 端到端冒烟：完整装配四个模块，走真实 HTTP。用 H2 的 MySQL 模式，不依赖 Docker。
 *
 * <p>H2 只能验证装配与分层是否通；MySQL 方言行为由 foundry-dao 的 DemoRepositoryIT 覆盖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationSmokeTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json");
        if (body == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void create_then_get_roundtrip() throws Exception {
        HttpResponse<String> created = send("POST", "/api/v1/demos", "{\"name\":\"roundtrip\"}");
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains("\"data\"").contains("\"name\":\"roundtrip\"");
        assertThat(created.headers().firstValue("X-Trace-Id")).isPresent();

        HttpResponse<String> got = send("GET", "/api/v1/demos/1", null);
        assertThat(got.statusCode()).isEqualTo(200);
    }

    @Test
    void duplicated_name_returns_409_with_business_code() throws Exception {
        send("POST", "/api/v1/demos", "{\"name\":\"dup\"}");

        HttpResponse<String> second = send("POST", "/api/v1/demos", "{\"name\":\"dup\"}");

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("\"code\":300002").contains("\"traceId\"");
    }

    @Test
    void missing_row_returns_404_and_echoes_trace_id() throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/v1/demos/999999"))
                        .header("X-Trace-Id", "trace-from-caller")
                        .build();

        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode()).isEqualTo(404);
        assertThat(resp.headers().firstValue("X-Trace-Id")).contains("trace-from-caller");
        assertThat(resp.body()).contains("\"code\":300001").contains("trace-from-caller");
    }

    @Test
    void readiness_probe_is_up() throws Exception {
        assertThat(send("GET", "/actuator/health/readiness", null).statusCode()).isEqualTo(200);
    }

    @Test
    void sensitive_actuator_endpoints_are_not_exposed() throws Exception {
        assertThat(send("GET", "/actuator/env", null).statusCode()).isEqualTo(404);
        assertThat(send("GET", "/actuator/heapdump", null).statusCode()).isEqualTo(404);
    }

    @Test
    void openapi_docs_are_served() throws Exception {
        HttpResponse<String> docs = send("GET", "/v3/api-docs", null);
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("/api/v1/demos");
    }
}
