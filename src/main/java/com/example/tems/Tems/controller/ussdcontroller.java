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
        // Check for FHIS enrollment flow first
        String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
        System.out.println("Current Flow: " + currentFlow + ", Step: " + step + ", Input: " + inputedText);
        
        // If user sends empty input and they're stuck in FHIS flow, reset
        if (currentFlow != null && currentFlow.equals("fhis_enrollment") && inputedText.isEmpty()) {
            resetUserSession(normalizedPhoneNumber);
            return HandleLevel1(normalizedPhoneNumber, parts, true);
        }
        
        if (currentFlow != null && currentFlow.equals("fhis_enrollment")) {
            return handleFHISEnrollmentFlow(normalizedPhoneNumber, inputedText);
        }

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
                clearSession(phone);
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
                return showMoreOptions(org);
            case "0": 
                return backToSearchResults(phone);
            default: 
                return "END Invalid choice";
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

    private String showMoreOptions(Organization org) {
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
        
        // Parse the input to get the last choice made
        String[] parts = inputText.split("\\*");
        String lastChoice = parts.length > 0 ? parts[parts.length - 1] : "";
        
        // Get or create enrollment record
        FhisEnrollment enrollment = GetorCreateFhisEnrollment(phoneNumber);
        String currentStep = enrollment.getCurrentStep();
        
        System.out.println("Current Step: " + currentStep + ", Last Choice: " + lastChoice);
        // Check if we're waiting for a continuation choice
        String continuationResult = handleContinuationChoice(phoneNumber, lastChoice, enrollment);
        if (continuationResult != null) {
            return continuationResult;
        }
        
        if (currentStep == null || currentStep.equals("sector_selection")) {
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
    }
    
    private String handleSectorSelection(String phone, String choice) {
        System.out.println("Sector Selection - Choice: " + choice);
        
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
            return "END Enrollment cancelled. Returning to main menu.";
        } else {
            return "CON Invalid selection. Please try again.\n" +
                   "Select enrollment type:\n" +
                   "1. Informal Sector\n" +
                   "2. Formal Sector (Coming Soon)\n" +
                   "0. Back to menu";
        }
    }

    private String handlePersonalData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Personal Data - Field: " + currentField + ", Input: " + inputText);
        
        if (currentField == null) {
            currentField = "fhisNo";
            saveToSession(phone, "currentField", currentField);
        }
        
        switch (currentField) {
            case "fhisNo":
                if (inputText == null || inputText.trim().isEmpty()) {
                    return "CON FHIS Number cannot be empty. Please enter your FHIS Number:";
                }
                enrollment.setFhisNo(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                fhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "title");
                return "CON Enter your Title (Mr/Mrs/Ms/Dr):";
            case "title":
                enrollment.setTitle(inputText.trim());
                saveToSession(phone, "currentField", "surname");
                return "CON Enter your Surname:";
            case "surname":
                enrollment.setSurname(inputText.trim());
                saveToSession(phone, "currentField", "firstName");
                return "CON Enter your First Name:";
            case "firstName":
                enrollment.setFirstName(inputText.trim());
                saveToSession(phone, "currentField", "middleName");
                return "CON Enter your Middle Name (optional):";
            case "middleName":
                enrollment.setMiddleName(inputText);
                saveToSession(phone, "currentField", "dateOfBirth");
                return "CON Enter your Date of Birth (YYYY-MM-DD):";
            case "dateOfBirth":
                if (!inputText.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return "CON Invalid date format. Please use YYYY-MM-DD:";
                }
                enrollment.setDateOfBirth(inputText);
                return moveToNextStage(phone, enrollment);
            default:
                return "END Invalid field. Please start over.";
        }
    }

    private String handleSocialData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        
        switch (currentField) {
            case "maritalStatus":
                enrollment.setMaritalStatus(inputText);
                saveToSession(phone, "currentField", "email");
                return "CON Enter your Email Address:";
            case "email":
                enrollment.setEmail(inputText);
                saveToSession(phone, "currentField", "bloodGroup");
                return "CON Enter your Blood Group:";
            case "bloodGroup":
                enrollment.setBloodGroup(inputText);
                saveToSession(phone, "currentField", "residentialAddress");
                return "CON Enter your Residential Address:";
            case "residentialAddress":
                enrollment.setResidentialAddress(inputText);
                saveToSession(phone, "currentField", "occupation");
                return "CON Enter your Occupation:";
            case "occupation":
                enrollment.setOccupation(inputText);
                return moveToNextStage(phone, enrollment);
            default:
                return "END Invalid field. Please start over.";
        }
    }

    private String handleCorporateData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        
        switch (currentField) {
            case "ninNumber":
                enrollment.setNinNumber(inputText);
                saveToSession(phone, "currentField", "telephoneNumber");
                return "CON Enter your Telephone Number:";
            case "telephoneNumber":
                enrollment.setTelephoneNumber(inputText);
                saveToSession(phone, "currentField", "organizationName");
                return "CON Enter your Organization Name:";
            case "organizationName":
                enrollment.setOrganizationName(inputText);
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
                return "END Enrollment submitted successfully! Thank you for enrolling in the FHIS program.";
            case "2":
                saveToSession(phone, "currentField", "fhisNo");
                return "CON Enter your FHIS Number to edit:";
            case "0":
                clearenrollmentSession(phone);
                return "END Enrollment cancelled. Returning to main menu.";
            default:
                return "END Invalid choice. Please try again.";
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
                return "END Enrollment cancelled. Returning to main menu.";
            } else if ("1".equals(choice)) {
                // Continue with the next field
                String currentField = (String) retrieveFromSession(phone, "currentField");
                return promptForNextField(currentField);
            } else if ("2".equals(choice) && enrollment.getCurrentStep().equals("corporate_data")) {
                clearenrollmentSession(phone);
                return "END Enrollment cancelled. Returning to main menu.";
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
           "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
           "FHIS: " + enrollment.getFhisNo();
    }

    private FhisEnrollment GetorCreateFhisEnrollment(String phoneNumber) {
        Optional<FhisEnrollment> existingEnrollment = fhisEnrollmentRepository.findByPhoneNumber(phoneNumber);

        if (existingEnrollment.isPresent()) {
            System.out.println("Found existing enrollment for phone: " + phoneNumber);
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
            // Clear all enrollment-related keys
            String[] keysToDelete = {
                phoneNumber + ":currentFlow",
                phoneNumber + ":enrollmentOrgId", 
                phoneNumber + ":currentField",
                phoneNumber + ":enrollmentData"  // If you store any temp enrollment data
            };
            
            // Also clear any FHIS-specific session keys
            Set<String> allKeys = redisTemplate.keys(phoneNumber + ":*");
            if (allKeys != null) {
                List<String> enrollmentKeys = allKeys.stream()
                    .filter(key -> key.contains("enrollment") || key.contains("fhis") || key.contains("currentFlow") || key.contains("currentField"))
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
}