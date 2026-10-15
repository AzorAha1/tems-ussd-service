package com.example.tems.Tems.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.tems.Tems.controller.AggregatorWebhookController.AggregatorNotification.Details;
import com.example.tems.Tems.service.SubscriptionService;

import lombok.Data;

@RestController
@RequestMapping(value = "/api/v1/tems/webhook")
public class AggregatorWebhookController {
    private static final Logger LOG = LoggerFactory.getLogger(AggregatorWebhookController.class);

    // HML puts the product's own identity (the "PD-..." value) in notification.product.identity.
    // For product 576 that is PD-17625145556900. Comma-separated if there are ever several products.
    @Value("${aggregator.webhook.accepted-identities:PD-17625145556900}")
    private String acceptedIdentities = "PD-17625145556900";

    /** How long a paid session lasts. 1440 = 24 hours, matching the product's validity_days of 1. */
    @Value("${ussd.payment.session-minutes:1440}")
    private int sessionMinutes = 1440;

    /**
     * Shared secret. When set, every call must carry it as ?key=... or the X-Webhook-Key header, otherwise 401.
     * Put it in the webhook URL registered with HML. Empty = not enforced (the call is still logged).
     */
    @Value("${aggregator.webhook.secret:}")
    private String webhookSecret = "";

    /**
     * Comma-separated IP addresses allowed to call the webhook (HML's server). Empty = not enforced.
     * The source IP is read from X-Real-IP / X-Forwarded-For, which is only trustworthy if the platform's proxy
     * overwrites them, so use it together with the secret.
     */
    @Value("${aggregator.webhook.allowed-ips:}")
    private String allowedIps = "";

    private final SubscriptionService subscriptionService;

    @Autowired
    public AggregatorWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/aggregator")
    public ResponseEntity<String> handleAggregatorWebhookNotification(
            @RequestBody AggregatorNotification notification,
            @RequestParam(value = "key", required = false) String keyParam,
            @RequestHeader(value = "X-Webhook-Key", required = false) String keyHeader,
            HttpServletRequest request) {

        String providedKey = keyParam != null ? keyParam : keyHeader;
        String sourceIp = clientIp(request.getHeader("X-Real-IP"), request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
        // Never log the key itself; the IP is HML's server address, not personal data.
        LOG.info("aggregator_webhook request key_present={} source_ip={} secret_enforced={} ip_enforced={}",
            providedKey != null, sourceIp, !webhookSecret.isBlank(), !allowedIps.isBlank());

        Auth auth = authorize(webhookSecret, providedKey, allowedIps, sourceIp);
        if (auth == Auth.BAD_KEY) {
            LOG.warn("aggregator_webhook rejected reason=bad_key source_ip={}", sourceIp);
            return ResponseEntity.status(401).body("Unauthorized");
        }
        if (auth == Auth.BAD_IP) {
            LOG.warn("aggregator_webhook rejected reason=bad_ip source_ip={}", sourceIp);
            return ResponseEntity.status(403).body("Forbidden");
        }

        if (notification == null || notification.getType() == null
                || notification.getProduct() == null || notification.getProduct().getIdentity() == null) {
            LOG.warn("aggregator_webhook rejected reason=malformed_payload");
            return ResponseEntity.badRequest().body("Malformed notification");
        }

        String type = notification.getType();
        String identity = notification.getProduct().getIdentity();
        LOG.info("aggregator_webhook received type={} product={}", type, identity);

        if (!isAcceptedIdentity(identity)) {
            LOG.warn("aggregator_webhook rejected reason=unknown_product product={}", identity);
            return ResponseEntity.badRequest().body("Invalid product identity");
        }

        boolean success = "SYNC_NOTIFICATION".equals(type) || "RENEWAL_NOTIFICATION".equals(type);
        boolean noBalance = "INSUFFICIENT_BALANCE".equals(type);
        boolean chargeError = "CHARGING_ERROR".equals(type);
        if (!success && !noBalance && !chargeError) {
            LOG.info("aggregator_webhook ignored type={}", type);
            return ResponseEntity.ok("Webhook notification received successfully");
        }

        Details details = notification.getDetails();
        String rawPhone = details == null ? null : details.getPhone();
        if (rawPhone == null || rawPhone.isBlank()) {
            LOG.warn("aggregator_webhook rejected reason=no_phone type={}", type);
            return ResponseEntity.badRequest().body("Phone number missing");
        }
        String cleanNumber = normalizePhoneNumber(rawPhone);

        if (success) {
            subscriptionService.activateSession(cleanNumber, sessionMinutes);
            LOG.info("aggregator_webhook activated type={} phone={} minutes={}", type, maskPhone(cleanNumber), sessionMinutes);
        } else {
            subscriptionService.markFailed(cleanNumber, noBalance);
            LOG.info("aggregator_webhook charge_failed type={} phone={}", type, maskPhone(cleanNumber));
        }
        return ResponseEntity.ok("Webhook notification received successfully");
    }

    // ---- authentication helpers (package-private static so they are easy to unit test) ----

    enum Auth { OK, BAD_KEY, BAD_IP }

    static Auth authorize(String secret, String providedKey, String allowedIps, String sourceIp) {
        if (secret != null && !secret.isBlank() && !sameSecret(secret, providedKey)) return Auth.BAD_KEY;
        if (allowedIps != null && !allowedIps.isBlank() && !csvContains(allowedIps, sourceIp)) return Auth.BAD_IP;
        return Auth.OK;
    }

    static boolean sameSecret(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    static String clientIp(String realIp, String forwardedFor, String remoteAddr) {
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        if (forwardedFor != null && !forwardedFor.isBlank()) return forwardedFor.split(",")[0].trim();
        return remoteAddr == null ? "" : remoteAddr;
    }

    static boolean csvContains(String csv, String value) {
        if (csv == null || value == null) return false;
        for (String entry : csv.split(",")) {
            if (entry.trim().equals(value.trim())) return true;
        }
        return false;
    }

    private boolean isAcceptedIdentity(String identity) {
        return csvContains(acceptedIdentities, identity);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        // 1. Remove non-digit characters
        String normalized = phoneNumber.replaceAll("[^0-9]", "");

        // 2. Convert international to local format
        if (normalized.startsWith("234")) {
            normalized = "0" + normalized.substring(3);
        }
        // 3. Ensure local format (0xxxxxxxxx)
        else if (!normalized.startsWith("0")) {
            normalized = "0" + normalized;
        }

        // 4. Validate length (Nigerian numbers are 11 digits)
        if (normalized.length() != 11) {
            LOG.warn("aggregator_webhook unusual_phone_length length={}", normalized.length());
        }
        return normalized;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 3);
    }

    @Data
    public static class AggregatorNotification {
        private String type;
        private Product product;
        private Details details;

        @Data
        public static class Product {
            private String identity;
        }
        @Data
        public static class Details {
            private String phone;
        }
    }
}