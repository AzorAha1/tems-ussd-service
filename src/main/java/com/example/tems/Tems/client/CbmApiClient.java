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

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CbmApiClient.class);

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

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);

            Map<String, Object> body = response.getBody();

            if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                return Optional.ofNullable((Map<String, Object>) body.get("data"));
            }
            return Optional.empty();

        } catch (HttpClientErrorException e) {
            LOG.warn("cbm_verification_failure status={}", e.getStatusCode().value());
            return Optional.empty();
        } catch (Exception e) {
            LOG.warn("cbm_verification_failure type={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
