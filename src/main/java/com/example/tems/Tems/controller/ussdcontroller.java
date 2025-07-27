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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
    public UssdController(OrganizationRepository OrganizationRepository, AggregatorService aggregatorService, SubscriptionService subscriptionService, 
                          FhisEnrollmentRepository fhisEnrollmentRepository) {
        this.OrganizationRepository = OrganizationRepository;
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
        
        // Check for FHIS enrollment flow FIRST, before parsing steps
        String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
        System.out.println("Current Flow: " + currentFlow + ", Input: " + inputedText);
        
        // If user sends empty input and they're stuck in FHIS flow, reset
        if (currentFlow != null && currentFlow.equals("fhis_enrollment") && inputedText.isEmpty()) {
            resetUserSession(normalizedPhoneNumber);
            return HandleLevel1(normalizedPhoneNumber, new String[0], true);
        }
        
        // Handle FHIS enrollment flow - this takes precedence over step-based flow
        if (currentFlow != null && currentFlow.equals("fhis_enrollment")) {
            return handleFHISEnrollmentFlow(normalizedPhoneNumber, inputedText);
        }
        
        // Only parse steps for main USSD flow
        String[] parts = inputedText.isEmpty() ? new String[0] : inputedText.split("\\*");
        int step = parts.length;
        System.out.println("Main USSD Flow - Step: " + step + ", Input: " + inputedText);

        // Main USSD flow
        switch(step) {
            case 0:
                resetUserSession(normalizedPhoneNumber); 
                return HandleLevel1(normalizedPhoneNumber, parts, true);
            case 1: 
                return HandleLevel2(parts[0], normalizedPhoneNumber, parts);
            case 2: 
                return HandleLevel3(parts[1], normalizedPhoneNumber, parts);
            case 3: 
                return handleLevel4(parts[2], normalizedPhoneNumber, parts);
            case 4: 
                return handlelevel5(parts[3], normalizedPhoneNumber, parts);
            default: 
                resetUserSession(normalizedPhoneNumber); 
                return "END Session expired. Please start over.";
        }
    }

    private String HandleLevel1(String phone, String[] parts, Boolean hasActiveSession) {
        return "CON Welcome to TEMS SERVICE\n" +
                "Enter the name or initials of the organization you want to search for:\n";
    }

    private String HandleLevel2(String text, String phone, String[] parts) {
        Pageable firstpage = PageRequest.of(0, 5);
        Page<Organization> results = handleOrganizationSearch(text, firstpage);
        saveToSession(phone, "isMoreResultsFlow", false);
        
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
        
        // if (results.getContent().size() == 1) {
        //     Organization org = results.getContent().get(0);
        //     saveToSession(phone, "selectedOrgId", org.getId());
        //     return showorgmenu(org);
        // }
        
        saveToSession(phone, "selectedOrgId", null);
        return showOrganizationoptions(results.getContent(), 0, (int) results.getTotalPages());
    }

    private String showOrganizationoptions(List<Organization> organizations, int currentPage, int totalPages) {
        if (organizations == null || organizations.isEmpty()) {
            return "END No organizations found. Please try again.";
        }
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
        // Check if this is a "More results" flow first
        Boolean isMoreResultsFlowObj = (Boolean) retrieveFromSession(phone, "isMoreResultsFlow");
        boolean isMoreResultsFlow = Boolean.TRUE.equals(isMoreResultsFlowObj);
        
        if (isMoreResultsFlow) {
            saveToSession(phone, "isMoreResultsFlow", false);
            // Handle organization selection from more results
            return handleOrganizationSelection(choice, phone);
        }
        
        List<Long> orgids = getOrgIdsFromSession(phone);
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        
        System.out.println("HandleLevel3 - Phone: " + phone + ", Choice: " + choice + ", OrgIds: " + orgids + ", SelectedOrgId: " + selectedOrgId);

        // If we have a selected organization, show its menu
        if (selectedOrgId != null) {
            Optional<Organization> orgOptional = OrganizationRepository.findById(selectedOrgId);
            if (!orgOptional.isPresent()) {
                return "END Organization not found. Please try again.";
            }
            
            Organization org = orgOptional.get();
            return handleOrganizationMenu(choice, org, phone);
        }
        
        // Otherwise, handle organization selection from list
        return handleOrganizationSelection(choice, phone);
    }

    private String handleOrganizationSelection(String choice, String phone) {
        List<Long> orgids = getOrgIdsFromSession(phone);
        
        if (orgids == null || orgids.isEmpty()) {
            return "END No organizations found. Please try again.";
        }
        
        try {
            int selection = Integer.parseInt(choice);
            
            if (selection == 0) {
                // clearSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            }
        
            if (selection == 6) {
                return handleMoreResults(phone);
            }
            
            int maxDisplayedOptions = Math.min(orgids.size(), 5);
            if (selection < 1 || selection > maxDisplayedOptions) {
                return "END Invalid selection. Please try again.";
            }
            
            Long selectedID = orgids.get(selection - 1);
            saveToSession(phone, "selectedOrgId", selectedID);
            
            Optional<Organization> selectedOrgOptional = OrganizationRepository.findById(selectedID);
            if (!selectedOrgOptional.isPresent()) {
                return "END Organization not found. Please try again.";
            }
            
            Organization selectedOrg = selectedOrgOptional.get();
            return showorgmenu(selectedOrg);
            
        } catch (NumberFormatException e) {
            return "END Invalid input. Please enter a number.";
        } catch (Exception e) {
            System.err.println("Error in handleOrganizationSelection: " + e.getMessage());
            return "END An error occurred. Please try again.";
        }
    }

    private String handleOrganizationMenu(String choice, Organization org, String phone) {
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
                return showMoreOptions(phone, org);
            case "0": 
                return backToSearchResults(phone);
            default: 
                return "END Invalid choice";
        }
    }
    
    private String handleLevel4(String choice, String phone, String[] parts) {
        String currentSubMenu = (String) retrieveFromSession(phone, "currentSubMenu");
        if (currentSubMenu != null && currentSubMenu.equals("more_info")) {
            saveToSession(phone, "currentSubMenu", null);
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
                    case "2": 
                        return "CON Change Hospital - Coming Soon\n0. Back to menu";
                    case "0": 
                        return showorgmenu(org);
                    default: 
                        return "END Invalid choice";
                }
            }
        }
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
                case "2": 
                    return "CON Change Hospital - Coming Soon\n0. Back to menu";
                case "0": 
                    return showorgmenu(org);
                default: 
                    return "END Invalid choice";
            }
        }
        return handleOrganizationMenu(choice, org, phone);

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
            return handleOrganizationMenu(choice, org, phone);
        }
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

    private String showMoreOptions(String phone, Organization org) {
    saveToSession(phone, "currentSubMenu", "more_info");
        return "CON " + org.getName() + " - More Info:\n" +
               "1. Enroll\n" +
               "2. Change Hospital\n" +
               "0. Back to menu";
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
        
        saveToSession(phone, "currentPage", nextPage);
        
        List<Long> org_ids = results.getContent().stream()
            .map(Organization::getId)
            .collect(Collectors.toList());
        saveToSession(phone, "org_ids", org_ids);
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
        // Check if user already has an enrollment in progress or completed
        String existingCheck = checkExistingEnrollment(phone);
        if (existingCheck != null) {
            return existingCheck;
        }
        
        saveToSession(phone, "currentFlow", "fhis_enrollment");
        saveToSession(phone, "enrollmentOrgId", org.getId());
        
        return "CON FHIS Enrollment Started\n" +
           "Select enrollment type:\n" +
           "1. Informal Sector\n" +
           "2. Formal Sector (Coming Soon)\n" +
           "0. Back to menu"; 
    }

    private void clearSession(String phoneNumber) {
        try {
            String sessionkey = phoneNumber + ":";
            Set<String> keys = redisTemplate.keys(sessionkey + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                System.out.println("Session cleared for phone: " + phoneNumber);
            }
        } catch (Exception e) {
            System.err.println("Error clearing session: " + e.getMessage());
        }
    }
    
    // 6. Add a method to completely reset user session when they want to start fresh
    private void resetUserSession(String phoneNumber) {
        try {
            // Clear ALL session data for this phone number
            Set<String> allKeys = redisTemplate.keys(phoneNumber + ":*");
            if (allKeys != null && !allKeys.isEmpty()) {
                redisTemplate.delete(allKeys);
            }
            System.out.println("Complete session reset for phone: " + phoneNumber);
        } catch (Exception e) {
            System.err.println("Error resetting user session: " + e.getMessage());
        }
    }
    
    private String handleFHISEnrollmentFlow(String phoneNumber, String inputText) {
        System.out.println("FHIS Enrollment Flow - Phone: " + phoneNumber + ", Input: " + inputText);
        
        try {
            // Parse the input to get the last choice made
            String lastChoice = "";
            if (inputText != null && !inputText.isEmpty()) {
                String[] parts = inputText.split("\\*");
                lastChoice = parts.length > 0 ? parts[parts.length - 1] : "";
            }
            
            // Check for existing enrollment FIRST - but only on fresh entry (not when already handling choices)
            String existingFlow = (String) retrieveFromSession(phoneNumber, "existingEnrollmentFlow");
            if (existingFlow == null) {
                String existingCheck = checkExistingEnrollment(phoneNumber);
                if (existingCheck != null) {
                    return existingCheck;
                }
            }
            
            // Get or create enrollment record
            FhisEnrollment enrollment = GetorCreateFhisEnrollment(phoneNumber);
            if (enrollment == null) {
                clearenrollmentSession(phoneNumber);
                return "END Error retrieving enrollment. Please try again.";
            }
            
            String currentStep = enrollment.getCurrentStep();
            if (currentStep == null) {
                enrollment.setCurrentStep("sector_selection");
                fhisEnrollmentRepository.save(enrollment);
                currentStep = "sector_selection";
            }
            
            System.out.println("Current Step: " + currentStep + ", Last Choice: " + lastChoice);
            
            // Check if we're waiting for a continuation choice
            String continuationResult = handleContinuationChoice(phoneNumber, lastChoice, enrollment);
            if (continuationResult != null) {
                return continuationResult;
            }
            
            if (currentStep.equals("sector_selection")) {
                return handleSectorSelection(phoneNumber, lastChoice);
            }
            
            switch (currentStep) {
                case "personal_data":
                    return handlePersonalData(phoneNumber, lastChoice, enrollment);
                case "social_data":
                    return handleSocialData(phoneNumber, lastChoice, enrollment);
                case "corporate_data":
                    return handleCorporateData(phoneNumber, lastChoice, enrollment);
                case "completed":
                    return HandleEnrollmentCompletion(phoneNumber, lastChoice, enrollment);
                default:
                    clearenrollmentSession(phoneNumber);
                    return "END Invalid enrollment step. Please start over.";
            }
        } catch (Exception e) {
            System.err.println("Error in FHIS enrollment flow: " + e.getMessage());
            clearenrollmentSession(phoneNumber);
            return "END An error occurred. Please try again.";
        }
    }
    
    private String checkExistingEnrollment(String phoneNumber) {
        try {
            Optional<FhisEnrollment> existing = fhisEnrollmentRepository.findByPhoneNumber(phoneNumber);
            if (existing.isPresent()) {
                FhisEnrollment enrollment = existing.get();
                String currentStep = enrollment.getCurrentStep();
                
                if ("completed".equals(currentStep)) {
                    return "CON You already have a completed FHIS enrollment.\n" +
                           "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                           "FHIS No: " + enrollment.getFhisNo() + "\n\n" +
                           "1. Start new enrollment\n" +
                           "2. View details\n" +
                           "0. Back to menu";
                } else if (currentStep != null && !currentStep.equals("sector_selection")) {
                    return "CON Existing enrollment found (Incomplete).\n" +
                           "Progress: " + getProgressPercentage(currentStep) + "%\n\n" +
                           "1. Continue existing\n" +
                           "2. Start fresh\n" +
                           "0. Back to menu";
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking existing enrollment: " + e.getMessage());
        }
        return null;
    }
    
    private String getProgressPercentage(String step) {
        switch (step) {
            case "personal_data": return "25";
            case "social_data": return "50";
            case "corporate_data": return "75";
            case "completed": return "100";
            default: return "0";
        }
    }
    
    private String handleSectorSelection(String phone, String choice) {
        System.out.println("Sector Selection - Choice: " + choice);
        
        // Handle existing enrollment options first
        String existingFlow = (String) retrieveFromSession(phone, "existingEnrollmentFlow");
        if ("true".equals(existingFlow)) {
            saveToSession(phone, "existingEnrollmentFlow", null);
            return handleExistingEnrollmentChoice(phone, choice);
        }
        
        if (choice.equals("1")) {
            FhisEnrollment enrollment = GetorCreateFhisEnrollment(phone);
            enrollment.setEnrollmentType("Informal");
            enrollment.setCurrentStep("personal_data");
            fhisEnrollmentRepository.save(enrollment);
            saveToSession(phone, "currentField", "fhisNo");
            return "CON INFORMAL SECTOR\nEnter your FHIS Number:";
        } else if (choice.equals("2")) {
            return "END Formal sector enrollment is coming soon. Please check back later.";
        } else if (choice.equals("0")) {
            clearenrollmentSession(phone);
            resetUserSession(phone);
            return HandleLevel1(phone, new String[0], true);
        } else {
            return "CON Invalid selection. Please try again.\n" +
                   "Select enrollment type:\n" +
                   "1. Informal Sector\n" +
                   "2. Formal Sector (Coming Soon)\n" +
                   "0. Back to menu";
        }
    }
    
    private String handleExistingEnrollmentChoice(String phone, String choice) {
        try {
            Optional<FhisEnrollment> existing = fhisEnrollmentRepository.findByPhoneNumber(phone);
            if (!existing.isPresent()) {
                clearenrollmentSession(phone);
                return "END Enrollment not found. Please try again.";
            }
            
            FhisEnrollment enrollment = existing.get();
            
            switch (choice) {
                case "1": // Continue existing or start new
                    if ("completed".equals(enrollment.getCurrentStep())) {
                        // Delete existing and start fresh
                        fhisEnrollmentRepository.delete(enrollment);
                        clearenrollmentSession(phone);
                        return "CON FHIS Enrollment Started\n" +
                               "Select enrollment type:\n" +
                               "1. Informal Sector\n" +
                               "2. Formal Sector (Coming Soon)\n" +
                               "0. Back to menu";
                    } else {
                        // Continue existing
                        String currentStep = enrollment.getCurrentStep();
                        saveToSession(phone, "currentField", getCurrentFieldForStep(currentStep));
                        return resumeEnrollmentStep(enrollment);
                    }
                case "2": // View details or start fresh
                    if ("completed".equals(enrollment.getCurrentStep())) {
                        return "CON Enrollment Details:\n" +
                               "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                               "FHIS: " + enrollment.getFhisNo() + "\n" +
                               "Type: " + enrollment.getEnrollmentType() + "\n\n" +
                               "0. Back to menu";
                    } else {
                        // Delete existing and start fresh
                        fhisEnrollmentRepository.delete(enrollment);
                        clearenrollmentSession(phone);
                        return "CON FHIS Enrollment Started\n" +
                               "Select enrollment type:\n" +
                               "1. Informal Sector\n" +
                               "2. Formal Sector (Coming Soon)\n" +
                               "0. Back to menu";
                    }
                case "0":
                    clearenrollmentSession(phone);
                    resetUserSession(phone);
                    return HandleLevel1(phone, new String[0], true);
                default:
                    return "CON Invalid choice. Please select:\n1, 2, or 0";
            }
        } catch (Exception e) {
            System.err.println("Error handling existing enrollment choice: " + e.getMessage());
            clearenrollmentSession(phone);
            return "END An error occurred. Please try again.";
        }
    }
    
    private String getCurrentFieldForStep(String step) {
        switch (step) {
            case "personal_data": return "fhisNo";
            case "social_data": return "maritalStatus";
            case "corporate_data": return "ninNumber";
            default: return "fhisNo";
        }
    }
    
    private String resumeEnrollmentStep(FhisEnrollment enrollment) {
        String currentStep = enrollment.getCurrentStep();
        switch (currentStep) {
            case "personal_data":
                if (enrollment.getFhisNo() == null) {
                    return "CON INFORMAL SECTOR\nEnter your FHIS Number:";
                } else if (enrollment.getTitle() == null) {
                    return "CON Enter your Title (Mr/Mrs/Ms/Dr):";
                } else if (enrollment.getSurname() == null) {
                    return "CON Enter your Surname:";
                } else if (enrollment.getFirstName() == null) {
                    return "CON Enter your First Name:";
                } else if (enrollment.getMiddleName() == null) {
                    return "CON Enter your Middle Name (optional):";
                } else {
                    return "CON Enter your Date of Birth (YYYY-MM-DD):";
                }
            case "social_data":
                if (enrollment.getMaritalStatus() == null) {
                    return "CON Enter your Marital Status:";
                } else if (enrollment.getEmail() == null) {
                    return "CON Enter your Email Address:";
                } else if (enrollment.getBloodGroup() == null) {
                    return "CON Enter your Blood Group:";
                } else if (enrollment.getResidentialAddress() == null) {
                    return "CON Enter your Residential Address:";
                } else {
                    return "CON Enter your Occupation:";
                }
            case "corporate_data":
                if (enrollment.getNinNumber() == null) {
                    return "CON Enter your NIN Number:";
                } else if (enrollment.getTelephoneNumber() == null) {
                    return "CON Enter your Telephone Number:";
                } else {
                    return "CON Enter your Organization Name:";
                }
            default:
                return "CON Resume enrollment from where you left off:";
        }
    }

    private String handlePersonalData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Personal Data - Field: " + currentField + ", Input: " + inputText);
        
        if (currentField == null) {
            currentField = "fhisNo";
            saveToSession(phone, "currentField", currentField);
        }
        
        // Validate input is not empty (except for optional fields)
        if ((inputText == null || inputText.trim().isEmpty()) && !currentField.equals("middleName")) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
        
        switch (currentField) {
            case "fhisNo":
                if (!isValidFhisNumber(inputText.trim())) {
                    return "CON Invalid FHIS Number format. Please enter a valid FHIS Number:";
                }
                enrollment.setFhisNo(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "title");
                return "CON Enter your Title (Mr/Mrs/Ms/Dr):";
            case "title":
                if (!isValidTitle(inputText.trim())) {
                    return "CON Invalid title. Please enter Mr, Mrs, Ms, or Dr:";
                }
                enrollment.setTitle(inputText.trim());
                saveToSession(phone, "currentField", "surname");
                return "CON Enter your Surname:";
            case "surname":
                if (!isValidName(inputText.trim())) {
                    return "CON Invalid surname format. Please enter a valid surname:";
                }
                enrollment.setSurname(inputText.trim());
                saveToSession(phone, "currentField", "firstName");
                return "CON Enter your First Name:";
            case "firstName":
                if (!isValidName(inputText.trim())) {
                    return "CON Invalid first name format. Please enter a valid first name:";
                }
                enrollment.setFirstName(inputText.trim());
                saveToSession(phone, "currentField", "middleName");
                return "CON Enter your Middle Name (optional):";
            case "middleName":
                enrollment.setMiddleName(inputText != null ? inputText.trim() : "");
                saveToSession(phone, "currentField", "dateOfBirth");
                return "CON Enter your Date of Birth (YYYY-MM-DD):";
            case "dateOfBirth":
                if (!isValidDateOfBirth(inputText)) {
                    return "CON Invalid date format or future date. Please use YYYY-MM-DD:";
                }
                enrollment.setDateOfBirth(inputText);
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
            default:
                return "END Invalid field. Please start over.";
        }
    }

    private String handleSocialData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Social Data - Field: " + currentField + ", Input: " + inputText);
        
        if (inputText == null || inputText.trim().isEmpty()) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
        
        switch (currentField) {
            case "maritalStatus":
                if (!isValidMaritalStatus(inputText.trim())) {
                    return "CON Invalid marital status. Please enter Single, Married, Divorced, or Widowed:";
                }
                enrollment.setMaritalStatus(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "email");
                return "CON Enter your Email Address:";
            case "email":
                if (!isValidEmail(inputText.trim())) {
                    return "CON Invalid email format. Please enter a valid email address:";
                }
                enrollment.setEmail(inputText.trim());
                saveToSession(phone, "currentField", "bloodGroup");
                return "CON Enter your Blood Group:";
            case "bloodGroup":
                if (!isValidBloodGroup(inputText.trim())) {
                    return "CON Invalid blood group. Please enter A+, A-, B+, B-, AB+, AB-, O+, or O-:";
                }
                enrollment.setBloodGroup(inputText.trim());
                saveToSession(phone, "currentField", "residentialAddress");
                return "CON Enter your Residential Address:";
            case "residentialAddress":
                enrollment.setResidentialAddress(inputText.trim());
                saveToSession(phone, "currentField", "occupation");
                return "CON Enter your Occupation:";
            case "occupation":
                enrollment.setOccupation(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
            default:
                return "END Invalid field. Please start over.";
        }
    }

    private String handleCorporateData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Corporate Data - Field: " + currentField + ", Input: " + inputText);
        
        if (inputText == null || inputText.trim().isEmpty()) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
        
        switch (currentField) {
            case "ninNumber":
                if (!isValidNinNumber(inputText.trim())) {
                    return "CON Invalid NIN format. Please enter an 11-digit NIN:";
                }
                enrollment.setNinNumber(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "telephoneNumber");
                return "CON Enter your Telephone Number:";
            case "telephoneNumber":
                if (!isValidPhoneNumber(inputText.trim())) {
                    return "CON Invalid phone number format. Please enter a valid phone number:";
                }
                enrollment.setTelephoneNumber(inputText.trim());
                saveToSession(phone, "currentField", "organizationName");
                return "CON Enter your Organization Name:";
            case "organizationName":
                enrollment.setOrganizationName(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
            default:
                return "END Invalid field. Please start over.";
        }
    }

    private String HandleEnrollmentCompletion(String phone, String inputText, FhisEnrollment enrollment) {
        switch(inputText) {
            case "1":
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                clearenrollmentSession(phone);
                return "END Enrollment submitted successfully! Thank you for enrolling in the FHIS program. Your reference number is: " + enrollment.getFhisNo();
            case "2":
                // Allow editing - reset to personal data step
                enrollment.setCurrentStep("personal_data");
                fhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "fhisNo");
                return "CON EDIT MODE - Enter your FHIS Number:";
            case "0":
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            default:
                return "CON Invalid choice. Please select:\n1. Confirm Enrollment\n2. Edit Details\n0. Cancel Enrollment";
        }
    }

    private String moveToNextStage(String phone, FhisEnrollment enrollment) {
        switch(enrollment.getCurrentStep()) {
            case "personal_data":
                enrollment.setCurrentStep("social_data");
                fhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "maritalStatus");
                saveToSession(phone, "waitingForContinue", true);  // Flag to wait for user choice
                return "CON Personal data saved! (25% done)\n1. Continue to social data\n0. Back";
                
            case "social_data":
                enrollment.setCurrentStep("corporate_data");
                fhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "ninNumber");
                saveToSession(phone, "waitingForContinue", true);  // Flag to wait for user choice
                return "CON 50% completed! Social Details Saved.\n\n" +
                        "We will now ask for your corporate data.\n" +
                        "1. Continue\n" +
                        "2. Cancel Enrollment";
                        
            case "corporate_data":
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                return "CON 75% completed! Almost done.\n\n" +
                        showEnrollmentSummary(enrollment) + 
                        "\n1. Confirm Enrollment\n" +
                        "2. Edit Details\n" +
                        "0. Cancel Enrollment";
            default:
                return "END Enrollment submitted successfully! Thank you for enrolling in the FHIS program.";
        }
    }
    
    private String handleContinuationChoice(String phone, String choice, FhisEnrollment enrollment) {
        Boolean waitingForContinue = (Boolean) retrieveFromSession(phone, "waitingForContinue");
        
        if (Boolean.TRUE.equals(waitingForContinue)) {
            saveToSession(phone, "waitingForContinue", false);  // Clear the flag
            
            if ("0".equals(choice)) {
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            } else if ("1".equals(choice)) {
                // Continue with the next field
                String currentField = (String) retrieveFromSession(phone, "currentField");
                return promptForNextField(currentField);
            } else if ("2".equals(choice) && enrollment.getCurrentStep().equals("corporate_data")) {
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            } else {
                return "CON Invalid choice. Please select:\n1. Continue\n0. Back";
            }
        }
        
        return null; // Not waiting for continuation, proceed with normal flow
    }
    
    private String promptForNextField(String currentField) {
        switch (currentField) {
            case "maritalStatus":
                return "CON Enter your Marital Status:";
            case "ninNumber":
                return "CON Enter your NIN Number:";
            default:
                return "CON Continue with enrollment:";
        }
    }

    private String showEnrollmentSummary(FhisEnrollment enrollment) {
        return "REVIEW:\n" +
           "Name: " + enrollment.getTitle() + " " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
           "FHIS: " + enrollment.getFhisNo() + "\n" +
           "Email: " + enrollment.getEmail() + "\n" +
           "Phone: " + enrollment.getTelephoneNumber();
    }

    private FhisEnrollment GetorCreateFhisEnrollment(String phoneNumber) {
        try {
            Optional<FhisEnrollment> existingEnrollment = fhisEnrollmentRepository.findByPhoneNumber(phoneNumber);

            if (existingEnrollment.isPresent()) {
                System.out.println("Found existing enrollment for phone: " + phoneNumber);
                FhisEnrollment enrollment = existingEnrollment.get();
                
                // Check if it's a completed enrollment that needs to be handled differently
                if ("completed".equals(enrollment.getCurrentStep())) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "true");
                }
                
                return enrollment;
            }
            
            FhisEnrollment newEnrollment = new FhisEnrollment();
            newEnrollment.setPhoneNumber(phoneNumber);
            newEnrollment.setCreatedAt(LocalDateTime.now());
            newEnrollment.setUpdatedAt(LocalDateTime.now());
            newEnrollment.setCurrentStep("sector_selection");
            return fhisEnrollmentRepository.save(newEnrollment);
        } catch (Exception e) {
            System.err.println("Error getting or creating FHIS enrollment: " + e.getMessage());
            return null;
        }
    }
    
    private void clearenrollmentSession(String phoneNumber) {
        try {
            // Clear all enrollment-related keys
            String[] keysToDelete = {
                phoneNumber + ":currentFlow",
                phoneNumber + ":enrollmentOrgId", 
                phoneNumber + ":currentField",
                phoneNumber + ":waitingForContinue",
                phoneNumber + ":existingEnrollmentFlow"
            };
            
            // Also clear any FHIS-specific session keys
            Set<String> allKeys = redisTemplate.keys(phoneNumber + ":*");
            if (allKeys != null) {
                List<String> enrollmentKeys = allKeys.stream()
                    .filter(key -> key.contains("enrollment") || key.contains("fhis") || key.contains("currentFlow") || key.contains("currentField") || key.contains("waitingForContinue"))
                    .collect(Collectors.toList());
                
                if (!enrollmentKeys.isEmpty()) {
                    redisTemplate.delete(enrollmentKeys);
                }
            }
            
            redisTemplate.delete(Arrays.asList(keysToDelete));
            System.out.println("Comprehensive enrollment session cleared for phone: " + phoneNumber);
        } catch (Exception e) {
            System.err.println("Error clearing enrollment session: " + e.getMessage());
        }
    }
    
    // Validation helper methods
    private boolean isValidFhisNumber(String fhisNo) {
        if (fhisNo == null || fhisNo.isEmpty()) return false;
        // Add your FHIS number validation logic here (e.g., format, length)
        return fhisNo.matches("^[A-Za-z0-9]{6,20}$"); // Example: alphanumeric, 6-20 chars
    }
    
    private boolean isValidTitle(String title) {
        if (title == null) return false;
        String upperTitle = title.toUpperCase();
        return upperTitle.equals("MR") || upperTitle.equals("MRS") || upperTitle.equals("MS") || upperTitle.equals("DR");
    }
    
    private boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        // Allow letters, spaces, hyphens, and apostrophes
        return name.matches("^[a-zA-Z\\s\\-']{2,50}$");
    }
    
    private boolean isValidDateOfBirth(String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalDate now = LocalDate.now();
            LocalDate minDate = now.minusYears(100); // No one older than 100
            return !date.isAfter(now) && !date.isBefore(minDate);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isValidMaritalStatus(String status) {
        if (status == null) return false;
        String upperStatus = status.toUpperCase();
        return upperStatus.equals("SINGLE") || upperStatus.equals("MARRIED") || 
               upperStatus.equals("DIVORCED") || upperStatus.equals("WIDOWED");
    }
    
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
    
    private boolean isValidBloodGroup(String bloodGroup) {
        if (bloodGroup == null) return false;
        String upper = bloodGroup.toUpperCase();
        return upper.matches("^(A|B|AB|O)[+-]$");
    }
    
    private boolean isValidNinNumber(String nin) {
        if (nin == null) return false;
        return nin.matches("^\\d{11}$"); // 11 digits
    }
    
    private boolean isValidPhoneNumber(String phone) {
        if (phone == null) return false;
        String normalized = phone.replaceAll("[^0-9]", "");
        return normalized.length() >= 10 && normalized.length() <= 14;
    }
    
    private String getFieldDisplayName(String fieldName) {
        switch (fieldName) {
            case "fhisNo": return "FHIS Number";
            case "title": return "Title";
            case "surname": return "Surname";
            case "firstName": return "First Name";
            case "middleName": return "Middle Name";
            case "dateOfBirth": return "Date of Birth";
            case "maritalStatus": return "Marital Status";
            case "email": return "Email Address";
            case "bloodGroup": return "Blood Group";
            case "residentialAddress": return "Residential Address";
            case "occupation": return "Occupation";
            case "ninNumber": return "NIN Number";
            case "telephoneNumber": return "Telephone Number";
            case "organizationName": return "Organization Name";
            default: return "the required information";
        }
    }
}