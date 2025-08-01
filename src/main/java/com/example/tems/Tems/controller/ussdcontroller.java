package com.example.tems.Tems.controller;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.example.tems.Tems.Session.RedisConfig;
import com.example.tems.Tems.model.InformalFhisEnrollment;
import com.example.tems.Tems.model.Organization;
import com.example.tems.Tems.model.FormalFhisEnrollment;
import com.example.tems.Tems.repository.FormalFhisEnrollmentRepository;
import com.example.tems.Tems.repository.InformalFhisEnrollmentRepository;
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

    // FIXED: Renamed variable to follow camelCase convention
    private OrganizationRepository organizationRepository;
    private AggregatorService aggregatorService;
    private SubscriptionService subscriptionService;
    private InformalFhisEnrollmentRepository InformalfhisEnrollmentRepository;
    private FormalFhisEnrollmentRepository formalFhisEnrollmentRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // FIXED: Renamed constructor parameter and assignment
    @Autowired
    public UssdController(OrganizationRepository organizationRepository, AggregatorService aggregatorService, SubscriptionService subscriptionService,
            InformalFhisEnrollmentRepository InformalfhisEnrollmentRepository, FormalFhisEnrollmentRepository formalFhisEnrollmentRepository) {
        this.organizationRepository = organizationRepository;
        this.aggregatorService = aggregatorService;
        this.subscriptionService = subscriptionService;
        this.InformalfhisEnrollmentRepository = InformalfhisEnrollmentRepository;
        this.formalFhisEnrollmentRepository = formalFhisEnrollmentRepository;
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
        String inputedText = (inputText == null) ? "" : inputText.trim();
        // Only remove # at the very end of input, not everywhere
        if (inputedText.endsWith("#")) {
            inputedText = inputedText.substring(0, inputedText.length() - 1);
        }
        // extend number on every request
        extendUserSession(normalizedPhoneNumber);

        // Check for FHIS enrollment flow FIRST, before parsing steps
        String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
        System.out.println("Current Flow: " + currentFlow + ", Input: " + inputedText);

        // If user sends empty input and they're stuck in FHIS flow, reset
        if (currentFlow != null && currentFlow.equals("fhis_enrollment") && inputedText.isEmpty()) {
            resetUserSession(normalizedPhoneNumber);
            return HandleLevel1(normalizedPhoneNumber, new String[0], true);
        }    

        // Handle FHIS enrollment flow - this takes precedence over step-based flow
        if ("fhis_enrollment".equals(currentFlow)) {
            return handleInformalFhisEnrollmentFlow(normalizedPhoneNumber, inputedText);
        } else if ("formal_fhis_enrollment".equals(currentFlow)) {
            return handleFormalFhisEnrollmentFlow(normalizedPhoneNumber, inputedText);
        }

        String[] parts = inputedText != null ? inputedText.split("\\*") : new String[0];
        int step = parts.length;
        System.out.println("Main USSD Flow - Step: " + step + ", Input: " + inputedText);

        System.out.println("=== MAIN USSD DEBUG ===");
        System.out.println("Current Flow: " + currentFlow);
        System.out.println("Input Text: '" + inputedText + "'");
        System.out.println("Step: " + step);
        System.out.println("Parts: " + Arrays.toString(parts));
        System.out.println("========================");
        // Main USSD flow
        switch (step) {
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
    private static final int MAX_ORGANIZATIONS_PER_PAGE = 5;
    private static final int SESSION_TIMEOUT_MINUTES = 10;
    private static final int MIN_FHIS_NUMBER_LENGTH = 6;

    private String HandleLevel1(String phone, String[] parts, boolean isInitial) {
        if (isInitial) {
            // Check if user is in the middle of FHIS enrollment
            String currentFlow = (String) retrieveFromSession(phone, "currentFlow");
            if (currentFlow != null && (currentFlow.equals("fhis_enrollment") || currentFlow.equals("formal_fhis_enrollment"))) {
                return "CON You have an ongoing enrollment.\n1. Continue\n2. Start Fresh\n0. Exit";
            }
        }

        return "CON Welcome to TEMS SERVICE\n" +
                "Enter the name or initials of the organization you want to search for:";
    }

    private String HandleLevel2(String text, String phone, String[] parts) {
        // If no search text provided, ask user to enter one
        if (text == null || text.trim().isEmpty()) {
            return "CON Enter the name or initials of the organization you want to search for:";
        }
    
        Pageable firstPage = PageRequest.of(0, 5);
        Page<Organization> results = handleOrganizationSearch(text.trim(), firstPage);
        saveToSession(phone, "isMoreResultsFlow", false);
    
        if (results.isEmpty()) {
            return "END No matches for: " + text.trim();
        }
    
        // Save search data to session
        saveToSession(phone, "searchTerm", text.trim());
        saveToSession(phone, "currentPage", 0);
        saveToSession(phone, "totalPages", (int) results.getTotalPages());
    
        List<Long> orgIds = results.getContent().stream()
                .map(Organization::getId)
                .collect(Collectors.toList());
        saveToSession(phone, "org_ids", orgIds);
        saveToSession(phone, "selectedOrgId", null);
    
        return showOrganizationoptions(results.getContent(), 0, (int) results.getTotalPages());
    }

    private String showOrganizationoptions(List<Organization> organizations, int currentPage, int totalPages) {
        // FIXED: Added null check
        if (organizations == null || organizations.isEmpty()) {
            return "END No organizations found. Please try again.";
        }
        StringBuilder menu = new StringBuilder("CON Multiple matches found:\n");
        int count = 1;
        int iterationcount = Math.min(organizations.size(), 5); // Consider making 5 a constant
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

    private String showorgmenu(Organization orgofchoice) {
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
            // FIXED: Use renamed variable
            Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
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
        if (choice == null || choice.trim().isEmpty()) {
            System.err.println("Empty choice received for phone: " + phone);
            return "END Invalid input. Please try again by dialing the USSD code.";
        }
        
        List<Long> orgids = getOrgIdsFromSession(phone);
        if (orgids == null || orgids.isEmpty()) {
            return "END Session expired. Please start over.";
        }
        
        try {
            int selection = Integer.parseInt(choice);
            if (selection == 0) {
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            }
            if (selection == 6) {
                return handleMoreResults(phone);
            }
            
            int maxDisplayedOptions = Math.min(orgids.size(), MAX_ORGANIZATIONS_PER_PAGE);
            if (selection < 1 || selection > maxDisplayedOptions) {
                return "END Invalid selection. Please enter a number between 1 and " + maxDisplayedOptions + ".";
            }
            
            Long selectedID = orgids.get(selection - 1);
            saveToSession(phone, "selectedOrgId", selectedID);
            
            Optional<Organization> selectedOrgOptional = organizationRepository.findById(selectedID);
            if (!selectedOrgOptional.isPresent()) {
                return "END Organization not found. Please try again.";
            }
            
            Organization selectedOrg = selectedOrgOptional.get();
            return showorgmenu(selectedOrg);
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format for phone: " + phone + ", choice: '" + choice + "'");
            return "END Please enter a valid number.";
        } catch (Exception e) {
            System.err.println("Error in handleOrganizationSelection: " + e.getMessage());
            e.printStackTrace();
            return "END An error occurred. Please try again.";
        }
    }


    private String handleOrganizationMenu(String choice, Organization org, String phone) {
        switch (choice) {
            case "1":
                return "CON Contact Info:\nPhone: " + (org.getContactTelephone() != null ? org.getContactTelephone() : "Not available") +
                        "\n0. Back to menu";
            case "2":
                return "CON Address:\n" + (org.getContactAddress() != null ? org.getContactAddress() : "Not available") +
                        "\n0. Back to menu";
            case "3":
                return "CON Description:\n" + (org.getDescription() != null ? org.getDescription() : "Not available") +
                        "\n0. Back to menu";
            case "4":
                // FIXED: Pass phone number correctly
                return showMoreOptions(org, phone);
            case "0":
                return backToSearchResults(phone);
            default:
                return "END Invalid choice";
        }
    }

    private String handleLevel4(String choice, String phone, String[] parts) {
        // Check if this is a "More Info" submenu flow first
        String currentSubMenu = (String) retrieveFromSession(phone, "currentSubMenu");
        if (currentSubMenu != null && currentSubMenu.equals("more_info")) {
            saveToSession(phone, "currentSubMenu", null); // Clear flag
    
            Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
            if (selectedOrgId == null) {
                return "END No organization selected. Please try again.";
            }
            Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
            if (!orgOptional.isPresent()) {
                return "END Organization not found. Please try again.";
            }
            Organization org = orgOptional.get();
            String orgName = org.getName().toUpperCase();
    
            // Handle More Info menu choices
            if ((orgName.contains("FHIS") || orgName.contains("FCT HEALTH") || orgName.contains("FCT HEALTH INSURANCE"))) {
                if ("1".equals(choice)) {
                    return handleFHISEnrollment(org, phone);
                } else if ("2".equals(choice)) {
                    return "CON Change Hospital - Coming Soon\n0. Back to menu";
                } else if ("0".equals(choice)) {
                    return showorgmenu(org); // Back to main org menu
                } else {
                    return "END Invalid choice for More Info menu.";
                }
            } else {
                // Non-FHIS org in More Info menu
                if ("0".equals(choice)) {
                    return showorgmenu(org);
                } else {
                    return "END Invalid choice for this organization.";
                }
            }
        }
    
        // --- Handle the initial organization menu choices (including "4. More") ---
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        if (selectedOrgId == null) {
            return "END No organization selected. Please try again.";
        }
        Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
        if (!orgOptional.isPresent()) {
            return "END Organization not found. Please try again.";
        }
        Organization org = orgOptional.get();
    
        // THIS IS THE KEY FIX: Handle the organization menu choices properly
        return handleOrganizationMenu(choice, org, phone);
    }
    private String handlelevel5(String choice, String phone, String[] parts) {
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        if (selectedOrgId == null) {
            return "END No organization selected. Please try again.";
        }
        // FIXED: Use renamed variable
        Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
        if (!orgOptional.isPresent()) {
            return "END Organization not found. Please try again.";
        }
        Organization org = orgOptional.get();
        String orgName = org.getName().toUpperCase();

        // This handles the direct enrollment path from handlelevel5
        if (orgName.contains("FHIS") || orgName.contains("FCT HEALTH") || orgName.contains("FCT HEALTH INSURANCE")) {
            switch (choice) {
                case "1":
                    return handleFHISEnrollment(org, phone); // This correctly calls checkExistingEnrollment
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
        // FIXED: Use renamed variable
        List<Organization> organizations = orgids.stream()
                .map(id -> organizationRepository.findById(id).orElse(null))
                .filter(org -> org != null)
                .collect(Collectors.toList());
        return showOrganizationoptions(organizations, currentPage, totalPages);
    }

    // FIXED: Added phone parameter
    private String showMoreOptions(Organization org, String phone) {
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

        Pageable pageable = PageRequest.of(nextPage, 5); // Consider making 5 a constant
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
        if (phoneNumber == null)
            return "";
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
        if (searchTerm == null || searchTerm.trim().length() < 2) {
            return Page.empty(); // Return empty page if search term is too short
        }
        return organizationRepository.searchByNameOrInitialsContainingIgnoreCase(searchTerm, pageable);
    }

    private void saveToSession(String phoneNumber, String key, Object value) {
        try {
            String sessionkey = phoneNumber + ":" + key;
            System.out.println("Saving to session - Key: " + sessionkey +
                    ", Type: " + (value != null ? value.getClass().getSimpleName() : "null") +
                    ", Value: " + value);
            redisTemplate.opsForValue().set(sessionkey, value, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            System.err.println("Error saving to session: " + e.getMessage());
        }
    }

    private List<Long> getOrgIdsFromSession(String phone) {
        List<?> rawList = (List<?>) retrieveFromSession(phone, "org_ids");
        if (rawList == null)
            return null;
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
        if (value == null)
            return null;
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
            saveToSession(phone, "currentFlow", "fhis_enrollment");
            saveToSession(phone, "enrollmentOrgId", org.getId());
            return existingCheck; // Return the menu to handle existing enrollment
        }
    
        saveToSession(phone, "currentFlow", "fhis_enrollment");
        saveToSession(phone, "enrollmentOrgId", org.getId());
        return "CON Select enrollment type:\n" +
                "1. Informal Sector\n" +
                "2. Formal Sector\n" +
                "0. Back to menu";
    }
    //extend session time
    private void extendUserSession(String phoneNumber) {
        try {
            // Define known session keys instead of using wildcard search
            String[] knownKeys = {
                "currentFlow", "enrollmentOrgId", "searchTerm", "currentPage", 
                "totalPages", "org_ids", "selectedOrgId", "isMoreResultsFlow",
                "currentField", "waitingForContinue", "existingEnrollmentFlow",
                "currentSubMenu", "handlingExistingEnrollment", "viewingDetails"
            };
            
            for (String keyType : knownKeys) {
                String fullKey = phoneNumber + ":" + keyType;
                Boolean exists = redisTemplate.hasKey(fullKey);
                if (Boolean.TRUE.equals(exists)) {
                    redisTemplate.expire(fullKey, 10, TimeUnit.MINUTES);
                }
            }
            System.out.println("Session extended for phone: " + phoneNumber);
        } catch (Exception e) {
            System.err.println("Error extending user session: " + e.getMessage());
        }
    }

    // Consider removing if resetUserSession is sufficient
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

    // Add a method to completely reset user session when they want to start fresh
    private void resetUserSession(String phoneNumber) {
        try {
            Set<String> allKeys = redisTemplate.keys(phoneNumber + ":*");
            if (allKeys != null) {
                List<String> enrollmentKeys = allKeys.stream()
                        .filter(key -> key.contains("enrollment") ||
                                key.contains("fhis") ||
                                key.contains("currentFlow") ||
                                key.contains("currentField") ||
                                key.contains("waitingForContinue") ||
                                key.contains("existingEnrollmentFlow") ||
                                key.contains("currentSubMenu"))
                        .collect(Collectors.toList());
                if (!enrollmentKeys.isEmpty()) {
                    redisTemplate.delete(enrollmentKeys);
                }
            }
        } catch (Exception e) {
            System.err.println("Error resetting user session: " + e.getMessage());
        }
    }

    private String handleFHISEnrollmentFlow(String phoneNumber, String inputText) {
        System.out.println("FHIS Enrollment Flow - Phone: " + phoneNumber + ", Input: " + inputText);
        try {
            String viewingDetails = (String) retrieveFromSession(phoneNumber, "viewingDetails");
            if ("true".equals(viewingDetails) && "0".equals(inputText)) {
                saveToSession(phoneNumber, "viewingDetails", null);
                // Go back to the existing enrollment menu
                return checkExistingEnrollment(phoneNumber);
            }
            // check for formal
        
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
            InformalFhisEnrollment enrollment = GetorCreateInformalFhisEnrollment(phoneNumber);
            if (enrollment == null) {
                clearenrollmentSession(phoneNumber);
                return "END Error retrieving enrollment. Please try again.";
            }

            String currentStep = enrollment.getCurrentStep();
            if (currentStep == null) {
                enrollment.setCurrentStep("sector_selection");
                InformalfhisEnrollmentRepository.save(enrollment);
                currentStep = "sector_selection";
            }

            System.out.println("Current Step: " + currentStep + ", Last Choice: " + lastChoice);
            String currentField = (String) retrieveFromSession(phoneNumber, "currentField");
            if (currentField == null && !currentStep.equals("sector_selection") && !currentStep.equals("completed")) {
            currentField = determineCurrentFieldFromEnrollment(enrollment, currentStep);
            if (currentField != null) {
                saveToSession(phoneNumber, "currentField", currentField);
                System.out.println("Set missing currentField to: " + currentField + " for step: " + currentStep);
            }
        }

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
    private String handleFormalFhisEnrollmentFlow(String phoneNumber, String inputText) {
        System.out.println("FORMAL FHIS Enrollment Flow - Phone: " + phoneNumber + ", Input: " + inputText);
    
        // CRITICAL FIX: Extract only the last choice from the input
        String lastChoice = "";
        if (inputText != null && !inputText.isEmpty()) {
            String[] parts = inputText.split("\\*");
            lastChoice = parts.length > 0 ? parts[parts.length - 1] : "";
        }
    
        if ("0".equals(lastChoice)) {
            clearenrollmentSession(phoneNumber);
            resetUserSession(phoneNumber);
            return HandleLevel1(phoneNumber, new String[0], true);
        }
    
        String existingCheck = checkExistingFormalEnrollment(phoneNumber);
        if (existingCheck != null) {
            return existingCheck;
        }
    
        FormalFhisEnrollment enrollment = GetorCreateFormalFhisEnrollment(phoneNumber);
        if (enrollment == null) {
            clearenrollmentSession(phoneNumber);
            return "END Error retrieving enrollment. Please try again.";
        }
    
        String currentStep = enrollment.getCurrentStep();
        if (currentStep == null) {
            enrollment.setCurrentStep("personal_data");
            formalFhisEnrollmentRepository.save(enrollment);
            currentStep = "personal_data";
        }
    
        String currentField = (String) retrieveFromSession(phoneNumber, "currentField");
        if (currentField == null && !"completed".equals(currentStep)) {
            currentField = determineCurrentFieldFromFormalEnrollment(enrollment, currentStep);
            if (currentField != null) {
                saveToSession(phoneNumber, "currentField", currentField);
            }
        }
    
        // CRITICAL FIX: Use lastChoice instead of inputText
        String continuationResult = handleFormalContinuationChoice(phoneNumber, lastChoice, enrollment);
        if (continuationResult != null) {
            return continuationResult;
        }
    
        try {
            return switch (currentStep) {
                case "personal_data" -> handleFormalPersonalData(phoneNumber, lastChoice, enrollment);
                case "professional_data" -> handleFormalProfessionalData(phoneNumber, lastChoice, enrollment);
                case "social_data" -> handleFormalSocialData(phoneNumber, lastChoice, enrollment);
                case "dependants_data" -> handleFormalDependantsData(phoneNumber, lastChoice, enrollment);
                case "healthcare_data" -> handleFormalHealthcareData(phoneNumber, lastChoice, enrollment);
                case "completed" -> handleFormalEnrollmentCompletion(phoneNumber, lastChoice, enrollment);
                default -> "END Invalid step. Please restart.";
            };
        } catch (Exception e) {
            System.err.println("Error in Formal FHIS flow: " + e.getMessage());
            e.printStackTrace();
            clearenrollmentSession(phoneNumber);
            return "END An error occurred. Please try again.";
        }
    }
    private String checkExistingFormalEnrollment(String phoneNumber) {
        try {
            Optional<FormalFhisEnrollment> existing = formalFhisEnrollmentRepository.findByPhoneNumber(phoneNumber);
            if (existing.isPresent()) {
                FormalFhisEnrollment enrollment = existing.get();
                String currentStep = enrollment.getCurrentStep();

                if ("completed".equals(currentStep)) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "completed");
                    return "CON You already have a completed Formal FHIS enrollment.\n" +
                            "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                            "FHIS No: " + enrollment.getFhisNo() + "\n" +
                            "1. Continue\n" +
                            "2. Start Fresh\n" +
                            "0. Back to menu";
                } else if (currentStep != null) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "incomplete");
                    return "CON Existing Formal enrollment found (Incomplete).\n" +
                            "Progress: " + getProgressPercentage(currentStep) + "%\n" +
                            "1. Continue\n" +
                            "2. Start Fresh\n" +
                            "0. Back to menu";
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking existing formal enrollment: " + e.getMessage());
        }
        return null;
    }
    private String handleExistingFormalEnrollmentChoice(String phone, String choice) {
        try {
            Optional<FormalFhisEnrollment> existing = formalFhisEnrollmentRepository.findByPhoneNumber(phone);
            if (!existing.isPresent()) {
                clearenrollmentSession(phone);
                return "END Enrollment not found.";
            }
            FormalFhisEnrollment enrollment = existing.get();
            String existingFlow = (String) retrieveFromSession(phone, "existingEnrollmentFlow");
    
            switch (choice) {
                case "1": // Continue or restart
                    if ("completed".equals(existingFlow)) {
                        // Delete and restart for completed enrollment
                        formalFhisEnrollmentRepository.delete(enrollment);
                        clearenrollmentSession(phone);
                        return "CON FHIS Enrollment Started\n" +
                                "Select enrollment type:\n" +
                                "1. Informal Sector\n" +
                                "2. Formal Sector\n" +
                                "0. Back to menu";
                    } else {
                        // Resume incomplete enrollment
                        saveToSession(phone, "currentField", determineCurrentFieldFromFormalEnrollment(enrollment, enrollment.getCurrentStep()));
                        return resumeFormalEnrollmentStep(enrollment);
                    }
                case "2": // View or start fresh
                    if ("completed".equals(existingFlow)) {
                        saveToSession(phone, "viewingDetails", "true");
                        saveToSession(phone, "existingFormalEnrollmentHandled", true);
                        return "CON Enrollment Details:\n" +
                                "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                                "FHIS: " + enrollment.getFhisNo() + "\n" +
                                "Type: " + enrollment.getEnrollmentType() + "\n" +
                                "0. Back to menu";
                    } else {
                        // CRITICAL FIX: Delete existing and start fresh for incomplete enrollment
                        formalFhisEnrollmentRepository.delete(enrollment);
                        clearenrollmentSession(phone);
                        
                        // Create new enrollment and start immediately
                        FormalFhisEnrollment newEnrollment = new FormalFhisEnrollment();
                        newEnrollment.setPhoneNumber(phone);
                        newEnrollment.setEnrollmentType("Formal");
                        newEnrollment.setCurrentStep("personal_data");
                        newEnrollment.setCreatedAt(LocalDateTime.now());
                        newEnrollment.setUpdatedAt(LocalDateTime.now());
                        formalFhisEnrollmentRepository.save(newEnrollment);
                        
                        saveToSession(phone, "currentField", "fhisNo");
                        return "CON FORMAL SECTOR - Fresh Start\n" +
                                "Enter your FHIS Number:";
                    }
                case "0":
                    clearenrollmentSession(phone);
                    resetUserSession(phone);
                    return HandleLevel1(phone, new String[0], true);
                default:
                    return "CON Invalid choice.\n1. Continue\n2. Start fresh\n0. Back";
            }
        } catch (Exception e) {
            System.err.println("Error handling existing formal choice: " + e.getMessage());
            return "END Error. Please try again.";
        }
    }
    private String handleFormalPersonalData(String phone, String inputText, FormalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        if (currentField == null) {
            currentField = determineCurrentFieldFromFormalEnrollment(enrollment, "personal_data");
            if (currentField == null) return moveToFormalNextStage(phone, enrollment);
            saveToSession(phone, "currentField", currentField);
        }
    
        if ((inputText == null || inputText.trim().isEmpty()) && !"middleName".equals(currentField)) {
            return "CON Enter " + getFieldDisplayName(currentField) + ":";
        }
    
        switch (currentField) {
            case "fhisNo":
                if (!isValidFhisNumber(inputText.trim())) {
                    return "CON Invalid FHIS Number. Try again:";
                }
                enrollment.setFhisNo(inputText.trim());
                saveToSession(phone, "currentField", "surname");
                break;
            case "surname":
                if (!isValidName(inputText.trim())) return "CON Invalid surname:";
                enrollment.setSurname(inputText.trim());
                saveToSession(phone, "currentField", "firstName");
                break;
            case "firstName":
                if (!isValidName(inputText.trim())) return "CON Invalid first name:";
                enrollment.setFirstName(inputText.trim());
                saveToSession(phone, "currentField", "middleName");
                break;
            case "middleName":
                enrollment.setMiddleName(inputText != null ? inputText.trim() : "");
                saveToSession(phone, "currentField", "dateOfBirth");
                break;
            case "dateOfBirth":
                if (!isValidDateOfBirth(inputText)) return "CON Invalid date (YYYY-MM-DD):";
                enrollment.setDateOfBirth(inputText);
                saveToSession(phone, "currentField", "sex");
                break;
            case "sex":
                String upperSex = inputText.trim().toUpperCase();
                if (!"M".equals(upperSex) && !"F".equals(upperSex)) return "CON Sex: M or F:";
                enrollment.setSex(upperSex);
                saveToSession(phone, "currentField", "bloodGroup");
                break;
            case "bloodGroup":
                if (!isValidBloodGroup(inputText.trim())) return "CON Invalid blood group:";
                enrollment.setBloodGroup(inputText.trim());
                enrollment.setCurrentStep("professional_data");
                formalFhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "designation");
                saveToSession(phone, "waitingForContinue", true);
                return "CON Personal data saved.\n1. Continue to Professional Data\n0. Back";
        }
    
        formalFhisEnrollmentRepository.save(enrollment);
        return "CON Enter " + getFieldDisplayName(currentField) + ":";
    }
    private String handleFormalProfessionalData(String phone, String inputText, FormalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        if (currentField == null) {
            currentField = determineCurrentFieldFromFormalEnrollment(enrollment, "professional_data");
            if (currentField == null) return moveToFormalNextStage(phone, enrollment);
            saveToSession(phone, "currentField", currentField);
        }
    
        if (inputText == null || inputText.trim().isEmpty()) {
            return "CON Enter " + getFieldDisplayName(currentField) + ":";
        }
    
        switch (currentField) {
            case "designation": enrollment.setDesignation(inputText.trim()); saveToSession(phone, "currentField", "occupation"); break;
            case "occupation": enrollment.setOccupation(inputText.trim()); saveToSession(phone, "currentField", "presentStation"); break;
            case "presentStation": enrollment.setPresentStation(inputText.trim()); saveToSession(phone, "currentField", "rank"); break;
            case "rank": enrollment.setRank(inputText.trim()); saveToSession(phone, "currentField", "pfNumber"); break;
            case "pfNumber": enrollment.setPfNumber(inputText.trim()); saveToSession(phone, "currentField", "sdaName"); break;
            case "sdaName":
                enrollment.setSdaName(inputText.trim());
                enrollment.setCurrentStep("social_data");
                formalFhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "maritalStatus");
                saveToSession(phone, "waitingForContinue", true);
                return "CON Professional data saved.\n1. Continue to Social Data\n0. Back";
        }
    
        formalFhisEnrollmentRepository.save(enrollment);
        return "CON Enter " + getFieldDisplayName(currentField) + ":";
    }
    private String handleFormalSocialData(String phone, String inputText, FormalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        if (currentField == null) {
            currentField = determineCurrentFieldFromFormalEnrollment(enrollment, "social_data");
            if (currentField == null) return moveToFormalNextStage(phone, enrollment);
            saveToSession(phone, "currentField", currentField);
        }
    
        if (inputText == null || inputText.trim().isEmpty()) {
            return "CON Enter " + getFieldDisplayName(currentField) + ":";
        }
    
        switch (currentField) {
            case "maritalStatus": 
                if (!isValidMaritalStatus(inputText.trim())) return "CON Invalid status (Single, Married, etc):";
                enrollment.setMaritalStatus(inputText.trim());
                saveToSession(phone, "currentField", "telephoneNumber");
                break;
            case "telephoneNumber":
                if (!isValidPhoneNumber(inputText.trim())) return "CON Invalid phone number:";
                enrollment.setTelephoneNumber(inputText.trim());
                saveToSession(phone, "currentField", "residentialAddress");
                break;
            case "residentialAddress":
                enrollment.setResidentialAddress(inputText.trim());
                saveToSession(phone, "currentField", "email");
                break;
            case "email":
                if (!isValidEmail(inputText.trim())) return "CON Invalid email:";
                enrollment.setEmail(inputText.trim());
                enrollment.setCurrentStep("dependants_data");
                formalFhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "spouseFirstName");
                saveToSession(phone, "waitingForContinue", true);
                return "CON Social data saved.\n1. Continue to Dependants\n0. Back";
        }
    
        formalFhisEnrollmentRepository.save(enrollment);
        return "CON Enter " + getFieldDisplayName(currentField) + ":";
    }
    private String handleFormalDependantsData(String phone, String inputText, FormalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        if (currentField == null) {
            currentField = determineCurrentFieldFromFormalEnrollment(enrollment, "dependants_data");
            if (currentField == null) return moveToFormalNextStage(phone, enrollment);
            saveToSession(phone, "currentField", currentField);
        }
    
        if (inputText == null || inputText.trim().isEmpty()) {
            return "CON Enter " + getFieldDisplayName(currentField) + ":";
        }
    
        switch (currentField) {
            case "spouseFirstName": enrollment.setSpouseFirstName(inputText.trim()); saveToSession(phone, "currentField", "spouseSex"); break;
            case "spouseSex": 
                String sex = inputText.trim().toUpperCase(); 
                if (!"M".equals(sex) && !"F".equals(sex)) return "CON M or F only:"; 
                enrollment.setSpouseSex(sex); 
                saveToSession(phone, "currentField", "spouseBloodGroup"); 
                break;
            case "spouseBloodGroup": enrollment.setSpouseBloodGroup(inputText.trim()); saveToSession(phone, "currentField", "spouseDateOfBirth"); break;
            case "spouseDateOfBirth": 
                if (!isValidDateOfBirth(inputText)) return "CON YYYY-MM-DD:"; 
                enrollment.setSpouseDateOfBirth(inputText); 
                saveToSession(phone, "currentField", "child1FirstName"); 
                break;
            // Repeat for child1 to child4 (same pattern)
            case "child1FirstName": enrollment.setChild1FirstName(inputText.trim()); saveToSession(phone, "currentField", "child1Sex"); break;
            case "child1Sex": 
                String c1sex = inputText.trim().toUpperCase(); 
                if (!"M".equals(c1sex) && !"F".equals(c1sex)) return "CON M or F:"; 
                enrollment.setChild1Sex(c1sex); 
                saveToSession(phone, "currentField", "child1BloodGroup"); 
                break;
            case "child1BloodGroup": enrollment.setChild1BloodGroup(inputText.trim()); saveToSession(phone, "currentField", "child1DateOfBirth"); break;
            case "child1DateOfBirth": 
                if (!isValidDateOfBirth(inputText)) return "CON YYYY-MM-DD:"; 
                enrollment.setChild1DateOfBirth(inputText); 
                saveToSession(phone, "currentField", "child2FirstName"); 
                break;
            // child2
            case "child2FirstName": enrollment.setChild2FirstName(inputText.trim()); saveToSession(phone, "currentField", "child2Sex"); break;
            case "child2Sex": 
                String c2sex = inputText.trim().toUpperCase(); 
                if (!"M".equals(c2sex) && !"F".equals(c2sex)) return "CON M or F:"; 
                enrollment.setChild2Sex(c2sex); 
                saveToSession(phone, "currentField", "child2BloodGroup"); 
                break;
            case "child2BloodGroup": enrollment.setChild2BloodGroup(inputText.trim()); saveToSession(phone, "currentField", "child2DateOfBirth"); break;
            case "child2DateOfBirth": 
                if (!isValidDateOfBirth(inputText)) return "CON YYYY-MM-DD:"; 
                enrollment.setChild2DateOfBirth(inputText); 
                saveToSession(phone, "currentField", "child3FirstName"); 
                break;
            // child3
            case "child3FirstName": enrollment.setChild3FirstName(inputText.trim()); saveToSession(phone, "currentField", "child3Sex"); break;
            case "child3Sex": 
                String c3sex = inputText.trim().toUpperCase(); 
                if (!"M".equals(c3sex) && !"F".equals(c3sex)) return "CON M or F:"; 
                enrollment.setChild3Sex(c3sex); 
                saveToSession(phone, "currentField", "child3BloodGroup"); 
                break;
            case "child3BloodGroup": enrollment.setChild3BloodGroup(inputText.trim()); saveToSession(phone, "currentField", "child3DateOfBirth"); break;
            case "child3DateOfBirth": 
                if (!isValidDateOfBirth(inputText)) return "CON YYYY-MM-DD:"; 
                enrollment.setChild3DateOfBirth(inputText); 
                saveToSession(phone, "currentField", "child4FirstName"); 
                break;
            // child4
            case "child4FirstName": enrollment.setChild4FirstName(inputText.trim()); saveToSession(phone, "currentField", "child4Sex"); break;
            case "child4Sex": 
                String c4sex = inputText.trim().toUpperCase(); 
                if (!"M".equals(c4sex) && !"F".equals(c4sex)) return "CON M or F:"; 
                enrollment.setChild4Sex(c4sex); 
                saveToSession(phone, "currentField", "child4BloodGroup"); 
                break;
            case "child4BloodGroup": enrollment.setChild4BloodGroup(inputText.trim()); saveToSession(phone, "currentField", "child4DateOfBirth"); break;
            case "child4DateOfBirth": 
                if (!isValidDateOfBirth(inputText)) return "CON YYYY-MM-DD:"; 
                enrollment.setChild4DateOfBirth(inputText); 
                enrollment.setCurrentStep("healthcare_data");
                formalFhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "hospitalName");
                saveToSession(phone, "waitingForContinue", true);
                return "CON Dependants saved.\n1. Continue to Healthcare Provider\n0. Back";
        }
    
        formalFhisEnrollmentRepository.save(enrollment);
        return "CON Enter " + getFieldDisplayName(currentField) + ":";
    }
    private String handleFormalHealthcareData(String phone, String inputText, FormalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        if (currentField == null) {
            currentField = determineCurrentFieldFromFormalEnrollment(enrollment, "healthcare_data");
            if (currentField == null) return moveToFormalNextStage(phone, enrollment);
            saveToSession(phone, "currentField", currentField);
        }
    
        if (inputText == null || inputText.trim().isEmpty()) {
            return "CON Enter " + getFieldDisplayName(currentField) + ":";
        }
    
        switch (currentField) {
            case "hospitalName": enrollment.setHospitalName(inputText.trim()); saveToSession(phone, "currentField", "hospitalLocation"); break;
            case "hospitalLocation": enrollment.setHospitalLocation(inputText.trim()); saveToSession(phone, "currentField", "hospitalCodeNo"); break;
            case "hospitalCodeNo": 
                enrollment.setHospitalCodeNo(inputText.trim());
                enrollment.setCurrentStep("completed");
                formalFhisEnrollmentRepository.save(enrollment);
                return "CON Almost done!\n" + showFormalEnrollmentSummary(enrollment) + 
                       "\n1. Confirm\n2. Edit\n0. Cancel";
        }
    
        formalFhisEnrollmentRepository.save(enrollment);
        return "CON Enter " + getFieldDisplayName(currentField) + ":";
    }
    private String moveToFormalNextStage(String phone, FormalFhisEnrollment enrollment) {
        switch (enrollment.getCurrentStep()) {
            case "personal_data":
                enrollment.setCurrentStep("professional_data");
                saveToSession(phone, "currentField", "designation");
                break;
            case "professional_data":
                enrollment.setCurrentStep("social_data");
                saveToSession(phone, "currentField", "maritalStatus");
                break;
            case "social_data":
                enrollment.setCurrentStep("dependants_data");
                saveToSession(phone, "currentField", "spouseFirstName");
                break;
            case "dependants_data":
                enrollment.setCurrentStep("healthcare_data");
                saveToSession(phone, "currentField", "hospitalName");
                break;
            case "healthcare_data":
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                break;
        }
        formalFhisEnrollmentRepository.save(enrollment);
        saveToSession(phone, "waitingForContinue", true);
        return "CON Data saved.\n1. Continue\n0. Back";
    }
    private String handleFormalContinuationChoice(String phone, String choice, FormalFhisEnrollment enrollment) {
        Boolean waiting = (Boolean) retrieveFromSession(phone, "waitingForContinue");
        if (Boolean.TRUE.equals(waiting)) {
            saveToSession(phone, "waitingForContinue", false);
            if ("1".equals(choice)) {
                return promptForNextField((String) retrieveFromSession(phone, "currentField"));
            } else if ("0".equals(choice)) {
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            } else {
                return "CON Invalid. 1 to continue, 0 to back.";
            }
        }
        return null;
    }
    private String handleFormalEnrollmentCompletion(String phone, String choice, FormalFhisEnrollment enrollment) {
        switch (choice) {
            case "1":
                enrollment.setCurrentStep("completed");
                formalFhisEnrollmentRepository.save(enrollment);
                clearenrollmentSession(phone);
                return "END Enrollment successful! Ref: " + enrollment.getFhisNo();
            case "2":
                enrollment.setCurrentStep("personal_data");
                formalFhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "fhisNo");
                return "CON EDIT MODE - Enter FHIS No:";
            case "0":
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            default:
                return "CON 1. Confirm\n2. Edit\n0. Cancel";
        }
    }
    private String showFormalEnrollmentSummary(FormalFhisEnrollment enrollment) {
        return "REVIEW:\n" +
                "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                "FHIS: " + enrollment.getFhisNo() + "\n" +
                "Email: " + enrollment.getEmail() + "\n" +
                "Phone: " + enrollment.getTelephoneNumber();
    }

    private String determineCurrentFieldFromEnrollment(InformalFhisEnrollment enrollment, String currentStep) {
        switch (currentStep) {
            case "personal_data":
                if (enrollment.getFhisNo() == null) return "fhisNo";
                if (enrollment.getTitle() == null) return "title";
                if (enrollment.getSurname() == null) return "surname";
                if (enrollment.getFirstName() == null) return "firstName";
                if (enrollment.getMiddleName() == null) return "middleName";
                if (enrollment.getDateOfBirth() == null) return "dateOfBirth";
                break;
                
            case "social_data":
                if (enrollment.getMaritalStatus() == null) return "maritalStatus";
                if (enrollment.getEmail() == null) return "email";
                if (enrollment.getBloodGroup() == null) return "bloodGroup";
                if (enrollment.getResidentialAddress() == null) return "residentialAddress";
                if (enrollment.getOccupation() == null) return "occupation";
                break;
                
            case "corporate_data":
                if (enrollment.getNinNumber() == null) return "ninNumber";
                if (enrollment.getTelephoneNumber() == null) return "telephoneNumber";
                if (enrollment.getOrganizationName() == null) return "organizationName";
                break;
        }
        return null;
    }
    private String determineCurrentFieldFromFormalEnrollment(FormalFhisEnrollment enrollment, String currentStep) {
        switch (currentStep) {
            case "personal_data":
                if (enrollment.getFhisNo() == null) return "fhisNo";
                if (enrollment.getSurname() == null) return "surname";
                if (enrollment.getFirstName() == null) return "firstName";
                if (enrollment.getMiddleName() == null) return "middleName";
                if (enrollment.getDateOfBirth() == null) return "dateOfBirth";
                if (enrollment.getSex() == null) return "sex";
                if (enrollment.getBloodGroup() == null) return "bloodGroup";
                break;
                
            case "professional_data":
                if (enrollment.getDesignation() == null) return "designation";
                if (enrollment.getOccupation() == null) return "occupation";
                if (enrollment.getPresentStation() == null) return "presentStation";
                if (enrollment.getRank() == null) return "rank";
                if (enrollment.getPfNumber() == null) return "pfNumber";
                if (enrollment.getSdaName() == null) return "sdaName";
                break;
                
            case "social_data":
                if (enrollment.getMaritalStatus() == null) return "maritalStatus";
                if (enrollment.getTelephoneNumber() == null) return "telephoneNumber";
                if (enrollment.getResidentialAddress() == null) return "residentialAddress";
                if (enrollment.getEmail() == null) return "email";
                break;
                
            case "dependants_data":
                if (enrollment.getSpouseFirstName() == null) return "spouseFirstName";
                if (enrollment.getSpouseSex() == null) return "spouseSex";
                if (enrollment.getSpouseBloodGroup() == null) return "spouseBloodGroup";
                if (enrollment.getSpouseDateOfBirth() == null) return "spouseDateOfBirth";
                if (enrollment.getChild1FirstName() == null) return "child1FirstName";
                if (enrollment.getChild1Sex() == null) return "child1Sex";
                if (enrollment.getChild1BloodGroup() == null) return "child1BloodGroup";
                if (enrollment.getChild1DateOfBirth() == null) return "child1DateOfBirth";
                if (enrollment.getChild2FirstName() == null) return "child2FirstName";
                if (enrollment.getChild2Sex() == null) return "child2Sex";
                if (enrollment.getChild2BloodGroup() == null) return "child2BloodGroup";
                if (enrollment.getChild2DateOfBirth() == null) return "child2DateOfBirth";
                if (enrollment.getChild3FirstName() == null) return "child3FirstName";
                if (enrollment.getChild3Sex() == null) return "child3Sex";
                if (enrollment.getChild3BloodGroup() == null) return "child3BloodGroup";
                if (enrollment.getChild3DateOfBirth() == null) return "child3DateOfBirth";
                if (enrollment.getChild4FirstName() == null) return "child4FirstName";
                if (enrollment.getChild4Sex() == null) return "child4Sex";
                if (enrollment.getChild4BloodGroup() == null) return "child4BloodGroup";
                if (enrollment.getChild4DateOfBirth() == null) return "child4DateOfBirth";
                break;
                
            case "healthcare_data":
                if (enrollment.getHospitalName() == null) return "hospitalName";
                if (enrollment.getHospitalLocation() == null) return "hospitalLocation";
                if (enrollment.getHospitalCodeNo() == null) return "hospitalCodeNo";
                break;
        }
        return null;
    }

    private String handleInformalFhisEnrollmentFlow(String phoneNumber, String inputText) {
        System.out.println("Informal FHIS Enrollment Flow - Phone: " + phoneNumber + ", Input: " + inputText);
        try {
            // Check if user has an existing enrollment
            String handlingExisting = (String) retrieveFromSession(phoneNumber, "handlingExistingEnrollment");
            if (handlingExisting == null) {
                String existingCheck = checkExistingEnrollment(phoneNumber);
                if (existingCheck != null) {
                    return existingCheck; // Show "Continue or Start Fresh" only once
                }
            }
            // Once past this point, mark that we're handling it
            saveToSession(phoneNumber, "handlingExistingEnrollment", "true");
    
            // Get or create enrollment record
            InformalFhisEnrollment enrollment = GetorCreateInformalFhisEnrollment(phoneNumber);
            if (enrollment == null) {
                System.err.println("Failed to get or create informal enrollment for phone: " + phoneNumber);
                clearenrollmentSession(phoneNumber);
                resetUserSession(phoneNumber);
                return "END Error creating enrollment. Please try again.";
            }
    
            String currentStep = enrollment.getCurrentStep();
            if (currentStep == null) {
                enrollment.setCurrentStep("personal_data"); // Default to personal_data, not sector_selection
                InformalfhisEnrollmentRepository.save(enrollment);
                currentStep = "personal_data";
            }
    
            // CRITICAL FIX: Extract only the last choice from the input
            String lastChoice = "";
            if (inputText != null && !inputText.isEmpty()) {
                String[] parts = inputText.split("\\*");
                lastChoice = parts.length > 0 ? parts[parts.length - 1] : "";
            }
            
            System.out.println("Current Step: " + currentStep + ", Last Choice: " + lastChoice);
            
            String currentField = (String) retrieveFromSession(phoneNumber, "currentField");
            if (currentField == null && !currentStep.equals("sector_selection") && !currentStep.equals("completed")) {
                currentField = determineCurrentFieldFromEnrollment(enrollment, currentStep);
                if (currentField != null) {
                    saveToSession(phoneNumber, "currentField", currentField);
                    System.out.println("Set missing currentField to: " + currentField + " for step: " + currentStep);
                }
            }
    
            // Check if we're waiting for a continuation choice
            String continuationResult = handleContinuationChoice(phoneNumber, lastChoice, enrollment);
            if (continuationResult != null) {
                return continuationResult;
            }
    
            if (currentStep.equals("sector_selection")) {
                // CRITICAL FIX: Pass only the last choice, not the full input
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
            System.err.println("Error in Informal FHIS enrollment flow: " + e.getMessage());
            e.printStackTrace();
            clearenrollmentSession(phoneNumber);
            return "END An error occurred. Please try again.";
        }
    }

    private String checkExistingEnrollment(String phoneNumber) {
        try {
            Optional<InformalFhisEnrollment> existing = InformalfhisEnrollmentRepository.findByPhoneNumber(phoneNumber);
            if (existing.isPresent()) {
                InformalFhisEnrollment enrollment = existing.get();
                String currentStep = enrollment.getCurrentStep();
    
                // Skip if already handling
                String handling = (String) retrieveFromSession(phoneNumber, "handlingExistingEnrollment");
                if ("true".equals(handling)) {
                    return null;
                }
    
                if ("completed".equals(currentStep)) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "completed");
                    return "CON You already have a completed enrollment.\n" +
                            "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                            "FHIS No: " + enrollment.getFhisNo() + "\n" +
                            "1. Continue\n" +
                            "2. Start Fresh\n" +
                            "0. Exit";
                } else if (currentStep != null && !"sector_selection".equals(currentStep)) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "incomplete");
                    return "CON Existing enrollment found (Incomplete).\n" +
                            "Progress: " + getProgressPercentage(currentStep) + "%\n" +
                            "1. Continue\n" +
                            "2. Start Fresh\n" +
                            "0. Exit";
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking existing enrollment: " + e.getMessage());
        }
        return null;
    }
    

    // FIXED: Implemented getProgressPercentage helper
    private String getProgressPercentage(String step) {
        switch (step) {
            case "personal_data":
                return "25";
            case "social_data":
                return "50";
            case "corporate_data":
                return "75";
            case "completed":
                return "100";
            default:
                return "0";
        }
    }

    private String handleSectorSelection(String phone, String choice) {
        System.out.println("=== SECTOR SELECTION DEBUG ===");
        System.out.println("Phone: " + phone);
        System.out.println("Raw choice: '" + choice + "'");
        
        // Clean the choice
        choice = choice != null ? choice.trim() : "";
        System.out.println("Cleaned choice: '" + choice + "'");
        
        // CRITICAL FIX: Check if we're already handling existing enrollment
        String handlingExisting = (String) retrieveFromSession(phone, "handlingExistingEnrollment");
        
        // If we're NOT already handling existing enrollment, check for existing enrollments first
        if (!"true".equals(handlingExisting)) {
            try {
                Optional<InformalFhisEnrollment> existingInformal = InformalfhisEnrollmentRepository.findByPhoneNumber(phone);
                Optional<FormalFhisEnrollment> existingFormal = formalFhisEnrollmentRepository.findByPhoneNumber(phone);
                
                boolean hasInformal = existingInformal.isPresent();
                boolean hasFormal = existingFormal.isPresent();
                
                System.out.println("Has Informal: " + hasInformal + ", Has Formal: " + hasFormal);
                
                // If user has existing enrollment, handle that flow
                if (hasInformal || hasFormal) {
                    return handleExistingEnrollmentChoice(phone, choice);
                }
            } catch (Exception e) {
                System.err.println("Error checking existing enrollments: " + e.getMessage());
            }
        }
        
        // FRESH ENROLLMENT - Handle sector selection choices
        switch (choice) {
            case "1":
                System.out.println("Creating new Informal enrollment");
                InformalFhisEnrollment informalEnrollment = new InformalFhisEnrollment();
                informalEnrollment.setPhoneNumber(phone);
                informalEnrollment.setEnrollmentType("Informal");
                informalEnrollment.setCurrentStep("personal_data"); // Skip sector_selection step
                informalEnrollment.setCreatedAt(LocalDateTime.now());
                informalEnrollment.setUpdatedAt(LocalDateTime.now());
                InformalfhisEnrollmentRepository.save(informalEnrollment);
                
                saveToSession(phone, "currentFlow", "fhis_enrollment");
                saveToSession(phone, "currentField", "fhisNo");
                saveToSession(phone, "handlingExistingEnrollment", "true");
                
                return "CON INFORMAL SECTOR\nEnter your FHIS Number:";
                
            case "2":
                System.out.println("Creating new Formal enrollment");
                FormalFhisEnrollment formalEnrollment = new FormalFhisEnrollment();
                formalEnrollment.setPhoneNumber(phone);
                formalEnrollment.setEnrollmentType("Formal");
                formalEnrollment.setCurrentStep("personal_data"); // Skip sector_selection step
                formalEnrollment.setCreatedAt(LocalDateTime.now());
                formalEnrollment.setUpdatedAt(LocalDateTime.now());
                formalFhisEnrollmentRepository.save(formalEnrollment);
                
                saveToSession(phone, "currentFlow", "formal_fhis_enrollment");
                saveToSession(phone, "currentField", "fhisNo");
                saveToSession(phone, "handlingExistingEnrollment", "true");
                
                return "CON FORMAL SECTOR\nEnter your FHIS Number:";
                
            case "0":
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
                
            default:
                System.out.println("Invalid choice received: '" + choice + "'");
                return "CON Invalid selection. Please try again.\n" +
                       "Select enrollment type:\n" +
                       "1. Informal Sector\n" +
                       "2. Formal Sector\n" +
                       "0. Back to menu";
        }
    }
    // FIXED: Implemented handleExistingEnrollmentChoice
    private String handleExistingEnrollmentChoice(String phone, String choice) {
        try {
            Optional<InformalFhisEnrollment> existingInformal = InformalfhisEnrollmentRepository.findByPhoneNumber(phone);
            Optional<FormalFhisEnrollment> existingFormal = formalFhisEnrollmentRepository.findByPhoneNumber(phone);
    
            boolean hasInformal = existingInformal.isPresent();
            boolean hasFormal = existingFormal.isPresent();
    
            if ("2".equals(choice)) {
                // Start fresh: delete both and return to sector selection
                existingInformal.ifPresent(enrollment -> InformalfhisEnrollmentRepository.delete(enrollment));
                existingFormal.ifPresent(enrollment -> formalFhisEnrollmentRepository.delete(enrollment));
                clearenrollmentSession(phone);
                
                // CRITICAL FIX: Maintain the FHIS enrollment flow and return sector selection
                saveToSession(phone, "currentFlow", "fhis_enrollment");
                saveToSession(phone, "handlingExistingEnrollment", "true");
                
                return "CON FRESH START - Select enrollment type:\n" +
                       "1. Informal Sector\n" +
                       "2. Formal Sector\n" +
                       "0. Back to menu";
            }
    
            if ("1".equals(choice)) {
                if (hasInformal) {
                    InformalFhisEnrollment enrollment = existingInformal.get();
                    saveToSession(phone, "currentFlow", "fhis_enrollment");
                    saveToSession(phone, "currentField", determineCurrentFieldFromEnrollment(enrollment, enrollment.getCurrentStep()));
                    saveToSession(phone, "handlingExistingEnrollment", "true");
                    return resumeEnrollmentStep(enrollment);
                } else if (hasFormal) {
                    FormalFhisEnrollment enrollment = existingFormal.get();
                    saveToSession(phone, "currentFlow", "formal_fhis_enrollment");
                    saveToSession(phone, "currentField", determineCurrentFieldFromFormalEnrollment(enrollment, enrollment.getCurrentStep()));
                    saveToSession(phone, "handlingExistingEnrollment", "true");
                    return resumeFormalEnrollmentStep(enrollment);
                }
            }
    
            if ("0".equals(choice)) {
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            }
    
            return "CON Invalid choice. Please try again.\n1. Continue\n2. Start fresh\n0. Back";
        } catch (Exception e) {
            System.err.println("Error handling existing enrollment choice: " + e.getMessage());
            return "END Error. Please try again.";
        }
    }
    // FIXED: Implemented helper
    private String getCurrentFieldForStep(String step) {
        switch (step) {
            case "personal_data":
                return "fhisNo";
            case "social_data":
                return "maritalStatus";
            case "corporate_data":
                return "ninNumber";
            default:
                return "fhisNo";
        }
    }

    // FIXED: Implemented helper
    private String resumeEnrollmentStep(InformalFhisEnrollment enrollment) {
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
    // Add this method to your UssdController class

    private String resumeFormalEnrollmentStep(FormalFhisEnrollment enrollment) {
        String currentStep = enrollment.getCurrentStep();
        switch (currentStep) {
            case "personal_data":
                if (enrollment.getFhisNo() == null) {
                    return "CON FORMAL SECTOR\nEnter your FHIS Number:";
                } else if (enrollment.getSurname() == null) {
                    return "CON Enter your Surname:";
                } else if (enrollment.getFirstName() == null) {
                    return "CON Enter your First Name:";
                } else if (enrollment.getMiddleName() == null) {
                    return "CON Enter your Middle Name (optional):";
                } else if (enrollment.getDateOfBirth() == null) {
                    return "CON Enter your Date of Birth (YYYY-MM-DD):";
                } else if (enrollment.getSex() == null) {
                    return "CON Enter your Sex (M/F):";
                } else {
                    return "CON Enter your Blood Group:";
                }
                
            case "professional_data":
                if (enrollment.getDesignation() == null) {
                    return "CON Enter your Designation:";
                } else if (enrollment.getOccupation() == null) {
                    return "CON Enter your Occupation:";
                } else if (enrollment.getPresentStation() == null) {
                    return "CON Enter your Present Station:";
                } else if (enrollment.getRank() == null) {
                    return "CON Enter your Rank:";
                } else if (enrollment.getPfNumber() == null) {
                    return "CON Enter your PF Number:";
                } else {
                    return "CON Enter your SDA Name:";
                }
                
            case "social_data":
                if (enrollment.getMaritalStatus() == null) {
                    return "CON Enter your Marital Status:";
                } else if (enrollment.getTelephoneNumber() == null) {
                    return "CON Enter your Telephone Number:";
                } else if (enrollment.getResidentialAddress() == null) {
                    return "CON Enter your Residential Address:";
                } else {
                    return "CON Enter your Email Address:";
                }
                
            case "dependants_data":
                if (enrollment.getSpouseFirstName() == null) {
                    return "CON Enter Spouse First Name:";
                } else if (enrollment.getSpouseSex() == null) {
                    return "CON Enter Spouse Sex (M/F):";
                } else if (enrollment.getSpouseBloodGroup() == null) {
                    return "CON Enter Spouse Blood Group:";
                } else if (enrollment.getSpouseDateOfBirth() == null) {
                    return "CON Enter Spouse Date of Birth (YYYY-MM-DD):";
                } else if (enrollment.getChild1FirstName() == null) {
                    return "CON Enter Child 1 First Name:";
                } else if (enrollment.getChild1Sex() == null) {
                    return "CON Enter Child 1 Sex (M/F):";
                } else if (enrollment.getChild1BloodGroup() == null) {
                    return "CON Enter Child 1 Blood Group:";
                } else if (enrollment.getChild1DateOfBirth() == null) {
                    return "CON Enter Child 1 Date of Birth (YYYY-MM-DD):";
                } else if (enrollment.getChild2FirstName() == null) {
                    return "CON Enter Child 2 First Name:";
                } else if (enrollment.getChild2Sex() == null) {
                    return "CON Enter Child 2 Sex (M/F):";
                } else if (enrollment.getChild2BloodGroup() == null) {
                    return "CON Enter Child 2 Blood Group:";
                } else if (enrollment.getChild2DateOfBirth() == null) {
                    return "CON Enter Child 2 Date of Birth (YYYY-MM-DD):";
                } else if (enrollment.getChild3FirstName() == null) {
                    return "CON Enter Child 3 First Name:";
                } else if (enrollment.getChild3Sex() == null) {
                    return "CON Enter Child 3 Sex (M/F):";
                } else if (enrollment.getChild3BloodGroup() == null) {
                    return "CON Enter Child 3 Blood Group:";
                } else if (enrollment.getChild3DateOfBirth() == null) {
                    return "CON Enter Child 3 Date of Birth (YYYY-MM-DD):";
                } else if (enrollment.getChild4FirstName() == null) {
                    return "CON Enter Child 4 First Name:";
                } else if (enrollment.getChild4Sex() == null) {
                    return "CON Enter Child 4 Sex (M/F):";
                } else if (enrollment.getChild4BloodGroup() == null) {
                    return "CON Enter Child 4 Blood Group:";
                } else {
                    return "CON Enter Child 4 Date of Birth (YYYY-MM-DD):";
                }
                
            case "healthcare_data":
                if (enrollment.getHospitalName() == null) {
                    return "CON Enter Hospital Name:";
                } else if (enrollment.getHospitalLocation() == null) {
                    return "CON Enter Hospital Location:";
                } else {
                    return "CON Enter Hospital Code Number:";
                }
                
            default:
                return "CON Resume formal enrollment from where you left off:";
        }
    }

    private String handlePersonalData(String phone, String inputText, InformalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Personal Data - Field: " + currentField + ", Input: " + inputText);

        if (currentField == null) {
            currentField = determineCurrentFieldFromEnrollment(enrollment, "personal_data");
            if (currentField == null) {
                // All personal data is complete, move to next stage
                return moveToNextStage(phone, enrollment);
            }
            saveToSession(phone, "currentField", currentField);
            System.out.println("Auto-determined currentField: " + currentField);
        }
        
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
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "title");
                return "CON Enter your Title (Mr/Mrs/Ms/Dr):";
            case "title":
                if (!isValidTitle(inputText.trim())) {
                    return "CON Invalid title. Please enter Mr, Mrs, Ms, or Dr:";
                }
                enrollment.setTitle(inputText.trim());
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "surname");
                return "CON Enter your Surname:";
            case "surname":
                if (!isValidName(inputText.trim())) {
                    return "CON Invalid surname format. Please enter a valid surname:";
                }
                enrollment.setSurname(inputText.trim());
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "firstName");
                return "CON Enter your First Name:";
            case "firstName":
                if (!isValidName(inputText.trim())) {
                    return "CON Invalid first name format. Please enter a valid first name:";
                }
                enrollment.setFirstName(inputText.trim());
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "middleName");
                return "CON Enter your Middle Name (optional):";
            case "middleName":
                enrollment.setMiddleName(inputText != null ? inputText.trim() : "");
                saveToSession(phone, "currentField", "dateOfBirth");
                InformalfhisEnrollmentRepository.save(enrollment);
                return "CON Enter your Date of Birth (YYYY-MM-DD):";
            case "dateOfBirth":
                if (!isValidDateOfBirth(inputText)) {
                    return "CON Invalid date format or future date. Please use YYYY-MM-DD:";
                }
                enrollment.setDateOfBirth(inputText);
                enrollment.setUpdatedAt(LocalDateTime.now());
                InformalfhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
            default:
                // Invalid field, determine correct field and redirect
                String correctField = determineCurrentFieldFromEnrollment(enrollment, "personal_data");
                if (correctField != null) {
                    saveToSession(phone, "currentField", correctField);
                    return "CON Please enter " + getFieldDisplayName(correctField) + ":";
                }
                return "END Invalid field. Please start over.";
        }
    }

    private String handleSocialData(String phone, String inputText, InformalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Social Data - Field: " + currentField + ", Input: " + inputText);

        // CRITICAL FIX: If currentField is null, determine what field we need
        if (currentField == null) {
            currentField = determineCurrentFieldFromEnrollment(enrollment, "social_data");
            if (currentField == null) {
                // All social data is complete, move to next stage
                return moveToNextStage(phone, enrollment);
            }
            saveToSession(phone, "currentField", currentField);
            System.out.println("Auto-determined currentField: " + currentField);
        }
    
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
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "email");
                return "CON Enter your Email Address:";
            case "email":
                if (!isValidEmail(inputText.trim())) {
                    return "CON Invalid email format. Please enter a valid email address:";
                }
                enrollment.setEmail(inputText.trim());
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "bloodGroup");
                return "CON Enter your Blood Group:";
            case "bloodGroup":
                if (!isValidBloodGroup(inputText.trim())) {
                    return "CON Invalid blood group. Please enter A+, A-, B+, B-, AB+, AB-, O+, or O-:";
                }
                enrollment.setBloodGroup(inputText.trim());
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "residentialAddress");
                return "CON Enter your Residential Address:";
            case "residentialAddress":
                enrollment.setResidentialAddress(inputText.trim());
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "occupation");
                return "CON Enter your Occupation:";
            case "occupation":
                enrollment.setOccupation(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                InformalfhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
            default:
            // Invalid field, determine correct field and redirect
                String correctField = determineCurrentFieldFromEnrollment(enrollment, "social_data");
                if (correctField != null) {
                    saveToSession(phone, "currentField", correctField);
                    return "CON Please enter " + getFieldDisplayName(correctField) + ":";
                }
                return "END Invalid field. Please start over.";
        }
    }

    private String handleCorporateData(String phone, String inputText, InformalFhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Corporate Data - Field: " + currentField + ", Input: " + inputText);

        // CRITICAL FIX: If currentField is null, determine what field we need
        if (currentField == null) {
            currentField = determineCurrentFieldFromEnrollment(enrollment, "corporate_data");
            if (currentField == null) {
                // All corporate data is complete, move to next stage
                return moveToNextStage(phone, enrollment);
            }
            saveToSession(phone, "currentField", currentField);
            System.out.println("Auto-determined currentField: " + currentField);
        }
        
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
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "telephoneNumber");
                return "CON Enter your Telephone Number:";
            case "telephoneNumber":
                if (!isValidPhoneNumber(inputText.trim())) {
                    return "CON Invalid phone number format. Please enter a valid phone number:";
                }
                enrollment.setTelephoneNumber(inputText.trim());
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "organizationName");
                return "CON Enter your Organization Name:";
            case "organizationName":
                enrollment.setOrganizationName(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                InformalfhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
            default:
                String correctField = determineCurrentFieldFromEnrollment(enrollment, "corporate_data");
                if (correctField != null) {
                    saveToSession(phone, "currentField", correctField);
                    return "CON Please enter " + getFieldDisplayName(correctField) + ":";
                }
                return "END Invalid field. Please start over.";
        }
    }

    private String HandleEnrollmentCompletion(String phone, String inputText, InformalFhisEnrollment enrollment) {
        switch (inputText) {
            case "1":
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                InformalfhisEnrollmentRepository.save(enrollment);
                clearenrollmentSession(phone);
                return "END Enrollment submitted successfully! Thank you for enrolling in the FHIS program. Your reference number is: " + enrollment.getFhisNo();
            case "2":
                // Allow editing - reset to personal data step
                enrollment.setCurrentStep("personal_data");
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "fhisNo");
                return "CON EDIT MODE - Enter your FHIS Number:";
            case "0":
                clearenrollmentSession(phone);
                resetUserSession(phone); // Ensure full reset
                return HandleLevel1(phone, new String[0], true);
            default:
                return "CON Invalid choice. Please select:\n1. Confirm Enrollment\n2. Edit Details\n0. Cancel Enrollment";
        }
    }

    private String moveToNextStage(String phone, InformalFhisEnrollment enrollment) {
        switch (enrollment.getCurrentStep()) {
            case "personal_data":
                enrollment.setCurrentStep("social_data");
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "maritalStatus");
                saveToSession(phone, "waitingForContinue", true); // Flag to wait for user choice
                return "CON You have reached 25% of your enrolment process into FHIS. Please continue to conclude your FHIS registration in order to access affordable healthcare services.\n" + 
                    "1. Continue to social data\n0. Back";
            case "social_data":
                enrollment.setCurrentStep("corporate_data");
                InformalfhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "ninNumber");
                saveToSession(phone, "waitingForContinue", true); // Flag to wait for user choice
                return "CON You have reached 50% of your enrolment process into FHIS! Social Details Saved.\n" +
                        "We will now ask for your corporate data.\n" +
                        "1. Continue\n" +
                        "2. Cancel Enrollment";
            case "corporate_data":
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                InformalfhisEnrollmentRepository.save(enrollment);
                return "CON You have reached 75% of your enrolment process into FHIS! Almost done.\n" +
                        showEnrollmentSummary(enrollment) +
                        "\n1. Confirm Enrollment\n" +
                        "2. Edit Details\n" +
                        "0. Cancel Enrollment";
            default:
                return "END Enrollment submitted successfully! Thank you for enrolling in the FHIS program.";
        }
    }

    private String handleContinuationChoice(String phone, String choice, InformalFhisEnrollment enrollment) {
        Boolean waiting = (Boolean) retrieveFromSession(phone, "waitingForContinue");
        if (Boolean.TRUE.equals(waiting)) {
            saveToSession(phone, "waitingForContinue", false);
            if ("1".equals(choice)) {
                return promptForNextField((String) retrieveFromSession(phone, "currentField"));
            } else if ("0".equals(choice)) {
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            } else {
                return "CON Invalid. 1 to continue, 0 to back.";
            }
        }
        return null;
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

    private String showEnrollmentSummary(InformalFhisEnrollment enrollment) {
        return "REVIEW:\n" +
                "Name: " + enrollment.getTitle() + " " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                "FHIS: " + enrollment.getFhisNo() + "\n" +
                "Email: " + enrollment.getEmail() + "\n" +
                "Phone: " + enrollment.getTelephoneNumber();
    }

    private InformalFhisEnrollment GetorCreateInformalFhisEnrollment(String phoneNumber) {
        try {
            Optional<InformalFhisEnrollment> existingEnrollment = InformalfhisEnrollmentRepository.findByPhoneNumber(phoneNumber);
            if (existingEnrollment.isPresent()) {
                System.out.println("Found existing enrollment for phone: " + phoneNumber);
                InformalFhisEnrollment enrollment = existingEnrollment.get();
                // Check if it's a completed enrollment that needs to be handled differently
                if ("completed".equals(enrollment.getCurrentStep())) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "true");
                }
                return enrollment;
            }
            
            // CRITICAL FIX: Create new enrollment if none exists
            System.out.println("Creating new informal enrollment for phone: " + phoneNumber);
            InformalFhisEnrollment newEnrollment = new InformalFhisEnrollment();
            newEnrollment.setPhoneNumber(phoneNumber);
            newEnrollment.setEnrollmentType("Informal");
            newEnrollment.setCurrentStep("personal_data"); // Start with personal data
            newEnrollment.setCreatedAt(LocalDateTime.now());
            newEnrollment.setUpdatedAt(LocalDateTime.now());
            
            // Save the new enrollment to database
            InformalFhisEnrollment savedEnrollment = InformalfhisEnrollmentRepository.save(newEnrollment);
            System.out.println("Created new informal enrollment with ID: " + savedEnrollment.getId());
            
            return savedEnrollment;
            
        } catch (Exception e) {
            System.err.println("Error getting/creating informal FHIS enrollment: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private FormalFhisEnrollment GetorCreateFormalFhisEnrollment(String phoneNumber) {
        try {
            Optional<FormalFhisEnrollment> existingEnrollment = formalFhisEnrollmentRepository.findByPhoneNumber(phoneNumber);
            if (existingEnrollment.isPresent()) {
                System.out.println("Found existing formal enrollment for phone: " + phoneNumber);
                return existingEnrollment.get();
            }
            
            // CRITICAL FIX: Create new enrollment if none exists
            System.out.println("Creating new formal enrollment for phone: " + phoneNumber);
            FormalFhisEnrollment newEnrollment = new FormalFhisEnrollment();
            newEnrollment.setPhoneNumber(phoneNumber);
            newEnrollment.setEnrollmentType("Formal");
            newEnrollment.setCurrentStep("personal_data"); // Start with personal data
            newEnrollment.setCreatedAt(LocalDateTime.now());
            newEnrollment.setUpdatedAt(LocalDateTime.now());
            
            // Save the new enrollment to database
            FormalFhisEnrollment savedEnrollment = formalFhisEnrollmentRepository.save(newEnrollment);
            System.out.println("Created new formal enrollment with ID: " + savedEnrollment.getId());
            
            return savedEnrollment;
            
        } catch (Exception e) {
            System.err.println("Error getting/creating formal FHIS enrollment: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    private void clearenrollmentSession(String phoneNumber) {
        try {
            Set<String> keys = redisTemplate.keys(phoneNumber + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            System.out.println("Comprehensive enrollment session cleared for phone: " + phoneNumber);
        } catch (Exception e) {
            System.err.println("Error clearing enrollment session: " + e.getMessage());
        }
    }

    // --- Validation helper methods ---
    private boolean isValidFhisNumber(String fhisNo) {
        if (fhisNo == null || fhisNo.isEmpty())
            return false;
        // Allow 1-20 alphanumeric characters instead of 6-20
        return fhisNo.matches("^[A-Za-z0-9]{1,20}$");
    }

    private boolean isValidTitle(String title) {
        if (title == null)
            return false;
        String upperTitle = title.toUpperCase();
        return upperTitle.equals("MR") || upperTitle.equals("MRS") || upperTitle.equals("MS") || upperTitle.equals("DR");
    }

    private boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty())
            return false;
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
        if (status == null)
            return false;
        String upperStatus = status.toUpperCase();
        return upperStatus.equals("SINGLE") || upperStatus.equals("MARRIED") ||
                upperStatus.equals("DIVORCED") || upperStatus.equals("WIDOWED");
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty())
            return false;
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private boolean isValidBloodGroup(String bloodGroup) {
        if (bloodGroup == null)
            return false;
        String upper = bloodGroup.toUpperCase();
        return upper.matches("^(A|B|AB|O)[+-]$");
    }

    private boolean isValidNinNumber(String nin) {
        if (nin == null)
            return false;
        return nin.matches("^\\d{11}$"); // 11 digits
    }

    private boolean isValidPhoneNumber(String phone) {
        if (phone == null)
            return false;
        String normalized = phone.replaceAll("[^0-9]", "");
        return normalized.length() >= 10 && normalized.length() <= 14;
    }

    private String getFieldDisplayName(String fieldName) {
        switch (fieldName) {
            case "fhisNo":
                return "FHIS Number";
            case "title":
                return "Title";
            case "surname":
                return "Surname";
            case "firstName":
                return "First Name";
            case "middleName":
                return "Middle Name";
            case "dateOfBirth":
                return "Date of Birth";
            case "maritalStatus":
                return "Marital Status";
            case "email":
                return "Email Address";
            case "bloodGroup":
                return "Blood Group";
            case "residentialAddress":
                return "Residential Address";
            case "occupation":
                return "Occupation";
            case "ninNumber":
                return "NIN Number";
            case "telephoneNumber":
                return "Telephone Number";
            case "organizationName":
                return "Organization Name";
            default:
                return "the required information";
        }
    }
    // --- End Validation helper methods ---
}