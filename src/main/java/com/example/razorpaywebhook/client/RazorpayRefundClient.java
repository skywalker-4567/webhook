package com.example.razorpaywebhook.client;

import com.example.razorpaywebhook.exception.GatewayUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class RazorpayRefundClient {

    private final RestClient restClient;

    public RazorpayRefundClient(
            @Value("${razorpay.base-url}") String baseUrl,
            @Value("${razorpay.key-id}") String keyId,
            @Value("${razorpay.key-secret}") String keySecret) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
                .build();
    }

    public RazorpayRefundResponse initiateRefund(String paymentId, Long amount, String reason) {
        RazorpayRefundRequest body = RazorpayRefundRequest.builder()
                .amount(amount)
                .notes(reason != null ? Map.of("reason", reason) : Map.of())
                .build();

        try {
            return restClient.post()
                    .uri("/v1/payments/{paymentId}/refund", paymentId)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new GatewayUnavailableException(
                                "Razorpay refund returned " + response.getStatusCode());
                    })
                    .body(RazorpayRefundResponse.class);
        } catch (GatewayUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new GatewayUnavailableException("Razorpay unavailable: " + ex.getMessage());
        }
    }
}