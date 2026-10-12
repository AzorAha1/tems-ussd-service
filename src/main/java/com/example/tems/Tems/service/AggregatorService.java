package com.example.tems.Tems.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Client for the HML aggregator "Initiate subscription" call.
 *
 * NOT called from the USSD flow today. On MTN, the "charged @N12 / 1. Continue" screen is shown by MTN/HML
 * before /ussd is ever called, so calling initiateSubscription for an MTN user as well could bill them twice.
 * It therefore does nothing unless aggregator.subscription.enabled=true.
 *
 * Which network a phone is on comes from the "telco" field HML sends with each request. Guessing from the number's
 * prefix is only a last-resort fallback (numbers can be ported between networks).
 */
@Service
public class AggregatorService {
    private static final Logger LOG = LoggerFactory.getLogger(AggregatorService.class);

    public enum Telco { MTN, AIRTEL, NINEMOBILE, GLO, UNKNOWN }

    // Approximate Nigerian prefixes, used ONLY when HML did not send a telco. Verify against the current NCC list.
    private static final Set<String> MTN_PREFIXES = Set.of("703", "704", "706", "803", "806", "810", "813", "814", "816", "903", "906", "913", "916");
    private static final Set<String> AIRTEL_PREFIXES = Set.of("701", "708", "802", "808", "812", "901", "902", "904", "907", "912");
    private static final Set<String> GLO_PREFIXES = Set.of("705", "805", "807", "811", "815", "905", "915");
    private static final Set<String> NINEMOBILE_PREFIXES = Set.of("809", "817", "818", "908", "909");

    private final String apiKey;
    private final long productId;
    private final String mtnUrl;
    private final String airtelOrNinemobileUrl;
    private final boolean enabled;
    private final RestTemplate restTemplate;

    public AggregatorService(
            @Value("${aggregator.api.key:}") String apiKey,
            @Value("${aggregator.product.id:576}") long productId,
            @Value("${aggregator.mtn.url}") String mtnUrl,
            @Value("${aggregator.airtelor9mobile.url}") String airtelOrNinemobileUrl,
            @Value("${aggregator.subscription.enabled:false}") boolean enabled) {
        this.apiKey = apiKey;
        this.productId = productId;
        this.mtnUrl = mtnUrl;
        this.airtelOrNinemobileUrl = airtelOrNinemobileUrl;
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** Preferred: pass the telco HML sent with the request (for example "MTN"). */
    public boolean initiateSubscription(String phoneNumber, String telcoFromHml) {
        Telco telco = resolveTelco(telcoFromHml, phoneNumber);
        if (!enabled) {
            LOG.info("aggregator_subscription skipped reason=disabled telco={}", telco);
            return false;
        }
        if (apiKey == null || apiKey.isBlank()) {
            LOG.warn("aggregator_subscription skipped reason=no_api_key telco={}", telco);
            return false;
        }
        String host = hostFor(telco);
        if (host == null) {
            LOG.warn("aggregator_subscription skipped reason=no_host_for_telco telco={}", telco);
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", productId);               // HML's numeric product id, not the MTN subscription id
        payload.put("phone", toNationalFormat(phoneNumber)); // same format as HML's documented example
        payload.put("telco", telco.name());
        payload.put("channel", "USSD");

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                host + "/product/subscription/initiate", new HttpEntity<>(payload, headers), Map.class);
            @SuppressWarnings("rawtypes")
            Map body = response.getBody();
            boolean ok = response.getStatusCode().is2xxSuccessful() && body != null && Boolean.TRUE.equals(body.get("status"));
            // Never log the phone number or the response body (personal data).
            LOG.info("aggregator_subscription telco={} http={} ok={}", telco, response.getStatusCode().value(), ok);
            return ok;
        } catch (RestClientResponseException e) {
            LOG.warn("aggregator_subscription failed telco={} http={}", telco, e.getStatusCode().value());
            return false;
        } catch (Exception e) {
            LOG.warn("aggregator_subscription failed telco={} type={}", telco, e.getClass().getSimpleName());
            return false;
        }
    }

    /** Legacy entry point: no telco from HML, so it falls back to the prefix guess (logged as a warning). */
    public boolean initiateSubscription(String phoneNumber) {
        return initiateSubscription(phoneNumber, null);
    }

    // ---- helpers (package-private static so they are easy to unit test) ----

    private String hostFor(Telco telco) {
        switch (telco) {
            case MTN: return mtnUrl;
            case AIRTEL:
            case NINEMOBILE: return airtelOrNinemobileUrl;
            default: return null; // HML's docs list no host for Glo; never guess
        }
    }

    static Telco resolveTelco(String telcoFromHml, String phoneNumber) {
        Telco fromHml = telcoFromName(telcoFromHml);
        if (fromHml != Telco.UNKNOWN) return fromHml;
        Telco guessed = guessFromPrefix(phoneNumber);
        LOG.warn("aggregator_telco_fallback HML sent no usable telco, prefix guess={}", guessed);
        return guessed;
    }

    static Telco telcoFromName(String raw) {
        if (raw == null) return Telco.UNKNOWN;
        String name = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        switch (name) {
            case "MTN": return Telco.MTN;
            case "AIRTEL": return Telco.AIRTEL;
            case "NINEMOBILE":
            case "9MOBILE":
            case "ETISALAT": return Telco.NINEMOBILE;
            case "GLO":
            case "GLOBACOM": return Telco.GLO;
            default: return Telco.UNKNOWN;
        }
    }

    static Telco guessFromPrefix(String phoneNumber) {
        String national = toNationalFormat(phoneNumber);
        if (national.length() < 4) return Telco.UNKNOWN;
        String prefix = national.substring(1, 4); // after the leading 0
        if (MTN_PREFIXES.contains(prefix)) return Telco.MTN;
        if (AIRTEL_PREFIXES.contains(prefix)) return Telco.AIRTEL;
        if (GLO_PREFIXES.contains(prefix)) return Telco.GLO;
        if (NINEMOBILE_PREFIXES.contains(prefix)) return Telco.NINEMOBILE;
        return Telco.UNKNOWN;
    }

    static String toNationalFormat(String phoneNumber) {
        if (phoneNumber == null) return "";
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("234") && digits.length() == 13) return "0" + digits.substring(3);
        if (digits.length() == 10) return "0" + digits;
        return digits;
    }
}