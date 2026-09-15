package com.example.tems.Tems.service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.example.tems.Tems.config.CbmApiConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Service
public class CbmTokenManager {

    private final RestTemplate restTemplate;
    private final CbmApiConfig config;

    // Read-only token (cbm.member.read) — Basic auth, client_credentials
    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    // USSD write token (cbm.ussd.write) — signed JWT (private_key_jwt)
    private volatile String cachedUssdToken;
    private volatile Instant ussdTokenExpiresAt = Instant.EPOCH;

    public CbmTokenManager(@Qualifier("cbmRestTemplate") RestTemplate restTemplate, CbmApiConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    // ---------- Read-only access (cbm.member.read) ----------

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
        this.expiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
        return token;
    }

    // ---------- USSD write access (cbm.ussd.write, private_key_jwt) ----------

    public synchronized String getUssdWriteAccessToken() {
        if (cachedUssdToken != null && Instant.now().isBefore(ussdTokenExpiresAt)) {
            return cachedUssdToken;
        }
        return fetchUssdWriteToken();
    }

    private String fetchUssdWriteToken() {
        try {
            String jwkJson = System.getenv("CBM_USSD_SIGNING_KEY");
            RSAKey rsaJwk = RSAKey.parse(jwkJson);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(config.getClientId())
                .subject(config.getClientId())
                .audience(config.getTokenUrl())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();

            SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(),
                claims
            );
            signedJwt.sign(new RSASSASigner(rsaJwk));
            String clientAssertion = signedJwt.serialize();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("scope", "cbm.ussd.write");
            body.add("client_id", config.getClientId());
            body.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
            body.add("client_assertion", clientAssertion);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(config.getTokenUrl(), request, Map.class);

            Map<String, Object> tokenBody = response.getBody();
            String token = (String) tokenBody.get("access_token");
            String grantedScope = (String) tokenBody.get("scope");
            System.out.println("🔍 CBM ussd token granted scope: " + grantedScope);

            int expiresIn = tokenBody.get("expires_in") != null
                ? ((Number) tokenBody.get("expires_in")).intValue() : 3600;

            this.cachedUssdToken = token;
            this.ussdTokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
            return token;

        } catch (Exception e) {
            System.err.println("⚠️ CBM ussd write token fetch failed: " + e.getMessage());
            throw new IllegalStateException("Could not obtain CBM ussd.write token", e);
        }
    }
}