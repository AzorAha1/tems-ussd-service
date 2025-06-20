package com.example.tems.Tems.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ProductsService {
    
    @Value("${aggregator.api.key}")
    private String aggregatorApiKey;

    @Value("${aggregator.mtn.url}")
    private String aggregatorMtnUrl;

    @Value("${aggregator.airtelor9mobile.url}")
    private String aggregatorAirtelOr9MobileUrl;

    RestTemplate restTemplate = new RestTemplate();
    
    public String createTemsService(String phonenumber) {
        String hosturl = gethosturlbynumber(phonenumber);
        if (hosturl.equals("Invalid phone number")) {
            return "Invalid phone number";
        }
        
        Map<String, Object> product = Map.of(
            "name", "Nigerian Tems Service",
            "type", "SUBSCRIPTION",
            "subscription_type", "ONETIME_AND_RECURRING",
            "amount", 5000,
            "webhook", "https://f087-105-112-206-115.ngrok-free.app/api/v1/tems/webhook/aggregator",
            "validity_days", 0,
            "status", "ACTIVE"
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(aggregatorApiKey);
        headers.set("Content-Type", "application/json");

        try {
            System.out.println("🚀 Making request to: " + hosturl + "/products");
            System.out.println("📦 Request payload: " + product);
            System.out.println("🔑 Using API key: " + aggregatorApiKey.substring(0, 5) + "...");
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                hosturl + "/product/create",
                new HttpEntity<Map<String, Object>>(product, headers),
                Map.class
            );
            
            System.out.println("✅ Success! Response: " + response.getBody());
            return response.getBody().toString();
            
        } catch (HttpClientErrorException e) {
            System.err.println("❌ Client Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 404) {
                return "Error: API endpoint not found. Please check the aggregator URL configuration.";
            } else if (e.getStatusCode().value() == 401) {
                return "Error: Unauthorized. Please check your API key.";
            } else {
                return "Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
            }
        } catch (HttpServerErrorException e) {
            System.err.println("💥 Server Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return "Error: Server error from aggregator API - " + e.getStatusCode();
        } catch (ResourceAccessException e) {
            System.err.println("🔌 Connection Error: " + e.getMessage());
            return "Error: Could not connect to aggregator API. Please check if the service is running.";
        } catch (Exception e) {
            System.err.println("🚨 Unexpected Error: " + e.getMessage());
            return "Error: Unexpected error occurred - " + e.getMessage();
        }
    }
    
    public String gethosturlbynumber(String phonenumber) {
        String cleanNumber = phonenumber.replaceAll("[^0-9]", "");
        if (cleanNumber.startsWith("234")) {
            cleanNumber = cleanNumber.substring(3); // Remove country code
        }
        // remove leading zeros
        if (cleanNumber.startsWith("0")) {
            cleanNumber = cleanNumber.substring(1);
        }
        
        System.out.println("📱 Processing phone number: " + phonenumber + " -> " + cleanNumber);
        
        // MTN prefixes (more accurate)
        if (cleanNumber.startsWith("703") || cleanNumber.startsWith("706") ||
            cleanNumber.startsWith("803") || cleanNumber.startsWith("806") || 
            cleanNumber.startsWith("810") || cleanNumber.startsWith("813") ||
            cleanNumber.startsWith("814") || cleanNumber.startsWith("816") ||
            cleanNumber.startsWith("903") || cleanNumber.startsWith("906") ||
            cleanNumber.startsWith("913") || cleanNumber.startsWith("916")) {
            System.out.println("📡 Identified as MTN, using URL: " + aggregatorMtnUrl);
            return aggregatorMtnUrl;
        }
        
        // Airtel and 9mobile prefixes
        else if (cleanNumber.startsWith("701") || cleanNumber.startsWith("705") ||
                 cleanNumber.startsWith("708") || cleanNumber.startsWith("802") ||
                 cleanNumber.startsWith("808") || cleanNumber.startsWith("809") ||
                 cleanNumber.startsWith("812") || cleanNumber.startsWith("817") ||
                 cleanNumber.startsWith("818") || cleanNumber.startsWith("901") ||
                 cleanNumber.startsWith("902") || cleanNumber.startsWith("904") ||
                 cleanNumber.startsWith("907") || cleanNumber.startsWith("908") ||
                 cleanNumber.startsWith("909") || cleanNumber.startsWith("911") ||
                 cleanNumber.startsWith("912")) {
            System.out.println("📡 Identified as Airtel/9mobile, using URL: " + aggregatorAirtelOr9MobileUrl);
            return aggregatorAirtelOr9MobileUrl;
        } else {
            System.out.println("❌ Unsupported phone number prefix");
            return "Invalid phone number"; // Return an error message for unsupported numbers
        }
    }
}