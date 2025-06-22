package com.example.tems.Tems.controller;

import java.util.Optional;
import com.example.tems.Tems.model.Organization;
import com.example.tems.Tems.repository.OrganizationRepository;
import com.example.tems.Tems.service.AggregatorService;
import com.example.tems.Tems.service.SubscriptionService;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
// @RequestMapping("/api")
public class UssdController {
    // @Value("${aggregator.product.id}")
    // private String productID;
    // organisation repository helps us to fetch organization details from the database
    private OrganizationRepository OrganizationRepository;
    // aggregator service helps us to initiate subscription with the aggregator which is payment
    private AggregatorService aggregatorService;
    // Subscription service will help us start and manage sessions
    private SubscriptionService subscriptionService;
    @Autowired
    public UssdController(OrganizationRepository organizationRepository, AggregatorService aggregatorService, SubscriptionService subscriptionService) {
        this.OrganizationRepository = organizationRepository;
        this.aggregatorService = aggregatorService;
        this.subscriptionService = subscriptionService;
    }
    @PostMapping(
    value = "/ussd",
    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String handleUssdRequest(@RequestParam(required = false) String text, @RequestParam String phone) {
        String normalizedPhoneNumber = normalizePhoneNumber(phone);
        // Check if the phone number is valid
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
            return "END Invalid phone number provided.";
        }
        String inputText = (text == null) ? "" : text;
        // Split the text input by '*'
        String[] parts = inputText.isEmpty() ? new String[0] : inputText.split("\\*");
        String lastinput = parts.length > 0 ? parts[parts.length - 1] : "";
        // Check if the user has an active session
        boolean hasActiveSession = subscriptionService.hasActiveSession(normalizedPhoneNumber);
        // If the text is empty, show the initial menu
        if (inputText.isEmpty()) {
            return HandleinitialMenu(normalizedPhoneNumber, hasActiveSession);
        }
        // If the user has an active session, handle the service flow
        if (!hasActiveSession) {
            return handlePaymentFlow(normalizedPhoneNumber, parts, lastinput);
        }
        return handleServiceFlow(normalizedPhoneNumber, parts, lastinput);
    }
    //method to handle incoming phone numbers
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        
        String normalized = phoneNumber.replaceAll("[^0-9]", "");
        
        if (normalized.startsWith("234") && normalized.length() == 13) {
            return "0" + normalized.substring(3);
        } else if (!normalized.startsWith("0") && normalized.length() == 10) {
            return "0" + normalized;
        } else if (normalized.length() == 11 && normalized.startsWith("0")) {
            return normalized;
        }
        
        return phoneNumber; // fallback
    }
    // method to search organisations
    private String handleOrganizationSearch(String Searchterm) {
        List <Organization> organizations = OrganizationRepository.findByNameContainingIgnoreCase(Searchterm);
        if (organizations.isEmpty()) {
            return "No organizations found for the search term: " + Searchterm;
        }
        Organization org = organizations.get(0); // Get the first organization
        return "END Organization Found:\nName: " + org.getName() +
                   "\nAddress: " + org.getContactAddress() +
                   "\nDescription: " + org.getDescription() +
                   "\nTelephone: " + org.getContactTelephone();
    }
    //method to handle initial menu
    private String HandleinitialMenu(String phoneNumber, boolean hasActiveSession) {
        if (hasActiveSession) {
            return "CON Welcome back to NIGERIAN TEMS SERVICE\n" +
                   "1. Search Organization\n" +
                   "2. Exit";
        } else {
            return "CON Welcome to NIGERIAN TEMS SERVICE\n" +
                "Cost: ₦50/session:\n" + 
                "Please select an option:\n" +
                "1. Subscribe to Service\n" +
                "2. Exit";
        }
    }
    //method to handle payflow
    private String handlePaymentFlow(String phoneNumber, String[] parts, String lastinput) {
        if (parts.length == 1 && lastinput.equals("1")) {
            try {
                aggregatorService.initiateSubscription(phoneNumber);
                // Update success message to match actual flow
                return "END Confirm payment via SMS to access";
            } catch (Exception e) {
                return "END Payment initiation failed. Please try again later.";
            }
        } else if (lastinput.equals("2")) {
            return "END Thank you for using NIGERIAN TEMS SERVICE";
        } else {
            return "END Invalid input. Please try again.";
        }
    }
    // method to handle service flow if user is subscribed
    private String handleServiceFlow(String phoneNumber, String[] parts, String lastinput) {
        // user just entered 1
        if (parts.length == 1 && lastinput.equals("1")) {
            return "CON Enter Organization Name to Search:";
       // Example: text = "1*Shell" → parts.length == 2 → lastInput = "Shell"
        } else if (parts.length == 2 && lastinput != null && !lastinput.isEmpty()) {
            return handleOrganizationSearch(lastinput);
        // user just entered 2
        
        } else if (lastinput.equals("2")) {
            return "END Thank you for using NIGERIAN TEMS SERVICE\"";
        } else {
            return "END Invalid input. Please try again.";
        }
    }
}