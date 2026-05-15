package com.example.razorpaywebhook.client;

import com.example.razorpaywebhook.dto.CreateOrderRequest;
import com.example.razorpaywebhook.exception.GatewayTimeoutException;
import com.example.razorpaywebhook.exception.GatewayUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class RazorpayOrderClient {

    private final RestClient restClient;

    public RazorpayOrderClient(
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

    public RazorpayOrderResponse createOrder(CreateOrderRequest req) {
        RazorpayOrderRequest body = RazorpayOrderRequest.builder()
                .amount(req.getAmount())
                .currency(req.getCurrency())
                .receipt("rcpt_" + System.currentTimeMillis())
                .notes(req.getMetadata() != null ? req.getMetadata() : Map.of())
                .build();

        try {
            return restClient.post()
                    .uri("/v1/orders")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new GatewayUnavailableException(
                                "Razorpay returned " + response.getStatusCode());
                    })
                    .body(RazorpayOrderResponse.class);
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new GatewayTimeoutException("Razorpay request timed out");
            }
            throw new GatewayUnavailableException("Razorpay unavailable: " + ex.getMessage());
        }
    }
}