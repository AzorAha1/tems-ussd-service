package com.example.tems.Tems.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
// @RequestMapping("/api")
public class UssdController {
    @PostMapping(
        value = "/ussd",  // <- Direct root path
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String testEndpoint(@RequestParam(required = false) String text) {
        System.out.println("RAW INPUT: " + text);
        if (text == null || text.isEmpty()) {
            return "CON Welcome to NIGERIAN TEMS SERVICE\n1. Search Organization\n2. Exit";
        }
        String[] parts = text.split("\\*");
        String lastInput = parts[parts.length - 1];
        switch (lastInput) {
            case "1":
                if (parts.length == 1) { // First level selection
                    return "CON Enter organization name:";
                } else { // Organization name entered
                    String orgName = lastInput;
                    return "END Searching for: " + orgName;
                }
                
            case "2":
                return "END Thank you for using TEMS";
                
            default:
                return "END Invalid option selected";
        }

        
    }
}