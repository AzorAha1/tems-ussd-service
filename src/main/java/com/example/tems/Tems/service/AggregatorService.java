package com.example.tems.Tems.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class AggregatorService {
    @Value("${aggregator.api.key}")
    private String apiKey;

    @Value("${aggregator.mtn.product.id}")
    private String mtnProductId;

    @Value("${aggregator.airtel.product.id}")
    private String airtelProductId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ProductsService productsService;

    public AggregatorService(ProductsService productsService) {
        this.productsService = productsService;
    }

    public void initiateSubscription(String phoneNumber) {
        String hostUrl = productsService.gethosturlbynumber(phoneNumber);
        if (hostUrl.equals("Invalid phone number")) {
            System.out.println("❌ Invalid phone number: " + phoneNumber);
            return;
        }
        
        // Determine telco and select product ID
        String telco = determineTelco(phoneNumber);
        String productId = telco.equals("MTN") ? mtnProductId : airtelProductId;
        
        // Convert to international format
        String internationalPhone = convertToInternationalFormat(phoneNumber);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> payload = Map.of(
            "product_id", productId,
            "phone", internationalPhone,
            "telco", telco,
            "channel", "USSD"
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
        
        try {
            String endpoint = "/product/subscription/initiate";
            System.out.println("🌐 Calling: " + hostUrl + endpoint);
            System.out.println("📦 Payload: " + payload);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                hostUrl + endpoint,
                request,
                String.class
            );
            
            System.out.println("✅ Subscription initiated: " + response.getStatusCode());
            System.out.println("📄 Response: " + response.getBody());
        } catch (Exception e) {
            System.out.println("❌ Error initiating subscription: " + e.getMessage());
        }
    }
    
    private String convertToInternationalFormat(String phoneNumber) {
        String clean = phoneNumber.replaceAll("[^0-9]", "");
        if (clean.startsWith("0")) {
            return "234" + clean.substring(1);
        } else if (clean.startsWith("234")) {
            return clean;
        } else if (clean.length() == 10) {
            return "234" + clean;
        }
        return phoneNumber; // fallback
    }
    
    private String determineTelco(String phoneNumber) {
        String clean = phoneNumber.replaceAll("[^0-9]", "");
        if (clean.startsWith("0")) clean = clean.substring(1);
        if (clean.startsWith("234")) clean = clean.substring(3);
        
        // MTN prefixes
        if (clean.matches("^(703|706|803|806|810|813|814|816|903|906|913|916).*")) {
            return "MTN";
        } 
        // 9mobile prefixes
        else if (clean.matches("^(809|817|818|909|908|911).*")) {
            return "9MOBILE";
        }
        // Default to AIRTEL
        return "AIRTEL";
    }
}