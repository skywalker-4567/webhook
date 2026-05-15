package com.example.razorpaywebhook.client;

import com.example.razorpaywebhook.exception.GatewayUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class RazorpaySettlementClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RazorpaySettlementClient(
            @Value("${razorpay.base-url}") String baseUrl,
            @Value("${razorpay.key-id}") String keyId,
            @Value("${razorpay.key-secret}") String keySecret,
            ObjectMapper objectMapper) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(keyId, keySecret))
                .build();
        this.objectMapper = objectMapper;
    }

    public List<RazorpaySettlementItem> getSettlements(Instant from, Instant to) {
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/settlements")
                            .queryParam("from", from.getEpochSecond())
                            .queryParam("to", to.getEpochSecond())
                            .queryParam("count", 100)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw new GatewayUnavailableException(
                                "Razorpay settlements returned " + response.getStatusCode());
                    })
                    .body(String.class);

            return parseItems(responseBody);

        } catch (GatewayUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new GatewayUnavailableException(
                    "Razorpay settlements unavailable: " + ex.getMessage());
        }
    }

    private List<RazorpaySettlementItem> parseItems(String responseBody) {
        List<RazorpaySettlementItem> items = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode node : itemsNode) {
                    items.add(objectMapper.treeToValue(node, RazorpaySettlementItem.class));
                }
            }
        } catch (Exception ex) {
            log.error("Failed to parse settlement response", ex);
        }
        return items;
    }
}