package com.srm.creditengine.shared.runtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Verifies the configured deterministic FX adapter without exposing provider details. */
@Component
class MockFxHealthIndicator implements HealthIndicator {
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    MockFxHealthIndicator(@Value("${srm.fx-provider.base-url:http://localhost:8090}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Health health() {
        if (baseUrl.isBlank()) {
            return Health.unknown().build();
        }
        try {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                            .GET()
                            .timeout(Duration.ofSeconds(2))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200 ? Health.up().build() : Health.down().build();
        } catch (Exception exception) {
            return Health.down().build();
        }
    }
}
