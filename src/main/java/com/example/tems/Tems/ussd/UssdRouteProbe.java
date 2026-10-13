package com.example.tems.Tems.ussd;

import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import static com.example.tems.Tems.ussd.UssdInboundRequest.SessionEvent.*;

/** Temporary two-callback experiment, isolated from the phone-based application engine. */
@Component
public final class UssdRouteProbe {
    public static final String MENU = "CON TEMS ROUTE ALIAS TEST\n\n1. Continue\n0. Exit";
    private static final String EXPIRED = "END Route alias test session expired. Please dial again.";
    private final RedisTemplate<String, Object> redis;
    private final UssdGatewayProperties properties;
    private final UssdRouteParser parser;

    public record Result(String response, boolean initial, String state) {}

    public UssdRouteProbe(RedisTemplate<String, Object> redis, UssdGatewayProperties properties,
                         UssdRouteParser parser) {
        this.redis = redis;
        this.properties = properties;
        this.parser = parser;
    }

    /** null means use the unchanged application path. A configured test number is always required. */
    public Result handle(UssdInboundRequest request, String phone) {
        if (!properties.isAliasTestEnabled() || !approved(phone)) return null;
        var path = parser.initialPath(request, properties.cumulative(), !request.sessionId().isEmpty() || request.event() == BEGIN);
        boolean publicOne = path.valid() && path.baseCode().equals("7447")
            && path.transportPrefix().isEmpty() && path.publicRoute().equals("1");
        if (request.sessionId().isEmpty()) {
            return publicOne ? new Result("END Route alias test requires a stable session ID.", true, "missing-session-id") : null;
        }
        String key = key(phone, request.sessionId());
        Object stored = redis.opsForValue().get(key);
        boolean active = stored instanceof java.util.Map<?, ?> values && values.get("entryInput") instanceof String;
        if (stored != null && !active) return new Result(EXPIRED, false, "invalid-state");
        if (request.event() == END && active) {
            redis.delete(key);
            return new Result("END Route alias test closed.", false, "closed");
        }
        if (request.event() == BEGIN) active = false;
        if (active) {
            // Never resolve an active menu choice as a new entry route.
            Object entryInput = ((java.util.Map<?, ?>) stored).get("entryInput");
            if (request.event() == UNKNOWN && request.input(properties.cumulative()).equals(entryInput)) {
                // A retransmitted initial dial ending in 1 must not manufacture a successful second step.
                return new Result(MENU, false, "duplicate-or-ambiguous-entry");
            }
            String reply = request.input(properties.cumulative());
            if (properties.cumulative()) {
                String[] parts = reply.trim().replaceAll("^\\*|#$", "").split("\\*", -1);
                reply = parts[parts.length - 1];
            }
            if (reply.equals("1") || reply.equals("0")) {
                redis.delete(key);
                return new Result(reply.equals("1") ? "END Route alias session test successful."
                    : "END Route alias test exited.", false, "completed");
            }
            return new Result(MENU, false, "awaiting-choice");
        }
        if (!publicOne) return null;
        if (request.event() == CONTINUE || request.event() == END) return new Result(EXPIRED, false, "expired");
        // Unknown-event replies must not silently turn into a new test after Redis expiry.
        String input = request.input(properties.cumulative()).trim().replaceAll("^\\*|#$", "");
        String service = request.serviceCode().trim().replaceAll("^\\*|#$", "");
        boolean entryPayload = path.remainingSegments().isEmpty()
            && (input.isEmpty() || input.equals("7447") || input.equals("7447*1")
                || (service.equals("7447") && input.equals("1")));
        if (request.event() != BEGIN && !entryPayload) {
            return new Result(EXPIRED, false, "expired");
        }
        redis.opsForValue().set(key, new java.util.HashMap<>(java.util.Map.of("entryInput",
            request.input(properties.cumulative()))), 3, TimeUnit.MINUTES);
        return new Result(MENU, true, "awaiting-choice");
    }

    public boolean approved(String phone) {
        return properties.getAliasTestNumbers().stream().anyMatch(number -> normalizePhone(number).equals(phone));
    }

    private static String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("234") && digits.length() == 13) return "0" + digits.substring(3);
        return digits.length() == 10 ? "0" + digits : digits;
    }

    static String key(String phone, String session) {
        return "ussd:route-probe:" + UssdDiagnosticTrace.fingerprint(phone + "\u0000" + session);
    }
}
