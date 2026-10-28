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
 * Client for the HML aggregator subscription calls (Initiate and Unsubscribe).
 *
 * It does nothing unless aggregator.subscription.enabled=true, so it can be deployed safely.
 *
 * Which network a phone is on comes from the "telco" field HML sends with each request. Guessing from the number's
 * prefix is only a last-resort fallback (numbers can be ported between networks).
 */
@Service
public class AggregatorService {
    private static final Logger LOG = LoggerFactory.getLogger(AggregatorService.class);

    public enum Telco { MTN, AIRTEL, NINEMOBILE, GLO, UNKNOWN }

    /**
     * STARTED: HML accepted the request. SKIPPED: nothing was sent (disabled, no key, unknown network).
     * FAILED: HML refused it, or it never left us (safe to try again).
     * UNKNOWN: it was sent but no answer came back (timeout or reset), so HML may still have processed it.
     */
    public enum InitiateResult { STARTED, SKIPPED, FAILED, UNKNOWN }

    private enum PostResult { OK, REJECTED, UNKNOWN }

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
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** Ask HML to start the charge. The result of the charge arrives later at the webhook. */
    public InitiateResult initiate(String phoneNumber, String telcoFromHml) {
        Telco telco = resolveTelco(telcoFromHml, phoneNumber);
        String host = readyHost(telco, "initiate");
        if (host == null) return InitiateResult.SKIPPED;

        Map<String, Object> payload = basePayload(phoneNumber, telco);
        payload.put("channel", "USSD");
        PostResult result = post(host + "/product/subscription/initiate", payload, telco, "initiate");
        if (result == PostResult.OK) return InitiateResult.STARTED;
        return result == PostResult.UNKNOWN ? InitiateResult.UNKNOWN : InitiateResult.FAILED;
    }

    /** Stop a subscription (HML's docs: POST /product/subscription/unsubscribe). Not used by the USSD flow yet. */
    public boolean unsubscribe(String phoneNumber, String telcoFromHml) {
        Telco telco = resolveTelco(telcoFromHml, phoneNumber);
        String host = readyHost(telco, "unsubscribe");
        if (host == null) return false;
        return post(host + "/product/subscription/unsubscribe", basePayload(phoneNumber, telco), telco, "unsubscribe") == PostResult.OK;
    }

    public boolean initiateSubscription(String phoneNumber, String telcoFromHml) {
        return initiate(phoneNumber, telcoFromHml) == InitiateResult.STARTED;
    }

    /** Legacy entry point: no telco from HML, so it falls back to the prefix guess (logged as a warning). */
    public boolean initiateSubscription(String phoneNumber) {
        return initiateSubscription(phoneNumber, null);
    }

    // ---- internals ----

    /** The host to call, or null (with a log line) if this call must not be made. */
    private String readyHost(Telco telco, String action) {
        if (!enabled) {
            LOG.info("aggregator_{} skipped reason=disabled telco={}", action, telco);
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            LOG.warn("aggregator_{} skipped reason=no_api_key telco={}", action, telco);
            return null;
        }
        String host = hostFor(telco);
        if (host == null) {
            LOG.warn("aggregator_{} skipped reason=no_host_for_telco telco={}", action, telco);
        }
        return host;
    }

    private Map<String, Object> basePayload(String phoneNumber, Telco telco) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", productId);               // HML's numeric product id, not the MTN subscription id
        payload.put("phone", toNationalFormat(phoneNumber)); // same format as HML's documented example
        payload.put("telco", telco.name());
        return payload;
    }

    private PostResult post(String url, Map<String, Object> payload, Telco telco, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), Map.class);
            @SuppressWarnings("rawtypes")
            Map body = response.getBody();
            boolean ok = response.getStatusCode().is2xxSuccessful() && body != null && Boolean.TRUE.equals(body.get("status"));
            // Log only HML's status code and a short message (digit runs masked), never the phone number or the full body.
            LOG.info("aggregator_{} telco={} http={} ok={} hml_status_code={} hml_message={}", action, telco,
                response.getStatusCode().value(), ok,
                body == null ? null : body.get("status_code"), safeMessage(body == null ? null : body.get("message")));
            return ok ? PostResult.OK : PostResult.REJECTED;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            LOG.warn("aggregator_{} failed telco={} http={}", action, telco, status);
            // 502/503/504 come from a gateway in front of HML: we cannot tell whether HML processed the request.
            return (status == 502 || status == 503 || status == 504) ? PostResult.UNKNOWN : PostResult.REJECTED;
        } catch (Exception e) {
            boolean neverSent = neverReachedServer(e);
            LOG.warn("aggregator_{} failed telco={} type={} cause={} request_sent={}", action, telco,
                e.getClass().getSimpleName(), rootCauseName(e), !neverSent);
            return neverSent ? PostResult.REJECTED : PostResult.UNKNOWN;
        }
    }

    /** True when the failure happened before the request left us (connection refused, unknown host, connect timeout). */
    static boolean neverReachedServer(Throwable error) {
        Throwable t = error;
        int depth = 0;
        while (t != null && depth++ < 10) {
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
                    || t instanceof java.net.NoRouteToHostException) return true;
            if (t instanceof java.net.SocketTimeoutException && t.getMessage() != null
                    && t.getMessage().toLowerCase(Locale.ROOT).contains("connect")) return true;
            t = t.getCause() == t ? null : t.getCause();
        }
        return false;
    }

    static String rootCauseName(Throwable error) {
        Throwable t = error;
        int depth = 0;
        while (t.getCause() != null && t.getCause() != t && depth++ < 10) t = t.getCause();
        return t.getClass().getSimpleName();
    }

    /** A short, log-safe version of HML's message: long digit runs (phone numbers, ids) are masked. */
    static String safeMessage(Object message) {
        if (message == null) return null;
        String text = String.valueOf(message).replaceAll("\\d{6,}", "***");
        return text.length() > 80 ? text.substring(0, 80) : text;
    }

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