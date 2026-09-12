package com.example.tems.Tems.client;

import com.example.tems.Tems.config.CbmApiConfig;
import com.example.tems.Tems.service.CbmTokenManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
public class CbmApiClient {

    private final RestTemplate restTemplate;
    private final CbmApiConfig config;
    private final CbmTokenManager tokenManager;

    public CbmApiClient(@Qualifier("cbmRestTemplate") RestTemplate restTemplate, CbmApiConfig config, CbmTokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.tokenManager = tokenManager;
    }

    public Optional<Map<String, Object>> verifyMemberById(String memberId) {
        String url = config.getBaseUrl() + "/api/v1/partner/members/verify?member_id=" + memberId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenManager.getAccessToken());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            System.out.println("🔍 CBM verify - calling: " + url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            Map<String, Object> body = response.getBody();
            System.out.println("🔍 CBM verify - response: " + body);

            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                return Optional.ofNullable((Map<String, Object>) body.get("data"));
            }
            return Optional.empty();

        } catch (HttpClientErrorException e) {
            System.err.println("⚠️ CBM verify failed - URL: " + url
                + " | Status: " + e.getStatusCode()
                + " | Body: " + e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("⚠️ CBM verify failed - URL: " + url + " | " + e.getMessage());
            return Optional.empty();
        }
    }
}