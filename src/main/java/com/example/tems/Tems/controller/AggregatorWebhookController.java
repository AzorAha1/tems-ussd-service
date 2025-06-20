package com.example.tems.Tems.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/webhook")
public class AggregatorWebhookController {
    
    @PostMapping("/aggregator")
    public ResponseEntity<String> handleAggregatorWebhookNotification() {
        // Log the received payload for debug
        System.out.println("Received webhook notification from aggregator");
        return ResponseEntity.ok("Webhook notification received successfully");
    }
}
