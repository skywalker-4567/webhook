package com.example.razorpaywebhook.fraud;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class MLClient {

    private final RestClient restClient;

    public MLClient(
            @Value("${ml.service.base-url:http://ml-service:8000}") String baseUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public Optional<MLScoreResponse> score(MLScoreRequest request) {
        try {
            MLScoreResponse response = restClient.post()
                    .uri("/score")
                    .body(request)
                    .retrieve()
                    .body(MLScoreResponse.class);
            return Optional.ofNullable(response);
        } catch (Exception ex) {
            log.warn("MLClient: scoring failed — paymentAmount={} reason={}",
                    request.amount(), ex.getMessage());
            return Optional.empty();
        }
    }
}