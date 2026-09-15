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

            System.out.println("🔍 CBM relay - calling: " + url + " | body: " + body);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            Map<String, Object> responseBody = response.getBody();
            System.out.println("🔍 CBM relay - raw response: " + responseBody);

            return extractMessage(responseBody);

        } catch (HttpClientErrorException e) {
            System.err.println("⚠️ CBM relay failed - Status: " + e.getStatusCode()
                + " | Body: " + e.getResponseBodyAsString());
            return "END CBM registration is temporarily unavailable. Please try again shortly.";
        } catch (Exception e) {
            System.err.println("⚠️ CBM relay failed - " + e.getMessage());
            return "END CBM registration is temporarily unavailable. Please try again shortly.";
        }
    }

    // Defensive extraction — tries the most likely field names.
    // Once we see one real response in the logs, we'll know the exact shape.
    @SuppressWarnings("unchecked")
    private String extractMessage(Map<String, Object> body) {
        if (body == null) return "END CBM registration is temporarily unavailable. Please try again shortly.";

        Object candidate = body.get("message");
        if (candidate == null) candidate = body.get("reply");
        if (candidate == null) candidate = body.get("response");
        if (candidate == null) candidate = body.get("text");

        if (candidate instanceof String) {
            String msg = (String) candidate;
            // If CBM already prefixes with CON/END, pass through as-is.
            if (msg.startsWith("CON ") || msg.startsWith("END ")) {
                return msg;
            }
            // Otherwise infer from a separate "continue"/"is_final" style flag if present.
            Object continueFlag = body.get("continue");
            boolean shouldContinue = Boolean.TRUE.equals(continueFlag);
            return (shouldContinue ? "CON " : "END ") + msg;
        }

        System.err.println("⚠️ CBM relay - unrecognized response shape: " + body);
        return "END CBM registration is temporarily unavailable. Please try again shortly.";
    }
}