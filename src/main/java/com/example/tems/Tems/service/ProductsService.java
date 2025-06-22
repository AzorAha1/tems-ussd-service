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
            "status", "LIVE"
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(aggregatorApiKey);
        headers.set("Content-Type", "application/json");

        try {
            System.out.println("🚀 Making request to: " + hosturl + "/products");
            System.out.println("📦 Request payload: " + product);
            System.out.println("🔑 Using API key: " + aggregatorApiKey.substring(0, 5) + "...");
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                hosturl + "/product",
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
        
        // Keep leading zero for proper prefix detection
        if (cleanNumber.startsWith("234")) {
            cleanNumber = "0" + cleanNumber.substring(3);
        }
        
        System.out.println("📱 Processing phone number: " + phonenumber + " -> " + cleanNumber);
        
        // MTN prefixes
        if (cleanNumber.matches("^(0703|0706|0803|0806|0810|0813|0814|0816|0903|0906|0913|0916).*")) {
            System.out.println("📡 Identified as MTN");
            return aggregatorMtnUrl;
        }
        // 9mobile prefixes
        else if (cleanNumber.matches("^(0809|0817|0818|0909|0908|0911).*")) {
            System.out.println("📡 Identified as 9mobile");
            return aggregatorAirtelOr9MobileUrl;
        }
        // Default to Airtel
        System.out.println("📡 Identified as Airtel");
        return aggregatorAirtelOr9MobileUrl;
    }
}