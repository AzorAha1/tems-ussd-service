package com.example.tems.Tems.ussd;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/** Request-scoped safe evidence. Arbitrary dialogue, headers and Redis values are never logged. */
public final class UssdDiagnosticTrace {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ROUTES = Set.of("1", "2", "3", "4", "5", "7", "10", "27", "100", "222", "1001");
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private UssdInboundRequest inbound;
    private boolean initial;

    public UssdDiagnosticTrace(String correlation, HttpServletRequest request) {
        fields.put("correlationId", correlation);
        fields.put("method", request.getMethod());
        fields.put("contentType", media(request.getContentType()));
        fields.put("accept", media(request.getHeader("Accept")));
        fields.put("handler", "normalization");
    }

    public void inbound(UssdInboundRequest request) {
        inbound = request;
        fields.put("sessionId", request.sessionId().isEmpty() ? "missing" : fingerprint(request.sessionId()));
        fields.put("phone", maskPhone(request.phoneNumber()));
        fields.put("messageEvent", request.event());
    }

    public void routing(boolean initial, UssdRouteParser.Path path, Map<String, Boolean> activeKeys) {
        this.initial = initial;
        fields.put("requestKind", initial ? "INITIAL" : "FOLLOW_UP");
        fields.put("baseCode", path.baseCode().equals("7447") ? "7447" : "unknown");
        fields.put("normalizedPublicRoute", path.valid() && ROUTES.contains(path.publicRoute()) ? path.publicRoute() : "unknown-or-root");
        fields.put("transportPrefix", path.transportPrefix());
        fields.put("redisStateKeys", activeKeys);
    }

    public void handler(String handler, String access) {
        fields.put("handler", handler);
        fields.put("access", access);
    }

    public void sessionDecision(boolean hasState, boolean changedId, boolean dialString) {
        fields.put("sessionDecision", Map.of("hasApplicationState", hasState,
            "changedSessionId", changedId, "dialStringRestart", dialString));
    }

    public void probe(UssdRouteProbe.Result result) {
        fields.put("probeState", result.state());
        fields.put("applicationResponse", result.response()); // Only these fixed diagnostic strings.
    }

    public void probeAccess(boolean enabled, boolean approved) {
        fields.put("probeAccess", !enabled ? "disabled" : approved ? "approved" : "allowlist-denied-use-existing-flow");
    }

    public void emit(Logger log, boolean enabled, String response, boolean plain, long started) {
        if (!enabled) return;
        if (inbound != null) {
            Map<String, String> raw = new LinkedHashMap<>();
            inbound.rawFields().forEach((key, value) -> raw.put(key,
                key.startsWith("messageType") || key.equals("message_type")
                    ? safeEvent(value, inbound.event()) : safeRoutingField(value, initial)));
            fields.put("rawFields", raw);
            fields.put("originalServiceCode", safeRoutingField(inbound.serviceCode(), true));
            fields.put("userInput", safeRoutingField(inbound.userInput(), initial));
            fields.put("cumulativeText", safeRoutingField(inbound.cumulativeText(), initial));
        }
        fields.put("httpStatus", 200);
        fields.put("responseContentType", plain ? "text/plain" : "application/json");
        fields.put("responseType", response.startsWith("CON ") ? "CON" : "END");
        fields.put("applicationResponseFingerprint", fingerprint(response));
        if (fields.containsKey("probeState")) {
            fields.put("responseBody", plain ? response : Map.of("continue", response.startsWith("CON "),
                "message", response.substring(4)));
        }
        fields.put("durationMs", (System.nanoTime() - started) / 1_000_000);
        try { log.info("USSD_DIAGNOSTIC {}", JSON.writeValueAsString(fields)); }
        catch (Exception ignored) { log.warn("USSD_DIAGNOSTIC encoding-failed"); }
    }

    static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static String maskPhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.length() >= 10 ? "*******" + digits.substring(digits.length() - 4) : "missing-or-invalid";
    }

    private static String media(String raw) {
        if (raw == null) return "missing";
        if (raw.length() > 160) return "redacted";
        for (String part : raw.split(",", -1)) {
            if (!part.trim().matches("(?i)(application/(json|x-www-form-urlencoded)|text/plain|\\*/\\*)"
                    + "(\\s*;\\s*(charset=utf-?8|q=[01](\\.[0-9]{1,3})?))*")) return "redacted";
        }
        return raw;
    }

    static String safeRoutingField(String raw, boolean initial) {
        if (raw == null || raw.isEmpty()) return "";
        String normalized = raw.trim().replaceAll("^\\*|#$", "");
        if (normalized.equals("7447") || normalized.equals("744710") || normalized.equals("744727")) return raw;
        var path = new UssdRouteParser().parse(raw);
        if (path.valid() && path.baseCode().equals("7447") && path.remainingSegments().isEmpty()
                && (path.publicRoute().isEmpty() || ROUTES.contains(path.publicRoute()))) return raw;
        return initial && ROUTES.contains(normalized) ? raw : "<redacted>";
    }

    private static String safeEvent(String raw, UssdInboundRequest.SessionEvent event) {
        return raw != null && (raw.matches("BEGIN|CONTINUE|END")
            || (event != UssdInboundRequest.SessionEvent.UNKNOWN && raw.matches("[0-9]{1,2}"))) ? raw : "<redacted>";
    }
}
