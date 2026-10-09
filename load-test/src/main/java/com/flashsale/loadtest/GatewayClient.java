package com.flashsale.loadtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** JSON over HTTP to the API Gateway. Every call is timed and recorded in {@link Metrics} under its operation name. */
final class GatewayClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final URI gateway;
    private final Metrics metrics;

    GatewayClient(URI gateway, Metrics metrics) {
        this.gateway = gateway;
        this.metrics = metrics;
    }

    ApiResponse get(String operation, String path, String token) {
        return send(operation, request(path, token).GET());
    }

    ApiResponse post(String operation, String path, String token, String idempotencyKey, Object body) {
        HttpRequest.Builder request = request(path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send(operation, request);
    }

    private HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(gateway.resolve(path)).timeout(REQUEST_TIMEOUT);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private ApiResponse send(String operation, HttpRequest.Builder request) {
        long start = System.nanoTime();
        ApiResponse response;
        try {
            HttpResponse<String> answer = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            response = new ApiResponse(answer.statusCode(), parse(answer.body()));
        } catch (IOException noAnswer) {
            response = ApiResponse.noResponse();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the gateway", interrupted);
        }
        metrics.record(operation, response, System.nanoTime() - start);
        return response;
    }

    private String toJson(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Request body is not serializable", exception);
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return json.readTree(body);
        } catch (JsonProcessingException notJson) {
            return MissingNode.getInstance();
        }
    }
}
