package com.example.tems.Tems.controller;

import java.util.Optional;
import com.example.tems.Tems.model.Organization;
import com.example.tems.Tems.repository.OrganizationRepository;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
// @RequestMapping("/api")
public class UssdController {
    @Autowired
    private OrganizationRepository OrganizationRepository;
    @PostMapping(
    value = "/ussd",
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

    if (parts.length == 1 && lastInput.equals("1")) {
        return "CON Enter Organization Name";
    } else if (parts.length == 2 && lastInput.length() > 0) {
        List<Organization> organizations = OrganizationRepository.findByNameContainingIgnoreCase(lastInput);
        if (!organizations.isEmpty()) {
            Organization org = organizations.get(0);
            return "END Organization Found:\nName: " + org.getName() +
                   "\nAddress: " + org.getContactAddress() +
                   "\nDescription: " + org.getDescription() +
                   "\nTelephone: " + org.getContactTelephone();
        } else {
            return "END Organization not found. Please try again.";
        }
    } else if (lastInput.equals("2")) {
        return "END Thank you for using NIGERIAN TEMS SERVICE";
    } else {
        return "END Invalid option. Please try again.";
    }
}
}