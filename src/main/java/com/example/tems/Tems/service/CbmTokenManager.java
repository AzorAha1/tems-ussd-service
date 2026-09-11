package com.example.tems.Tems.service;

import com.example.tems.Tems.config.CbmApiConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Service
public class CbmTokenManager {

    private final RestTemplate restTemplate;
    private final CbmApiConfig config;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public CbmTokenManager(@Qualifier("cbmRestTemplate") RestTemplate restTemplate, CbmApiConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        return fetchNewToken();
    }

    private String fetchNewToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getClientId(), config.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", config.getScope());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(config.getTokenUrl(), request, Map.class);

        Map<String, Object> tokenBody = response.getBody();
        if (tokenBody == null || tokenBody.get("access_token") == null) {
            throw new IllegalStateException("CBM token response missing access_token");
        }

        String token = (String) tokenBody.get("access_token");
        int expiresIn = tokenBody.get("expires_in") != null
            ? ((Number) tokenBody.get("expires_in")).intValue()
            : 3600;

        this.cachedToken = token;
        this.expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 60)); // refresh 60s early
        return token;
    }
}