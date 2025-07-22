package com.example.tems.Tems.controller;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.example.tems.Tems.Session.RedisConfig;
import com.example.tems.Tems.model.FhisEnrollment;
import com.example.tems.Tems.model.Organization;
import com.example.tems.Tems.repository.FhisEnrollmentRepository;
import com.example.tems.Tems.repository.OrganizationRepository;
import com.example.tems.Tems.service.AggregatorService;
import com.example.tems.Tems.service.SubscriptionService;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UssdController {
    
    private OrganizationRepository OrganizationRepository;
    private AggregatorService aggregatorService;
    private SubscriptionService subscriptionService;
    private FhisEnrollmentRepository fhisEnrollmentRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    public UssdController(OrganizationRepository organizationRepository, AggregatorService aggregatorService, SubscriptionService subscriptionService, 
                          FhisEnrollmentRepository fhisEnrollmentRepository) {
        this.OrganizationRepository = organizationRepository;
        this.aggregatorService = aggregatorService;
        this.subscriptionService = subscriptionService;
        this.fhisEnrollmentRepository = fhisEnrollmentRepository;
    }
    
    @PostMapping(
        value = "/ussd",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String handleUssdRequest(@RequestParam(name = "text", required = false) String inputText, @RequestParam(name = "phoneNumber") String phoneNumber) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
            return "END Invalid phone number provided.";
        }
        
        String inputedText = (inputText == null) ? "" : inputText;
        String[] parts = inputedText.isEmpty() ? new String[0] : inputedText.split("\\*");
        int step = parts.length;


        // informal sector enrollment flow
        // getting currentflow
        String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
        System.out.println("Current Flow: " + currentFlow);
        if (currentFlow != null && currentFlow.equals("fhis_enrollement")) {
            return handleFHISEnrollment(normalizedPhoneNumber, inputedText);
        }

        boolean hasActiveSession = true;
        switch(step) {
            case 0:
                clearSession(normalizedPhoneNumber); // Clear session only when starting fresh
                return HandleLevel1(normalizedPhoneNumber, parts, hasActiveSession);
            case 1: 
                return HandleLevel2(parts[0], normalizedPhoneNumber, parts);
            case 2: 
                Boolean isMoreResultsFlowObj3 = (Boolean) retrieveFromSession(normalizedPhoneNumber, "isMoreResultsFlow");
                boolean isMoreResultsFlow3 = Boolean.TRUE.equals(isMoreResultsFlowObj3);
                System.out.println("Case 3 - isMoreResultsFlow: " + isMoreResultsFlow3 + ", input: " + parts[1]);
                
                if (isMoreResultsFlow3) {
                    // If we are in more results flow, we should not clear the session
                    // Just handle the selection without clearing
                    System.out.println("Handling more results flow selection");
                    saveToSession(normalizedPhoneNumber, "isMoreResultsFlow", false);
                    return HandleLevel3(parts[1], normalizedPhoneNumber, parts);
                }
                return HandleLevel3(parts[1], normalizedPhoneNumber, parts);
            case 3: 
                // Check if this is actually a more results flow selection
                Boolean isMoreResultsFlowObj4 = (Boolean) retrieveFromSession(normalizedPhoneNumber, "isMoreResultsFlow");
                boolean isMoreResultsFlow4 = Boolean.TRUE.equals(isMoreResultsFlowObj4);
                System.out.println("Case 4 - isMoreResultsFlow: " + isMoreResultsFlow4 + ", input: " + parts[2]);
                
                if (isMoreResultsFlow4) {
                    // This is actually an organization selection from more results, not level 4
                    System.out.println("Handling organization selection from more results at step 4");
                    saveToSession(normalizedPhoneNumber, "isMoreResultsFlow", false);
                    return HandleLevel3(parts[1], normalizedPhoneNumber, parts);
                }
                return handleLevel4(parts[2], normalizedPhoneNumber, parts);
            case 4: 
                return handlelevel5(parts[3], normalizedPhoneNumber, parts);
            default: 
                return "END Session expired";
        }
    }

    private String HandleLevel1(String phone, String[] parts, Boolean hasActiveSession) {
        if (hasActiveSession) {
            return "CON Welcome to TEMS SERVICE\n" +
                    "Enter the name or initials of the organization you want to search for:\n";
        } else {
            return "CON Welcome to NIGERIAN TEMS SERVICE\n" +
                "Cost: ₦50/session:\n" + 
                "Please select an option:\n" +
                "1. Subscribe to Service\n" +
                "2. Exit";
        }
        
    }

    private String HandleLevel2(String text, String phone, String[] parts) {
        Pageable firstpage = PageRequest.of(0, 5);
        Page<Organization> results = handleOrganizationSearch(text, firstpage);
        saveToSession(phone, "isMoreResultsFlow", false); // Reset more results flow
        
        if (results.isEmpty()) {
            return "END No matches for: " + text;
        }
        
        // Save search data to session
        saveToSession(phone, "searchTerm", text);
        saveToSession(phone, "currentPage", 0);
        saveToSession(phone, "totalPages", (int) results.getTotalPages());
        
        // Save current page organizations
        List<Long> org_ids = results.getContent().stream()
            .map(Organization::getId)
            .collect(Collectors.toList());
        saveToSession(phone, "org_ids", org_ids);
        
        if (results.getContent().size() == 1) {
            // single result found, save it to session and show org menu
            Organization org = results.getContent().get(0);
            saveToSession(phone, "selectedOrgId", org.getId());
            return showorgmenu(org);
        }
        
        saveToSession(phone, "selectedOrgId", null); // Clear any previously selected organization ID
        return showOrganizationoptions(results.getContent(), 0, (int) results.getTotalPages());
    }

    private String showOrganizationoptions(List<Organization> organizations, int currentPage, int totalPages) {
        StringBuilder menu = new StringBuilder("CON Multiple matches found:\n");
        int count = 1;
        int iterationcount = Math.min(organizations.size(), 5);
        
        for (int i = 0; i < iterationcount; i++) {
            Organization org = organizations.get(i);
            menu.append(count).append(". ").append(org.getName()).append("\n");
            count++;
        }
        
        if (currentPage < totalPages - 1) {
            menu.append("6. More results\n");
        }
        menu.append("0. Back to main menu\n");
        return menu.toString();
    }

    private String showorgmenu(Organization orgofchoice){
        return "CON " + orgofchoice.getName() + "\n" +
               "1. Contact Info\n" +
               "2. Address\n" +
               "3. Description\n" + 
               "4. More\n" +
               "0. Back to search results"; 
    }

    private String HandleLevel3(String choice, String phone, String[] parts) {
        List<Long> orgids = getOrgIdsFromSession(phone);
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        
        System.out.println("HandleLevel3 - Phone: " + phone + ", Choice: " + choice + ", OrgIds: " + orgids);
        
        if (orgids == null || orgids.isEmpty()) {
            return "END No organizations found. Please try again.";
        }
        
        try {
            int selection = Integer.parseInt(choice);
            
            if (selection == 0) {
                clearSession(phone);
                return HandleLevel1(phone, parts, true); // Go back to Level 1
            }
            // check if we already have a selected organization ID in session
            if (selectedOrgId != null) {
                System.out.println("Selected Organization ID already exists in session: " + selectedOrgId);
                // If we have a selected organization, we can skip to Level 4
                return handleLevel4(choice, phone, parts);
            }
            // Handle "More results" - this should NOT advance to Level 4
            if (selection == 6) {
                return handleMoreResults(phone); // This returns CON, staying at Level 3
            }
            
            // Check if selection is valid for displayed options (max 5)
            int maxDisplayedOptions = Math.min(orgids.size(), 5);
            System.out.println("Max displayed options: " + maxDisplayedOptions + ", Selection: " + selection);
            
            if (selection < 1 || selection > maxDisplayedOptions) {
                return "END Invalid selection. Please try again.";
            }
            
            // Get the selected organization ID from the current page's org_ids
            Long selectedID = orgids.get(selection - 1);
            System.out.println("Selected ID: " + selectedID);
            
            // Save the selected organization ID to session
            saveToSession(phone, "selectedOrgId", selectedID);
            
            // Fetch the organization from database
            Optional<Organization> selectedOrgOptional = OrganizationRepository.findById(selectedID);
            if (!selectedOrgOptional.isPresent()) {
                return "END Organization not found. Please try again.";
            }
            
            Organization selectedOrg = selectedOrgOptional.get();
            System.out.println("Selected Organization: " + selectedOrg.getName());
            
            // Reset the more results flow flag since we've made a selection
            saveToSession(phone, "isMoreResultsFlow", false);
            
            return showorgmenu(selectedOrg);
            
        } catch (NumberFormatException e) {
            return "END Invalid input. Please enter a number.";
        } catch (Exception e) {
            System.err.println("Error in HandleLevel3: " + e.getMessage());
            e.printStackTrace(); // Add this for better debugging
            return "END An error occurred. Please try again.";
        }
    }
    private String handleLevel4(String choice, String phone, String[] parts) {
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        
        if (selectedOrgId == null) {
            return "END No organization selected. Please try again.";
        }
        
        Optional<Organization> orgOptional = OrganizationRepository.findById(selectedOrgId);
        if (!orgOptional.isPresent()) {
            return "END Organization not found. Please try again.";
        }
        
        Organization org = orgOptional.get();
        
        switch(choice) {
            case "1": 
                return "CON Contact Info:\nPhone: " + (org.getContactTelephone() != null ? org.getContactTelephone() : "Not available") + 
                       "\n\n0. Back to menu";
            case "2": 
                return "CON Address:\n" + (org.getContactAddress() != null ? org.getContactAddress() : "Not available") + 
                       "\n\n0. Back to menu";
            case "3": 
                return "CON Description:\n" + (org.getDescription() != null ? org.getDescription() : "Not available") + 
                       "\n\n0. Back to menu";
            case "4": 
                return showMoreOptions(org);
            case "0": 
                return backToSearchResults(phone);
            default: 
                return "END Invalid choice";
        }
    }

    private String handlelevel5(String choice, String phone, String[] parts) {
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        
        if (selectedOrgId == null) {
            return "END No organization selected. Please try again.";
        }
        
        Optional<Organization> orgOptional = OrganizationRepository.findById(selectedOrgId);
        if (!orgOptional.isPresent()) {
            return "END Organization not found. Please try again.";
        }
        
        Organization org = orgOptional.get();
        String orgName = org.getName().toUpperCase();
        
        if (orgName.contains("FHIS") || orgName.contains("FCT HEALTH") || orgName.contains("FCT HEALTH INSURANCE")) {
            switch(choice) {
                case "1": 
                    return handleFHISEnrollment(org, phone);
                case "0": 
                    return showorgmenu(org);
                default: 
                    return "END Invalid choice";
            }
        } else {
            switch(choice) {
                case "1":
                    return "END Contact Info:\nPhone: " + (org.getContactTelephone() != null ? org.getContactTelephone() : "Not available") + 
                           "\n\n0. Back to menu";
                case "2":
                    return "END Address:\n" + (org.getContactAddress() != null ? org.getContactAddress() : "Not available") + 
                           "\n\n0. Back to menu";
                case "3":
                    return "END Description:\n" + (org.getDescription() != null ? org.getDescription() : "Not available") + 
                           "\n\n0. Back to menu";
                case "4":
                    return showMoreOptions(org);
                case "0":
                    return backToSearchResults(phone);
            }
        }
        return "END Invalid choice";
    }

    private String backToSearchResults(String phone) {
        List<Long> orgids = getOrgIdsFromSession(phone);
        Integer currentPage = (Integer) retrieveFromSession(phone, "currentPage");
        Integer totalPages = (Integer) retrieveFromSession(phone, "totalPages");
        
        if (orgids == null || currentPage == null || totalPages == null) {
            return "END Session expired. Please start over.";
        }
        
        List<Organization> organizations = orgids.stream()
            .map(id -> OrganizationRepository.findById(id).orElse(null))
            .filter(org -> org != null)
            .collect(Collectors.toList());
            
        return showOrganizationoptions(organizations, currentPage, totalPages);
    }

    private String showMoreOptions(Organization org) {
        return "CON " + org.getName() + " - More Info:\n" +
               "1. Enroll\n" +
               "2. Change Hospital\n";
    }

     private String handleMoreResults(String phone) {
        String searchTerm = (String) retrieveFromSession(phone, "searchTerm");
        Integer currentPage = (Integer) retrieveFromSession(phone, "currentPage");
        Integer totalPages = (Integer) retrieveFromSession(phone, "totalPages");
        
        System.out.println("handleMoreResults - SearchTerm: " + searchTerm + ", CurrentPage: " + currentPage + ", TotalPages: " + totalPages);
        
        if (searchTerm == null || currentPage == null) {
            return "END No search term found. Please try again.";
        }
        
        int nextPage = currentPage + 1;
        
        if (nextPage >= totalPages) {
            return "END No more results found for: " + searchTerm;
        }
        
        Pageable pageable = PageRequest.of(nextPage, 5);
        Page<Organization> results = handleOrganizationSearch(searchTerm, pageable);
        
        if (results.isEmpty()) {
            return "END No more results found for: " + searchTerm;
        }
        
        // Update session with new page number
        saveToSession(phone, "currentPage", nextPage);
        
        // Save new org_ids
        List<Long> org_ids = results.getContent().stream()
            .map(Organization::getId)
            .collect(Collectors.toList());
        saveToSession(phone, "org_ids", org_ids);
        
        System.out.println("handleMoreResults - New org_ids: " + org_ids);
    
        // Save isMoreResultsFlow to true to indicate we are in more results flow
        saveToSession(phone, "isMoreResultsFlow", true);
        
        return showOrganizationoptions(results.getContent(), nextPage, totalPages);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        
        String normalized = phoneNumber.replaceAll("[^0-9]", "");
        
        if (normalized.startsWith("234") && normalized.length() == 13) {
            return "0" + normalized.substring(3);
        }
        if (normalized.length() == 10) {
            return "0" + normalized;
        }
        if (normalized.length() == 7) {
            return "09" + normalized;
        }
        return normalized;
    }

    private Page<Organization> handleOrganizationSearch(String searchTerm, Pageable pageable) {
        return OrganizationRepository.searchByNameOrInitialsContainingIgnoreCase(searchTerm, pageable);
    }

    // private String HandleinitialMenu(String phoneNumber, boolean hasActiveSession) {
    //     if (hasActiveSession) {
    //         return "CON Welcome back to NIGERIAN TEMS SERVICE\n" +
    //                "1. Search Organization\n" +
    //                "2. Exit";
    //     } else {
    //         return "CON Welcome to NIGERIAN TEMS SERVICE\n" +
    //             "Cost: ₦50/session:\n" + 
    //             "Please select an option:\n" +
    //             "1. Subscribe to Service\n" +
    //             "2. Exit";
    //     }
    // }

    private void saveToSession(String phoneNumber, String key, Object value) {
        try {
            String sessionkey = phoneNumber + ":" + key;
            System.out.println("Saving to session - Key: " + sessionkey + 
                              ", Type: " + (value != null ? value.getClass().getSimpleName() : "null") + 
                              ", Value: " + value);
            redisTemplate.opsForValue().set(sessionkey, value, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("Error saving to session: " + e.getMessage());
        }
    }
    private List<Long> getOrgIdsFromSession(String phone) {
        List<?> rawList = (List<?>) retrieveFromSession(phone, "org_ids");
        if (rawList == null) return null;
        
        return rawList.stream()
            .map(obj -> {
                if (obj instanceof Integer) {
                    return ((Integer) obj).longValue();
                } else if (obj instanceof Long) {
                    return (Long) obj;
                } else {
                    throw new ClassCastException("Unexpected type in org_ids");
                }
            })
            .collect(Collectors.toList());
    }
    private Long getLongFromSession(String phone, String key) {
        Object value = retrieveFromSession(phone, key);
        if (value == null) return null;
        
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        } else {
            throw new ClassCastException("Unexpected type for " + key + ": " + value.getClass());
        }
    }
    private Object retrieveFromSession(String phoneNumber, String key) {
        try {
            String sessionkey = phoneNumber + ":" + key;
            Object value = redisTemplate.opsForValue().get(sessionkey);
            System.out.println("Retrieved from session - Key: " + sessionkey + ", Value: " + value);
            return value;
        } catch (Exception e) {
            System.err.println("Error retrieving from session: " + e.getMessage());
            return null;
        }
    }

    private String handleFHISEnrollment(Organization org, String phone) {
        // save enrollement current flow
        saveToSession(phone, "currentFlow", "fhis_enrollement");
        saveToSession(phone, "enrollmentStep", "sector_selection");
        saveToSession(phone, "enrollmentOrgId", org.getId());

        // create new enrollment


        return "CON FHIS Enrollment Started\n" +
           "Select enrollment type:\n" +
           "1. Informal Sector\n" +
           "2. Formal Sector (Coming Soon)\n" +
           "0. Back to menu"; 
    }

    private void clearSession(String phoneNumber) {
        try {
            String sessionkey = phoneNumber + ":";
            Set <String> keys = redisTemplate.keys(sessionkey + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                System.out.println("Session cleared for phone: " + phoneNumber);
            } else {
                System.out.println("No session data found for phone: " + phoneNumber);
            }
        } catch (Exception e) {
            System.err.println("Error clearing session: " + e.getMessage());
        }
    }
    
    private String handleFHISEnrollment(String phoneNumber, String inputText) {
        return "chill";
    }

    // create or update fhis enrollment
    private FhisEnrollment GetorCreateFhisEnrollment(String phoneNumber) {
        // check for existing enrollment
        Optional<FhisEnrollment> existingEnrollment = fhisEnrollmentRepository.findByPhoneNumber(phoneNumber);

        if (existingEnrollment.isPresent()) {
            return existingEnrollment.get();
        }
        FhisEnrollment newEnrollment = new FhisEnrollment();
        newEnrollment.setPhoneNumber(phoneNumber);
        newEnrollment.setCreatedAt(LocalDateTime.now());
        newEnrollment.setUpdatedAt(LocalDateTime.now());
        newEnrollment.setCurrentStep("sector_selection");
        return fhisEnrollmentRepository.save(newEnrollment);
    }
    private void clearenrollmentSession(String phoneNumber) {
       try {
            Set<String> keys = redisTemplate.keys(phoneNumber + ":enrollment:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                System.out.println("Enrollment session cleared for phone: " + phoneNumber);
            } else {
                System.out.println("No enrollment session data found for phone: " + phoneNumber);
            }
            saveToSession(phoneNumber, "currentFlow", null); // Clear current flow

            saveToSession(phoneNumber, "enrollmentStep", null); // Clear enrollment step
            saveToSession(phoneNumber, "CurrentField", null); // Clear enrollment organization ID
       } catch (Exception e) {
            System.err.println("Error clearing enrollment session: " + e.getMessage());
        }

    }
}