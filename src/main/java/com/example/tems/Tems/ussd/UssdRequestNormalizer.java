package com.example.tems.Tems.ussd;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static com.example.tems.Tems.ussd.UssdInboundRequest.SessionEvent;

@org.springframework.stereotype.Component
public final class UssdRequestNormalizer {
    private final ObjectMapper mapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public UssdInboundRequest normalize(Map<String, String[]> parameters, String rawBody,
            String contentType, Map<String, SessionEvent> eventMapping) {
        Map<String, String> body = parse(rawBody, contentType);
        Map<String, String> params = new HashMap<>();
        parameters.forEach((key, values) -> {
            if (values != null && values.length > 0) params.put(key, values[0]);
        });
        String event = pick(params, body, "messageType", "message_type");
        return new UssdInboundRequest(
            pick(params, body, "phoneNumber", "phone", "msisdn", "mobile", "caller", "subscriber"),
            pick(params, body, "session_id", "sessionId"),
            pick(params, body, "serviceCode", "service_code", "shortCode", "shortcode"),
            pick(params, body, "input", "ussdString", "ussd_string", "message"),
            pick(params, body, "text"), eventMapping.getOrDefault(event, SessionEvent.UNKNOWN));
    }

    private String pick(Map<String, String> params, Map<String, String> body, String... aliases) {
        // Nonblank servlet parameters precede body values; alias order is explicit.
        for (Map<String, String> source : java.util.List.of(params, body)) {
            for (String alias : aliases) {
                String value = source.get(alias);
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return "";
    }

    private Map<String, String> parse(String raw, String contentType) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isBlank()) return result;
        try {
            if ((contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json"))
                    || raw.stripLeading().startsWith("{")) {
                Object parsed = mapper.readValue(raw, Object.class);
                if (!(parsed instanceof Map<?, ?> values)) throw new IllegalArgumentException();
                values.forEach((key, value) -> {
                    if (value instanceof Map || value instanceof java.util.List) throw new IllegalArgumentException();
                    if (value != null) result.put(key.toString(), value.toString());
                });
            } else {
                for (String pair : raw.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length != 2) throw new IllegalArgumentException();
                    result.putIfAbsent(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
            }
            return result;
        } catch (Exception ex) {
            // Do not retain a parser exception that might contain a PIN or full payload.
            throw new IllegalArgumentException("Invalid USSD payload");
        }
    }
}
