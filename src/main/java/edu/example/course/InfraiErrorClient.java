package edu.example.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiErrorClient {
    private final String key;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public InfraiErrorClient(@Value("${infrai.api-key}") String key,
                             @Value("${infrai.base-url}") String baseUrl) {
        this.key = key;
        this.baseUrl = baseUrl;
    }

    public record ApiError(String code, int status) {}

    public void capture(String attemptId, Map<String, Object> exceptionPayload) {
        try {
            String body = json.writeValueAsString(exceptionPayload);
            for (int retry = 0; retry < 4; retry++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/errors/capture"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", attemptId)
                        .method("POST", HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && retry < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                            .map(value -> {
                                try { return Long.parseLong(value); }
                                catch (NumberFormatException ignored) { return 0L; }
                            }).orElse(0L);
                    Thread.sleep(Math.max(seconds * 1000, 250L * (1L << retry)));
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    throw new ApiException(new ApiError(envelope.path("error").path("code").asText("UNKNOWN"), response.statusCode()));
                }
                if (response.statusCode() >= 500) throw new IOException("Upstream server response: " + response.statusCode());
                return;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot record course delivery", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Recording interrupted", e);
        }
    }

    public static class ApiException extends RuntimeException {
        private final ApiError detail;
        public ApiException(ApiError detail) { super(detail.code()); this.detail = detail; }
        public String code() { return detail.code(); }
        public int status() { return detail.status(); }
    }
}
