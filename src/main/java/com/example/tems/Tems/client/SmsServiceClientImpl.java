package com.example.tems.Tems.client;

import com.example.tems.Tems.config.SmsServiceConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
public class SmsServiceClientImpl implements SmsServiceClient {

    private final WebClient webClient;
    private final SmsServiceConfig config;

    public SmsServiceClientImpl(WebClient smsWebClient, SmsServiceConfig config) {
        this.webClient = smsWebClient;
        this.config = config;
    }

    @Override
    public SmsSendResponse sendSms(SmsSendRequest req) {
        String correlationId = (req.sessionId() != null && !req.sessionId().isBlank())
            ? req.sessionId()
            : UUID.randomUUID().toString();

        Map<String, Object> body = Map.of(
            "phone", req.phone(),
            "message", req.message(),
            "sessionId", req.sessionId() != null ? req.sessionId() : "",
            "refId", req.refId(),
            "module", req.module()
        );

        return sendWithRetry(body, correlationId, req.refId(), 0);
    }

    private SmsSendResponse sendWithRetry(Map<String, Object> body, String correlationId, String refId, int attempt) {
        try {
            Map response = webClient.post()
                .uri("/api/sms/send")
                .header("X-Service-Secret", config.getSecret())
                .header("X-Correlation-ID", correlationId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            String providerMessageId = extractMessageId(response);

            System.out.println("SMS dispatch " + (success ? "OK" : "FAILED")
                + " refId=" + refId + " correlationId=" + correlationId);

            return new SmsSendResponse(success, 200, "OK", providerMessageId, correlationId);

        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();

            if (status == 400 || status == 401 || status == 413 || status == 422) {
                System.err.println("SMS " + status + " (no retry) refId=" + refId
                    + " body=" + e.getResponseBodyAsString());
                return new SmsSendResponse(false, status, e.getResponseBodyAsString(), null, correlationId);
            }

            if (attempt < config.getMaxRetries()) {
                System.out.println("SMS " + status + " - retrying once. refId=" + refId);
                return sendWithRetry(body, correlationId, refId, attempt + 1);
            }

            System.err.println("SMS " + status + " - gave up after retry. refId=" + refId);
            return new SmsSendResponse(false, status, e.getResponseBodyAsString(), null, correlationId);

        } catch (Exception e) {
            if (attempt < config.getMaxRetries()) {
                System.out.println("SMS timeout/connect error - retrying once. refId=" + refId);
                return sendWithRetry(body, correlationId, refId, attempt + 1);
            }
            System.err.println("SMS timeout - gave up after retry. refId=" + refId + " error=" + e.getMessage());
            return new SmsSendResponse(false, 0, e.getMessage(), null, correlationId);
        }
    }

    private String extractMessageId(Map response) {
        if (response == null) return null;
        Object data = response.get("data");
        if (data instanceof Map dataMap) {
            Object id = dataMap.get("messageId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    static String maskPhone(String raw) {
        if (raw == null || raw.length() < 7) return "***";
        String digits = raw.replaceAll("\\D", "");
        int n = digits.length();
        return digits.substring(0, Math.min(4, n)) + "***" + digits.substring(Math.max(n - 4, 0));
    }
}