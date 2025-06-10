package com.example.tems.Tems.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class ussdcontroller {
    @PostMapping(
        value = "/ussd",
        consumes = "application/x-www-form-urlencoded",
        produces = "text/plain"
    )
    public String handleUssdRequest(@RequestParam Map<String, String> requestParams) {
        // Add your logic here
        String sessionId = requestParams.get("sessionId");
        String serviceCode = requestParams.get("serviceCode");
        String phoneNumber = requestParams.get("phoneNumber");
        String text = requestParams.get("text");
        String networkCode = requestParams.get("networkCode");

        if (text == null || text.isEmpty()) {
            return "CON Welcome to NIGERIAN TEMS SERVICE\n" + 
                    "To get info on Nigerian organizations, enter the name or initials.\n" + 
                    "1. Search Organisation\n" +
                    "2. Exit";
        }
        String[] textParts = text.split("\\*"); // this splits the text input by the user into parts using '*' as a delimiter
        String response = textParts[0]; // the first part of the text input is the response
        switch (response) {
            case "1":
                return "Welcome to NIGERIAN TEMS SERVICE\n" + 
                       "To search for an organization, enter the name or initials.\n";

            case "2":
                return "END Thank you for using NIGERIAN TEMS SERVICE. Goodbye!";
            default:
                break;
        }
        if (textParts.length > 1) {
            String searchQuery = textParts[1].trim(); // this gets the second part of the text input which is the search query
            // Here you would typically call a service to search for the organization based on the searchQuery
            // For now, we will just return a dummy response
            return "CON You searched for: " + searchQuery + "\n" +
                   "1. View Details\n" +
                   "2. Back to Main Menu";
        }
        return "END Invalid input. Please try again.";
    }

}
