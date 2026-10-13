package com.example.tems.Tems.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private final SubscriptionService subscriptionService;

    @Autowired
    public AggregatorWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/aggregator")
    public ResponseEntity<String> handleAggregatorWebhookNotification(@RequestBody AggregatorNotification notification) {
        if (notification == null || notification.getType() == null
                || notification.getProduct() == null || notification.getProduct().getIdentity() == null) {
            LOG.warn("aggregator_webhook rejected reason=malformed_payload");
            return ResponseEntity.badRequest().body("Malformed notification");
        }

        String type = notification.getType();
        String identity = notification.getProduct().getIdentity();
        LOG.info("aggregator_webhook received type={} product={}", type, identity);

        if (!isAcceptedIdentity(identity)) {
            // The identity is not a secret; logging it shows exactly what HML sent.
            LOG.warn("aggregator_webhook rejected reason=unknown_product product={}", identity);
            return ResponseEntity.badRequest().body("Invalid product identity");
        }

        boolean success = "SYNC_NOTIFICATION".equals(type) || "RENEWAL_NOTIFICATION".equals(type);
        boolean noBalance = "INSUFFICIENT_BALANCE".equals(type);
        boolean chargeError = "CHARGING_ERROR".equals(type);
        if (!success && !noBalance && !chargeError) {
            // Other notification types (for example the deprecated TELCO_RAW_SYNC) carry no phone number; acknowledge them.
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
            // Recorded so a dial that is waiting for this payment can tell the user what happened.
            subscriptionService.markFailed(cleanNumber, noBalance);
            LOG.info("aggregator_webhook charge_failed type={} phone={}", type, maskPhone(cleanNumber));
        }
        return ResponseEntity.ok("Webhook notification received successfully");
    }

    private boolean isAcceptedIdentity(String identity) {
        if (acceptedIdentities == null) return false;
        for (String accepted : acceptedIdentities.split(",")) {
            if (accepted.trim().equals(identity)) return true;
        }
        return false;
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