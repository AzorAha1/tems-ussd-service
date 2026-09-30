package com.example.tems.Tems.client;

import com.example.tems.Tems.config.CbmApiConfig;
import com.example.tems.Tems.service.CbmTokenManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class CbmUssdRelayClient {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CbmUssdRelayClient.class);

    private final RestTemplate restTemplate;
    private final CbmApiConfig config;
    private final CbmTokenManager tokenManager;

    public CbmUssdRelayClient(@Qualifier("cbmRestTemplate") RestTemplate restTemplate,
                               CbmApiConfig config, CbmTokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.tokenManager = tokenManager;
    }

    /**
     * Forwards one USSD hop to CBM's relay. Returns the raw CON/END string
     * to hand straight back to the aggregator, unmodified.
     */
    public String relay(String sessionId, String phoneNumber, String text) {
        String url = config.getBaseUrl() + "/api/v1/partner/ussd";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenManager.getUssdWriteAccessToken());
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("session_id", sessionId);
            body.put("phone_number", phoneNumber);
            body.put("text", text == null ? "" : text);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);


            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            Map<String, Object> responseBody = response.getBody();


            return extractMessage(responseBody);

        } catch (HttpClientErrorException e) {
            LOG.warn("cbm_relay_failure status={}", e.getStatusCode().value());
            return "END CBM registration is temporarily unavailable. Please try again shortly.";
        } catch (Exception e) {
            LOG.warn("cbm_relay_failure type={}", e.getClass().getSimpleName());
            return "END CBM registration is temporarily unavailable. Please try again shortly.";
        }
    }

    // Defensive extraction — tries the most likely field names.
    // Once we see one real response in the logs, we'll know the exact shape.
    @SuppressWarnings("unchecked")
    private String extractMessage(Map<String, Object> body) {
        if (body == null || !Boolean.TRUE.equals(body.get("success"))) {

            return "END CBM registration is temporarily unavailable. Please try again shortly.";
        }

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null) {

            return "END CBM registration is temporarily unavailable. Please try again shortly.";
        }

        String action = (String) data.get("action");   // "CON" or "END"
        String message = (String) data.get("message");

        if (action == null || message == null) {

            return "END CBM registration is temporarily unavailable. Please try again shortly.";
        }

        return action + " " + message;
    }
}
