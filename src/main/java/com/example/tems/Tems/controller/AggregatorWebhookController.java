package com.example.tems.Tems.controller;

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

    @Value("${aggregator.mtn.product.id}")
    private String mtnProductId;
    @Value("${aggregator.airtel.product.id}")
    private String airtelProductId;
    private final SubscriptionService subscriptionService;

    @Autowired
    public AggregatorWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }
    
    @PostMapping("/aggregator")
    public ResponseEntity<String> handleAggregatorWebhookNotification(@RequestBody AggregatorNotification notification) {
        // Log the received payload for debug
        System.out.println("Received Aggregator Notification: " + notification.getType());
        if (!notification.getProduct().getIdentity().equals(mtnProductId) && 
            !notification.getProduct().getIdentity().equals(airtelProductId)) {
            System.err.println("❌ Invalid product identity: " + notification.getProduct().getIdentity());
            return ResponseEntity.badRequest().body("Invalid product identity");
        }
        // details object
        Details details = notification.getDetails();
        // raw phone number
        String rawPhone = details.getPhone();
        if (rawPhone == null || rawPhone.isEmpty()) {
            System.err.println("❌ Phone number is null or empty");
            return ResponseEntity.badRequest().body("Phone number missing");
        }
        String cleanNumber = normalizePhoneNumber(rawPhone);
        
        // Log the normalized phone number
        System.out.println("Normalized Phone Number: " + cleanNumber);
        // Here you can add logic to handle the notification, such as saving it to a database
        switch(notification.getType()) {
            case "SYNC_NOTIFICATION":
                System.out.println("✅ Handling payment success");
                subscriptionService.activateSession(cleanNumber, 120);
                System.out.println("🟢 Activated 2-hour session for: " + cleanNumber);
                break;
            case "CHARGING_ERROR":      // Payment failed
            case "INSUFFICIENT_BALANCE": // Insufficient airtime
                System.out.println("⛔ Payment failed for: " + cleanNumber);
                break;
                    
            default:
                System.out.println("⚠️ Unknown notification type: " + notification.getType());
            }
        return ResponseEntity.ok("Webhook notification received successfully");
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
            System.err.println("⚠️ Invalid phone length: " + normalized);
        }
        return normalized;

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
