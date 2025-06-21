package com.example.tems.Tems.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@Service
public class AggregatorService {
    //get the apikey
    @Value("${aggregator.api.key}")
    private String apiKey;
    private final ProductsService productsService;
    // constructor to inject the RestTemplate and ProductsService
    public AggregatorService(ProductsService productsService) {
        this.productsService = productsService;
    }
    public void initiateSubscription(String phoneNumber, String productID) {
        // get resttemplate bean
        RestTemplate restTemplate = new RestTemplate();
        //so now get host url by phone number
        String hostUrl = productsService.gethosturlbynumber(phoneNumber);
        if (hostUrl.equals("Invalid phone number")) {
            System.out.println("❌ Invalid phone number provided: " + phoneNumber);
            return;
        }
        // create the headers
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.set(productID, hostUrl);

        // create the request body
        Map<String, String> requestBody = Map.of(
            "phone_number", phoneNumber,
            "product_id", productID
        );
        // make the POST request to the aggregator API
        HttpEntity requestEntity = new HttpEntity<>(requestBody, headers);
        // try to make the request
        try {
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                hostUrl + "/subscription/initiate", 
                requestEntity, 
                String.class
            );
            System.out.println("✅ Subscription initiated successfully for phone number: " + phoneNumber + "with status code" + responseEntity.getStatusCode());
        } catch (Exception e) {
            System.out.println("❌ Error initiating subscription for phone number: " + phoneNumber);
            System.out.println("Error message: " + e.getMessage());
        }
    }
    

}
