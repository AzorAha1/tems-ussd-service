package com.example.tems.Tems.controller;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import com.example.tems.Tems.Session.RedisConfig;
import com.example.tems.Tems.model.FhisEnrollment;
import com.example.tems.Tems.model.Hospital;
import com.example.tems.Tems.model.Organization;
import com.example.tems.Tems.repository.FhisEnrollmentRepository;
import com.example.tems.Tems.repository.HospitalRepository;
import com.example.tems.Tems.repository.OrganizationRepository;
import com.example.tems.Tems.service.AggregatorService;
import com.example.tems.Tems.service.SubscriptionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ussdcontroller {

    // FIXED: Renamed variable to follow camelCase convention
    private OrganizationRepository organizationRepository;
    // private AggregatorService aggregatorService;
    // private SubscriptionService subscriptionService;
    private FhisEnrollmentRepository FhisEnrollmentRepository;
    private final HospitalRepository hospitalRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // all sessions 
    private static class SessionKeys {
        public static final String[] NAVIGATION_KEYS = {
            "selectedOrgId", "searchTerm", "currentPage", "totalPages", "org_ids", 
            "isMoreResultsFlow", "currentSubMenu", "hospitalPage", "totalHospitalPages", 
            "hospital_ids", "pendingHospitalId", "hospitalSearchTerm",
            "menuShown", "lastInteraction", "awaitingSearchTerm" // Added for menu tracking
        };
        
        public static final String[] ENROLLMENT_KEYS = {
            "currentFlow", "enrollmentOrgId", "currentField", "waitingForContinue",
            "existingEnrollmentFlow", "handlingExistingEnrollment", "viewingDetails"
        };
        
        public static final String[] ALL_KEYS = {
            "selectedOrgId", "searchTerm", "currentPage", "totalPages", "org_ids", 
            "isMoreResultsFlow", "currentSubMenu", "hospitalPage", "totalHospitalPages", 
            "hospital_ids", "pendingHospitalId", "hospitalSearchTerm",
            "currentFlow", "enrollmentOrgId", "currentField", "waitingForContinue",
            "existingEnrollmentFlow", "handlingExistingEnrollment", "viewingDetails",
            "menuShown", "lastInteraction", "awaitingSearchTerm" // Added for menu tracking
        };
    }

    // FIXED: Renamed constructor parameter and assignment
    @Autowired
    public ussdcontroller(OrganizationRepository organizationRepository, AggregatorService aggregatorService, SubscriptionService subscriptionService, FhisEnrollmentRepository FhisEnrollmentRepository, HospitalRepository hospitalRepository) {
        this.organizationRepository = organizationRepository;
        // this.aggregatorService = aggregatorService;
        // this.subscriptionService = subscriptionService;
        this.FhisEnrollmentRepository = FhisEnrollmentRepository;
        this.hospitalRepository = hospitalRepository;
        
    }
    

    // @PostMapping(
    //     value = "/ussd",
    //     consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE },
    //     produces = MediaType.APPLICATION_JSON_VALUE  // IMPORTANT: Return JSON
    // )
    // public Map<String, Object> handleUssdRequest(
    //     @RequestParam(name = "text", required = false) String text,
    //     @RequestParam(name = "input", required = false) String input,
    //     @RequestParam(name = "phoneNumber", required = false) String phoneNumber,
    //     @RequestParam(name = "phone", required = false) String phone,
    //     @RequestParam(name = "session_id", required = false) String sessionId,
    //     @RequestBody(required = false) Map<String, Object> body
    // ) {
    //     try {
    //         // Extract parameters from body if not in query params
    //         if (body != null) {
    //             if (phoneNumber == null && body.containsKey("phoneNumber")) {
    //                 phoneNumber = body.get("phoneNumber").toString();
    //             }
    //             if (phone == null && body.containsKey("phone")) {
    //                 phone = body.get("phone").toString();
    //             }
    //             if (input == null && body.containsKey("input")) {
    //                 input = body.get("input").toString();
    //             }
    //             if (text == null && body.containsKey("text")) {
    //                 text = body.get("text").toString();
    //             }
    //         }
            
    //         String phoneFinal = phoneNumber != null ? phoneNumber : (phone != null ? phone : "");
    //         String inputFinal = input != null ? input : (text != null ? text : "");
            
    //         if (phoneFinal.isEmpty()) {
    //             System.err.println("❌ Missing phone number in USSD request");
    //             return createUssdResponse(false, "Invalid request: missing phone number.");
    //         }
            
    //         // Process and get string response
    //         String response = processUssdRequest(inputFinal, phoneFinal);
            
    //         // Convert to JSON format
    //         return convertToJsonResponse(response);
            
    //     } catch (Exception e) {
    //         System.err.println("Fatal error in USSD request: " + e.getMessage());
    //         e.printStackTrace();
    //         return createUssdResponse(false, "Service temporarily unavailable. Please try again.");
    //     }
    // }
    @PostMapping(
        value = "/ussd",
        consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE },
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> handleUssdRequest(
        @RequestParam(name = "text", required = false) String text,
        @RequestParam(name = "input", required = false) String input,
        @RequestParam(name = "phoneNumber", required = false) String phoneNumber,
        @RequestParam(name = "phone", required = false) String phone,
        @RequestParam(name = "session_id", required = false) String sessionId,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        System.out.println("=== USSD REQUEST START ===");
        System.out.println("Params - text: '" + text + "', input: '" + input + "', phone: '" + phone + "', phoneNumber: '" + phoneNumber + "'");
        
        try {
            // Extract parameters from body if not in query params
            if (body != null) {
                System.out.println("Body: " + body);
                if (phoneNumber == null && body.containsKey("phoneNumber")) {
                    phoneNumber = body.get("phoneNumber").toString();
                }
                if (phone == null && body.containsKey("phone")) {
                    phone = body.get("phone").toString();
                }
                if (input == null && body.containsKey("input")) {
                    input = body.get("input").toString();
                }
                if (text == null && body.containsKey("text")) {
                    text = body.get("text").toString();
                }
            }
            
            String phoneFinal = phoneNumber != null ? phoneNumber : (phone != null ? phone : "");
            String inputFinal = input != null ? input : (text != null ? text : "");
            
            System.out.println("Final - phone: '" + phoneFinal + "', input: '" + inputFinal + "'");
            
            if (phoneFinal.isEmpty()) {
                System.err.println("❌ Missing phone number");
                return createUssdResponse(false, "Invalid request: missing phone number.");
            }
            
            // Process and get string response
            String response = processUssdRequest(inputFinal, phoneFinal);
            System.out.println("Response: " + response);
            
            // Convert to JSON format
            Map<String, Object> jsonResponse = convertToJsonResponse(response);
            System.out.println("=== USSD REQUEST END ===");
            return jsonResponse;
            
        } catch (Exception e) {
            System.err.println("❌❌❌ FATAL ERROR in USSD request ❌❌❌");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            System.err.println("Stack trace:");
            e.printStackTrace();
            System.out.println("=== USSD REQUEST END (WITH ERROR) ===");
            
            return createUssdResponse(false, "Service temporarily unavailable. Please try again.");
        }
    }
    @PostMapping("/test-redis")
    public Map<String, Object> testRedis(@RequestParam String phone) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Test write
            redisTemplate.opsForValue().set(phone + ":test", "working", 1, TimeUnit.MINUTES);
            
            // Test read
            Object value = redisTemplate.opsForValue().get(phone + ":test");
            
            // Test delete
            redisTemplate.delete(phone + ":test");
            
            result.put("status", "success");
            result.put("redis_working", true);
            result.put("value", value);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("redis_working", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
    private Map<String, Object> convertToJsonResponse(String response) {
        Map<String, Object> jsonResponse = new HashMap<>();
        
        if (response.startsWith("CON ")) {
            jsonResponse.put("continue", true);
            jsonResponse.put("message", response.substring(4)); // Remove "CON "
        } else if (response.startsWith("END ")) {
            jsonResponse.put("continue", false);
            jsonResponse.put("message", response.substring(4)); // Remove "END "
        } else {
            // Fallback
            jsonResponse.put("continue", false);
            jsonResponse.put("message", response);
        }
        
        return jsonResponse;
    }
    private Map<String, Object> createUssdResponse(boolean shouldContinue, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("continue", shouldContinue);
        response.put("message", message);
        return response;
    }
    private boolean isInitialShortcodeRequest(String input, String phoneNumber) {
        if (input == null) return false;
        
        String normalizedInput = input.replaceAll("[*#]", "").trim().toLowerCase();
        
        System.out.println("🔍 Checking if initial request - input: '" + input + "', normalized: '" + normalizedInput + "'");
        
        // Check if input is EXACTLY the shortcode "7447"
        if (normalizedInput.equals("7447")) {
            System.out.println("✅ Matched shortcode '7447' - this is initial request");
            return true;
        }
        
        // Also check for empty input
        if (normalizedInput.isEmpty()) {
            System.out.println("✅ Empty input - this is initial request");
            return true;
        }
        
        // ✅ FIXED: Safe type checking for menuShown
        Object menuShown = retrieveFromSession(phoneNumber, "menuShown");
        boolean isMenuShown = false;
        if (menuShown != null) {
            if (menuShown instanceof Boolean) {
                isMenuShown = (Boolean) menuShown;
            } else if (menuShown instanceof String) {
                isMenuShown = "true".equalsIgnoreCase((String) menuShown);
            }
        }
        
        if (isMenuShown) {
            System.out.println("❌ Menu already shown - this is a follow-up request");
            return false;
        }
        
        // Single digit inputs should NOT be initial
        if (normalizedInput.matches("^[0-9]$")) {
            System.out.println("❌ Single digit input - not an initial request");
            return false;
        }
        
        // Check if no session exists
        boolean hasNoSession = Arrays.stream(SessionKeys.ALL_KEYS)
            .noneMatch(key -> Boolean.TRUE.equals(redisTemplate.hasKey(phoneNumber + ":" + key)));
        
        if (hasNoSession) {
            System.out.println("✅ No session found - treating as initial request");
            return true;
        }
        
        System.out.println("❌ Not an initial request - normalized: '" + normalizedInput + "'");
        return false;
    }
    // private String processUssdRequest(String inputText, String phoneNumber) {
    //     String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
    //     if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
    //         return "END Invalid phone number provided.";
    //     }
        
    //     String inputedText = (inputText == null) ? "" : inputText.trim();
        
    //     // Only remove # at the very end of input
    //     if (inputedText.endsWith("#")) {
    //         inputedText = inputedText.substring(0, inputedText.length() - 1);
    //     }
        
    //     System.out.println("📞 Processing USSD - Phone: " + normalizedPhoneNumber + ", Input: '" + inputedText + "'");

    //     // Extend session on every request
    //     extendUserSession(normalizedPhoneNumber);

    //     // CRITICAL: Check if this is initial shortcode request (e.g., "7447")
    //     if (isInitialShortcodeRequest(inputedText, normalizedPhoneNumber)) {
    //         System.out.println("✅ Initial USSD request detected - showing welcome menu");
    //         clearNavigationSession(normalizedPhoneNumber);
    //         return HandleLevel1(normalizedPhoneNumber, new String[0], true);
    //     }

    //     // Check for duplicate requests
    //     String requestId = normalizedPhoneNumber + ":" + inputedText + ":" + System.currentTimeMillis()/1000;
    //     if (isDuplicateRequest(requestId, inputedText)) {
    //         return "CON Processing your request...";
    //     }

    //     // Check for FHIS enrollment flow
    //     String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
    //     System.out.println("Current Flow: " + currentFlow + ", Input: " + inputedText);

    //     if ("fhis_enrollment".equals(currentFlow)) {
    //         return handleFHISEnrollmentFlow(normalizedPhoneNumber, inputedText);
    //     }
        
    //     // Parse input into steps
    //     String[] parts = inputedText != null ? inputedText.split("\\*") : new String[0];
    //     int step = parts.length;
        
    //     System.out.println("Main USSD Flow - Step: " + step + ", Parts: " + Arrays.toString(parts));

    //     // Main USSD flow
    //     switch (step) {
    //         case 0:
    //             clearNavigationSession(normalizedPhoneNumber);
    //             Optional<FhisEnrollment> existing = FhisEnrollmentRepository.findByPhoneNumber(normalizedPhoneNumber);
    //             if (existing.isPresent() && !"completed".equals(existing.get().getCurrentStep()) && 
    //                 existing.get().getCurrentStep() != null && !"sector_selection".equals(existing.get().getCurrentStep())) {
                    
    //                 FhisEnrollment enrollment = existing.get();
    //                 return "CON Welcome back!\n" +
    //                     "You have a " + enrollment.getEnrollmentType() + " enrollment in progress.\n" +
    //                     "Progress: " + getProgressPercentage(enrollment.getCurrentStep()) + "%\n\n" +
    //                     "1. Continue Enrollment\n" +
    //                     "2. Start Fresh Search\n" +
    //                     "0. Exit";
    //             }
    //             return HandleLevel1(normalizedPhoneNumber, parts, true);
                
    //         case 1:
    //             return HandleLevel2(parts[0], normalizedPhoneNumber, parts);
                
    //         case 2:
    //             return HandleLevel3(parts[1], normalizedPhoneNumber, parts);
                
    //         case 3:
    //             return handleLevel4(parts[2], normalizedPhoneNumber, parts);
                
    //         case 4:
    //             return handlelevel5(parts[3], normalizedPhoneNumber, parts);
                
    //         default:
    //             resetUserSession(normalizedPhoneNumber);
    //             return "END Session expired. Please start over.";
    //     }
    // }
    // private String processUssdRequest(String inputText, String phoneNumber) {
    //     String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
    //     if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
    //         return "END Invalid phone number provided.";
    //     }
        
    //     String inputedText = (inputText == null) ? "" : inputText.trim();
        
    //     // Only remove # at the very end of input
    //     if (inputedText.endsWith("#")) {
    //         inputedText = inputedText.substring(0, inputedText.length() - 1);
    //     }

    //     if (inputedText.equals(normalizedPhoneNumber) || inputedText.equals(phoneNumber)) {
    //         System.out.println("⚠️ Detected phone number as input - ignoring this request");
    //     // Check if there's an active session
    //         String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
    //         if (currentFlow != null) {
    //             return "CON Processing your request...";
    //         } else {
    //             return HandleLevel1(normalizedPhoneNumber, new String[0], true);
    //         }
    //     }
        
    //     System.out.println("📞 Processing USSD - Phone: " + normalizedPhoneNumber + ", Input: '" + inputedText + "'");

    //     // Extend session on every request
    //     extendUserSession(normalizedPhoneNumber);

    //     // CRITICAL: Check if this is initial shortcode request (e.g., "7447")
    //     if (isInitialShortcodeRequest(inputedText, normalizedPhoneNumber)) {
    //         System.out.println("✅ Initial USSD request detected - showing welcome menu");
    //         clearNavigationSession(normalizedPhoneNumber);
    //         return HandleLevel1(normalizedPhoneNumber, new String[0], true);
    //     }

    //     // Check for duplicate requests
    //     String requestId = normalizedPhoneNumber + ":" + inputedText + ":" + System.currentTimeMillis()/1000;
    //     if (isDuplicateRequest(requestId, inputedText)) {
    //         return "CON Processing your request...";
    //     }

    //     // Check for FHIS enrollment flow
    //     String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
    //     System.out.println("Current Flow: " + currentFlow + ", Input: " + inputedText);

    //     if ("fhis_enrollment".equals(currentFlow)) {
    //         return handleFHISEnrollmentFlow(normalizedPhoneNumber, inputedText);
    //     }
        
    //     // Parse input into steps
    //     String[] parts = inputedText != null ? inputedText.split("\\*") : new String[0];
    //     int step = parts.length;
        
    //     System.out.println("Main USSD Flow - Step: " + step + ", Parts: " + Arrays.toString(parts));

    //     // CRITICAL FIX: Better error handling for each step
    //     try {
    //         switch (step) {
    //             case 0:
    //                 clearNavigationSession(normalizedPhoneNumber);
    //                 Optional<FhisEnrollment> existing = FhisEnrollmentRepository.findByPhoneNumber(normalizedPhoneNumber);
    //                 if (existing.isPresent() && !"completed".equals(existing.get().getCurrentStep()) && 
    //                     existing.get().getCurrentStep() != null && !"sector_selection".equals(existing.get().getCurrentStep())) {
                        
    //                     FhisEnrollment enrollment = existing.get();
    //                     return "CON Welcome back!\n" +
    //                         "You have a " + enrollment.getEnrollmentType() + " enrollment in progress.\n" +
    //                         "Progress: " + getProgressPercentage(enrollment.getCurrentStep()) + "%\n\n" +
    //                         "1. Continue Enrollment\n" +
    //                         "2. Start Fresh Search\n" +
    //                         "0. Exit";
    //                 }
    //                 return HandleLevel1(normalizedPhoneNumber, parts, true);
                    
    //             case 1:
    //                 System.out.println("Calling HandleLevel2 with choice: '" + parts[0] + "'");
    //                 return HandleLevel2(parts[0], normalizedPhoneNumber, parts);
                    
    //             case 2:
    //                 System.out.println("Calling HandleLevel3 with choice: '" + parts[1] + "'");
    //                 return HandleLevel3(parts[1], normalizedPhoneNumber, parts);
                    
    //             case 3:
    //                 System.out.println("Calling handleLevel4 with choice: '" + parts[2] + "'");
    //                 return handleLevel4(parts[2], normalizedPhoneNumber, parts);
                    
    //             case 4:
    //                 System.out.println("Calling handlelevel5 with choice: '" + parts[3] + "'");
    //                 return handlelevel5(parts[3], normalizedPhoneNumber, parts);
                    
    //             default:
    //                 resetUserSession(normalizedPhoneNumber);
    //                 return "END Session expired. Please start over.";
    //         }
    //     } catch (Exception e) {
    //         System.err.println("❌ Error processing step " + step + ": " + e.getMessage());
    //         e.printStackTrace();
            
    //         // Return user-friendly error instead of generic message
    //         return "END An error occurred. Please dial *7447# to start over.";
    //     }
    // }
    private String processUssdRequest(String inputText, String phoneNumber) {
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
            return "END Invalid phone number provided.";
        }
        
        String inputedText = (inputText == null) ? "" : inputText.trim();
        
        // only remove # at teh very end of input
        if (inputedText.endsWith("#")) {
            inputedText = inputedText.substring(0, inputedText.length() - 1);
        }

        // detect if input is phone number
        if (inputedText.equals(normalizedPhoneNumber) || inputedText.equals(phoneNumber)) {
            System.out.println("⚠️ Detected phone number as input - ignoring");
            String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
            if (currentFlow != null) {
                return "CON Processing your request...";
            } else {
                return HandleLevel1(normalizedPhoneNumber, new String[0], true);
            }
        }
        
        System.out.println("📞 Processing USSD - Phone: " + normalizedPhoneNumber + ", Input: '" + inputedText + "'");

        extendUserSession(normalizedPhoneNumber);

        if (isInitialShortcodeRequest(inputedText, normalizedPhoneNumber)) {
            System.out.println("✅ Initial USSD request detected");
            clearNavigationSession(normalizedPhoneNumber);
            return HandleLevel1(normalizedPhoneNumber, new String[0], true);
        }

        String requestId = normalizedPhoneNumber + ":" + inputedText + ":" + System.currentTimeMillis()/1000;
        if (isDuplicateRequest(requestId, inputedText)) {
            return "CON Processing your request...";
        }

        // 🔥 NEW: SESSION-BASED ROUTING INSTEAD OF PARSING "*"
        
        // Check FHIS enrollment flow first
        String currentFlow = (String) retrieveFromSession(normalizedPhoneNumber, "currentFlow");
        System.out.println("Current Flow: " + currentFlow + ", Input: " + inputedText);
        if ("fhis_enrollment".equals(currentFlow)) {
            System.out.println("Routing to Fhis Enrollment flow");
            return handleFHISEnrollmentFlow(normalizedPhoneNumber, inputedText);
        }

        // Check if we're waiting for a search term
        Object awaitingSearchObj = retrieveFromSession(normalizedPhoneNumber, "awaitingSearchTerm");
        boolean awaitingSearch = false;
        if (awaitingSearchObj != null) {
            if (awaitingSearchObj instanceof Boolean) {
                awaitingSearch = (Boolean) awaitingSearchObj;
            } else if (awaitingSearchObj instanceof String) {
                awaitingSearch = "true".equalsIgnoreCase((String) awaitingSearchObj);
            }
        }
        
        if (awaitingSearch) {
            System.out.println("🔍 User is providing search term");
            return HandleLevel2(inputedText, normalizedPhoneNumber, new String[]{inputedText});
        }
        // check selectedorgid before org_ids
        Long selectedOrgId = getLongFromSession(normalizedPhoneNumber, "selectedOrgId");
        if (selectedOrgId != null) {
            System.out.println("🏢 User is navigating organization menu");
            String currentSubMenu = (String) retrieveFromSession(normalizedPhoneNumber, "currentSubMenu");
            if ("more_info".equals(currentSubMenu)) {
                System.out.println("User is in 'more_info' submenu");
                return handleLevel4(inputedText, normalizedPhoneNumber, new String[]{inputedText});
            } else {
                System.out.println("User is in main org menu");
                return HandleLevel3(inputedText, normalizedPhoneNumber, new String[]{inputedText});
            }
        } 

        // Check if we have search results (user is selecting from list)
        List<Long> orgIds = getOrgIdsFromSession(normalizedPhoneNumber);
        if (orgIds != null && !orgIds.isEmpty()) {
            System.out.println("📋 User is selecting from organization list");
            return HandleLevel3(inputedText, normalizedPhoneNumber, new String[]{inputedText});
        }
        // Default: main menu selection
        System.out.println("🏠 User is at main menu");
        return HandleLevel2(inputedText, normalizedPhoneNumber, new String[]{inputedText});
    }
    private static final int MAX_ORGANIZATIONS_PER_PAGE = 5;
    // private static final int SESSION_TIMEOUT_MINUTES = 10;
    // private static final int MIN_FHIS_NUMBER_LENGTH = 6;



    // private String HandleLevel1(String phone, String[] parts, boolean isInitial) {
    //     if (isInitial) {
    //         // Check if user is in the middle of FHIS enrollment
    //         String currentFlow = (String) retrieveFromSession(phone, "currentFlow");
    //         if (currentFlow != null && (currentFlow.equals("fhis_enrollment") || currentFlow.equals("formal_fhis_enrollment"))) {
    //             return "CON You have an ongoing enrollment.\n1. Continue\n2. Start Fresh\n0. Exit";
    //         }
    //     }

    //     return "CON Welcome to TEMS SERVICE\n" +
    //             "Enter the name or initials of the organization you want to search for:";
    // }
    private String HandleLevel1(String phone, String[] parts, boolean isInitial) {
        if (isInitial) {
            // Check if user is in the middle of FHIS enrollment
            String currentFlow = (String) retrieveFromSession(phone, "currentFlow");
            if (currentFlow != null && (currentFlow.equals("fhis_enrollment") || currentFlow.equals("formal_fhis_enrollment"))) {
                return "CON You have an ongoing enrollment.\n1. Continue\n2. Start Fresh\n0. Exit";
            }
            
            // 🔥 CRITICAL FIX: Save a session marker to indicate we've shown the menu
            saveToSession(phone, "menuShown", "true");
            saveToSession(phone, "lastInteraction", System.currentTimeMillis());
        }

        // Welcome menu - this will be shown when input is "7447"
        return "CON Welcome to TEMS SERVICE\n\n" +
            "1. Search Organizations\n" +
            "2. About TEMS\n" +
            "0. Exit";
    }

    // private String HandleLevel2(String text, String phone, String[] parts) {
    //     // Clear any stale organization selection
    //     saveToSession(phone, "selectedOrgId", null);
    //     saveToSession(phone, "currentSubMenu", null);
        
    //     if (text == null || text.trim().isEmpty()) {
    //         return "CON Enter the name or initials of the organization you want to search for:";
    //     }
    
    //     Pageable firstPage = PageRequest.of(0, 5);
    //     Page<Organization> results = handleOrganizationSearch(text.trim(), firstPage);
    //     saveToSession(phone, "isMoreResultsFlow", false);
    
    //     if (results.isEmpty()) {
    //         return "END No matches for: " + text.trim();
    //     }
    
    //     // Save search data to session
    //     saveToSession(phone, "searchTerm", text.trim());
    //     saveToSession(phone, "currentPage", 0);
    //     saveToSession(phone, "totalPages", (int) results.getTotalPages());
    
    //     List<Long> orgIds = results.getContent().stream()
    //             .map(Organization::getId)
    //             .collect(Collectors.toList());
    //     saveToSession(phone, "org_ids", orgIds);

    //     return showOrganizationoptions(results.getContent(), 0, (int) results.getTotalPages());
    // }
    private String HandleLevel2(String text, String phone, String[] parts) {
        System.out.println("📋 HandleLevel2 called - text: '" + text + "', phone: '" + phone + "'");
        
        try {
            // 🔥 NEW: Check if we're waiting for search term
            Object awaitingSearchObj = retrieveFromSession(phone, "awaitingSearchTerm");
            boolean awaitingSearch = false;
            if (awaitingSearchObj != null) {
                if (awaitingSearchObj instanceof Boolean) {
                    awaitingSearch = (Boolean) awaitingSearchObj;
                } else if (awaitingSearchObj instanceof String) {
                    awaitingSearch = "true".equalsIgnoreCase((String) awaitingSearchObj);
                }
            }
            
            if (awaitingSearch) {
                saveToSession(phone, "awaitingSearchTerm", null); // Clear flag
                
                // This is a search term, not a menu choice
                String searchTerm = text.trim();
                System.out.println("✅ Processing search term: '" + searchTerm + "'");
                
                if (searchTerm.isEmpty()) {
                    return "CON Please enter an organization name:";
                }
                
                Pageable firstPage = PageRequest.of(0, 5);
                Page<Organization> results = handleOrganizationSearch(searchTerm, firstPage);
                
                if (results.isEmpty()) {
                    return "END No matches for: " + searchTerm;
                }
                
                // Save search data
                saveToSession(phone, "searchTerm", searchTerm);
                saveToSession(phone, "currentPage", 0);
                saveToSession(phone, "totalPages", (int) results.getTotalPages());
                
                List<Long> orgIds = results.getContent().stream()
                        .map(Organization::getId)
                        .collect(Collectors.toList());
                saveToSession(phone, "org_ids", orgIds);
                
                return showOrganizationoptions(results.getContent(), 0, (int) results.getTotalPages());
            }
            
            // Clear stale data
            try {
                saveToSession(phone, "selectedOrgId", null);
                saveToSession(phone, "currentSubMenu", null);
            } catch (Exception e) {
                System.err.println("⚠️ Warning: Could not clear session data: " + e.getMessage());
            }
            
            // Validate input
            if (text == null || text.trim().isEmpty()) {
                System.err.println("❌ Empty text in HandleLevel2");
                return "CON Invalid input. Please select:\n\n" +
                    "1. Search Organizations\n" +
                    "2. About TEMS\n" +
                    "0. Exit";
            }
            
            String choice = text.trim();
            System.out.println("Processing choice: '" + choice + "'");
            
            // Handle menu choices
            switch (choice) {
                case "1":
                    System.out.println("✅ User selected: Search Organizations");
                    saveToSession(phone, "awaitingSearchTerm", true);  // 🔥 SAVE STATE
                    return "CON Enter the name or initials of the organization you want to search for:";
                    
                case "2":
                    System.out.println("✅ User selected: About TEMS");
                    return "END TEMS (Terracotta Easy Mobile Solutions)\n" +
                        "A service to help you find organization information easily.\n\n" +
                        "Dial *7447# to start.";
                    
                case "0":
                    System.out.println("✅ User selected: Exit");
                    try {
                        resetUserSession(phone);
                    } catch (Exception e) {
                        System.err.println("⚠️ Session reset failed but continuing: " + e.getMessage());
                    }
                    return "END Thank you for using TEMS SERVICE!";
                    
                default:
                    System.out.println("❌ Invalid choice: '" + choice + "'");
                    return "CON Invalid choice. Please select:\n\n" +
                        "1. Search Organizations\n" +
                        "2. About TEMS\n" +
                        "0. Exit";
            }
            
        } catch (Exception e) {
            System.err.println("❌ CRITICAL ERROR in HandleLevel2: " + e.getMessage());
            e.printStackTrace();
            return "END Error processing request. Please dial *7447# to try again.";
        }
    }
    private String safeHandle(String methodName, java.util.function.Supplier<String> handler) {
        try {
            return handler.get();
        } catch (Exception e) {
            System.err.println("❌ Error in " + methodName + ": " + e.getMessage());
            e.printStackTrace();
            return "END An error occurred. Please dial *7447# to start over.";
        }
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
                "4. More Menu\n" +
                "0. Main Menu";
    }

    // private String HandleLevel3(String choice, String phone, String[] parts) {
    //     // First check if we have a selected org (we're in org menu)
    //     Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        
    //     if (selectedOrgId != null) {
    //         // We're in an organization's menu
    //         Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
    //         if (!orgOptional.isPresent()) {
    //             clearNavigationSession(phone);
    //             return "END Organization not found. Please try again.";
    //         }
    //         return handleOrganizationMenu(choice, orgOptional.get(), phone);
    //     }
        
    //     // We're selecting from search results
    //     return handleOrganizationSelection(choice, phone);
    // }
    private String HandleLevel3(String choice, String phone, String[] parts) {
    // Check if we're coming from "1. Search Organizations"
        if (parts[0].equals("1") && parts.length == 2) {
            // This is the search query after selecting "1"
            String searchTerm = choice.trim();
            
            if (searchTerm.isEmpty()) {
                return "CON Enter the name or initials of the organization:";
            }
            
            Pageable firstPage = PageRequest.of(0, 5);
            Page<Organization> results = handleOrganizationSearch(searchTerm, firstPage);
            
            if (results.isEmpty()) {
                return "END No matches for: " + searchTerm;
            }
            
            // Save search data to session
            saveToSession(phone, "searchTerm", searchTerm);
            saveToSession(phone, "currentPage", 0);
            saveToSession(phone, "totalPages", (int) results.getTotalPages());
            
            List<Long> orgIds = results.getContent().stream()
                    .map(Organization::getId)
                    .collect(Collectors.toList());
            saveToSession(phone, "org_ids", orgIds);
            
            return showOrganizationoptions(results.getContent(), 0, (int) results.getTotalPages());
        }
        
        // Rest of your existing HandleLevel3 code...
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        
        if (selectedOrgId != null) {
            Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
            if (!orgOptional.isPresent()) {
                clearNavigationSession(phone);
                return "END Organization not found. Please try again.";
            }
            return handleOrganizationMenu(choice, orgOptional.get(), phone);
        }
        
        return handleOrganizationSelection(choice, phone);
    }

    private String handleOrganizationSelection(String choice, String phone) {
        if (choice == null || choice.trim().isEmpty()) {
            System.err.println("Empty choice received for phone: " + phone);
            return "END Invalid input. Please try again by dialing the USSD code.";
        }
        
        List<Long> orgids = getOrgIdsFromSession(phone);
        if (orgids == null || orgids.isEmpty()) {
            System.err.println("No org_ids found in session for phone: " + phone);
            return "END Session expired. Please start over.";
        }
        
        try {
            int selection = Integer.parseInt(choice);
            
            if (selection == 0) {
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            }
            
            // Handle pagination BEFORE checking selection bounds
            if (selection == 6) {
                String searchTerm = (String) retrieveFromSession(phone, "searchTerm");
                Integer currentPage = (Integer) retrieveFromSession(phone, "currentPage");
                Integer totalPages = (Integer) retrieveFromSession(phone, "totalPages");
                
                if (currentPage < totalPages - 1) {
                    return handleMoreResults(phone);
                } else {
                    return "CON No more results available.\n0. Back";
                }
            }
            
            // CRITICAL: Calculate actual available options on current page
            Integer currentPage = (Integer) retrieveFromSession(phone, "currentPage");
            int startIndex = 0; // For current page display
            int maxDisplayedOptions = Math.min(orgids.size() - startIndex, MAX_ORGANIZATIONS_PER_PAGE);
            
            if (selection < 1 || selection > maxDisplayedOptions) {
                return "END Invalid selection. Please enter a number between 1 and " + maxDisplayedOptions + ".";
            }
            
            // Get the correct organization ID based on selection
            Long selectedID = orgids.get(selection - 1);
            saveToSession(phone, "selectedOrgId", selectedID);
            
            System.out.println("Selected organization ID: " + selectedID + " for choice: " + selection);
            
            Optional<Organization> selectedOrgOptional = organizationRepository.findById(selectedID);
            if (!selectedOrgOptional.isPresent()) {
                System.err.println("Organization not found for ID: " + selectedID);
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
                // return backToSearchResults(phone);
                System.out.println("User pressed '0' - returning to main menu");
                clearNavigationSession(phone);
                return HandleLevel1(phone, new String[0], false);
            default:
                return "END Invalid choice";
        }
    }

    // private String handleLevel4(String choice, String phone, String[] parts) {
    //     // Check if this is a "More Info" submenu flow first
    //     String currentSubMenu = (String) retrieveFromSession(phone, "currentSubMenu");
    //     if (currentSubMenu != null && currentSubMenu.equals("more_info")) {
    //         saveToSession(phone, "currentSubMenu", null); // Clear flag
    
    //         Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
    //         if (selectedOrgId == null) {
    //             return "END No organization selected. Please try again.";
    //         }
    //         Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
    //         if (!orgOptional.isPresent()) {
    //             return "END Organization not found. Please try again.";
    //         }
    //         Organization org = orgOptional.get();
    //         String orgName = org.getName().toUpperCase();
    
    //         // Handle More Info menu choices
    //         if ((orgName.contains("FHIS") || orgName.contains("FCT HEALTH") || orgName.contains("FCT HEALTH INSURANCE"))) {
    //             if ("1".equals(choice)) {
    //                 return handleFHISEnrollment(org, phone);
    //             } else if ("2".equals(choice)) {
    //                 return handleChangeHospital(phone); // Handle change hospital
    //             } else if ("0".equals(choice)) {
    //                 return showorgmenu(org); // Back to main org menu
    //             } else {
    //                 return "END Invalid choice for More Info menu.";
    //             }
    //         } else {
    //             // Non-FHIS org in More Info menu
    //             if ("0".equals(choice)) {
    //                 return showorgmenu(org);
    //             } else {
    //                 return "END Invalid choice for this organization.";
    //             }
    //         }
    //     }
    
    //     // --- Handle the initial organization menu choices (including "4. More") ---
    //     Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
    //     if (selectedOrgId == null) {
    //         return "END No organization selected. Please try again.";
    //     }
    //     Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
    //     if (!orgOptional.isPresent()) {
    //         return "END Organization not found. Please try again.";
    //     }
    //     Organization org = orgOptional.get();
    
    //     // THIS IS THE KEY FIX: Handle the organization menu choices properly
    //     return handleOrganizationMenu(choice, org, phone);
    // }
    private String handleLevel4(String choice, String phone, String[] parts) {
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        if (selectedOrgId == null) {
            System.out.println("No selectedOrgId in handleLevel4 - clearing stale session");
            saveToSession(phone, "currentSubMenu", null);
            return "END No organization selected. Please start over.";
        }
    
        Optional<Organization> orgOptional = organizationRepository.findById(selectedOrgId);
        if (!orgOptional.isPresent()) {
            saveToSession(phone, "currentSubMenu", null);
            return "END Organization not found. Please try again.";
        }
        Organization org = orgOptional.get();
    
        String currentSubMenu = (String) retrieveFromSession(phone, "currentSubMenu");
        System.out.println("HandleLevel4 - currentSubMenu: " + currentSubMenu + ", choice: " + choice);
        
        if (currentSubMenu != null && currentSubMenu.equals("more_info")) {
            saveToSession(phone, "currentSubMenu", null); // Clear immediately
            
            String orgName = org.getName().toUpperCase();
            if (!(orgName.contains("FHIS") || orgName.contains("FCT HEALTH") || orgName.contains("FCT HEALTH INSURANCE"))) {
                System.out.println("Stale more_info state for non-FHIS org - redirecting");
                return showorgmenu(org);
            }
            
            switch (choice) {
                case "1":
                    if (orgName.contains("FHIS") || orgName.contains("FCT HEALTH") || orgName.contains("FCT HEALTH INSURANCE")) {
                        System.out.println("User selected FHIS enrollment from More Info menu");
                        return handleFHISEnrollment(org, phone);
                    } else {
                        System.out.println("Invalid FHIS enrollment attempt for non-FHIS org");
                        return "END Invalid choice for More Info menu.";
                    }
                case "2":
                    if (orgName.contains("FHIS") || orgName.contains("FCT HEALTH") || orgName.contains("FCT HEALTH INSURANCE")) {
                        return handleChangeHospital(phone);
                    } else {
                        return "END Invalid option for this organization.";
                    }
                case "0":
                    System.out.println("User selected '0' to return to main org menu from More Info");
                    clearNavigationSession(phone);
                    saveToSession(phone, "menuShow", true);
                default:
                    return "END Invalid choice for More Info menu.";
            }
        }
    
        // Regular organization menu
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
        String orgName = org.getName().toUpperCase();
        
        if (orgName.contains("FHIS") || orgName.contains("FCT HEALTH")) {
            return "CON " + org.getName() + " - More Info:\n" +
                    "1. Enroll\n" +
                    "2. Change Hospital\n" +
                    "0. Main Menu";
        } else {
            return "CON " + org.getName() + " - More Info:\n" +
                    "No additional services available.\n" +
                    "0. Main Menu";
        }
    }
    // handle change hospital
    private String handleChangeHospital(String phone) {
        // Check if user has an enrollment
        Optional<FhisEnrollment> existingEnrollment = FhisEnrollmentRepository.findByPhoneNumber(phone);
        
        if (!existingEnrollment.isPresent()) {
            return "END No FHIS enrollment found. Please enroll first.";
        }
        
        FhisEnrollment enrollment = existingEnrollment.get();
        if (enrollment.getHospital() == null) {
            return "END No hospital assigned yet. Please complete your enrollment first.";
        }
        
        // Show current hospital and options
        Hospital currentHospital = enrollment.getHospital();
        String currentHospitalInfo = currentHospital.getName();
        if (currentHospital.getLocation() != null && !currentHospital.getLocation().isEmpty()) {
            currentHospitalInfo += " (" + currentHospital.getLocation() + ")";
        }
        
        saveToSession(phone, "currentFlow", "change_hospital");
        saveToSession(phone, "hospitalSearchPage", 0);
        
        return "CON Current Hospital: " + currentHospitalInfo + "\n\n" +
               "Search for new hospital:\n" +
               "1. Search by name\n" +
               "2. Browse by location\n" +
               "3. View all hospitals\n" +
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
    
        // CRITICAL FIX: Update the session with new page data
        saveToSession(phone, "currentPage", nextPage);
        List<Long> org_ids = results.getContent().stream()
                .map(Organization::getId)
                .collect(Collectors.toList());
        saveToSession(phone, "org_ids", org_ids);
        
        // CRITICAL FIX: Don't set isMoreResultsFlow here
        // The selection will be handled normally in HandleLevel3
        // saveToSession(phone, "isMoreResultsFlow", true); // REMOVE THIS LINE
        
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
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            System.err.println("⚠️ Cannot save to session - phone number is null/empty");
            return;
        }
        
        if (key == null || key.isEmpty()) {
            System.err.println("⚠️ Cannot save to session - key is null/empty");
            return;
        }
        
        try {
            String sessionkey = phoneNumber + ":" + key;
            int timeoutMinutes = key.equals("currentField") ? 15 : 10;
            
            if (value == null) {
                // Delete the key if value is null
                redisTemplate.delete(sessionkey);
            } else {
                redisTemplate.opsForValue().set(sessionkey, value, timeoutMinutes, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error saving to session (key: " + key + "): " + e.getMessage());
            // Don't throw - just log and continue
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
        // CRITICAL: Validate organization context before starting enrollment
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        if (selectedOrgId == null || !selectedOrgId.equals(org.getId())) {
            System.out.println("Invalid session state for FHIS enrollment - resetting");
            clearNavigationSession(phone);
            return "END Session expired. Please search for the organization again.";
        }
        
        // Check if user already has an enrollment
        String existingCheck = checkExistingEnrollment(phone);
        if (existingCheck != null) {
            saveToSession(phone, "currentFlow", "fhis_enrollment");
            saveToSession(phone, "enrollmentOrgId", org.getId());
            return existingCheck;
        }
    
        saveToSession(phone, "currentFlow", "fhis_enrollment");
        saveToSession(phone, "enrollmentOrgId", org.getId());
        return "CON Select enrollment type:\n" +
                "1. Informal Sector\n" +
                "2. Formal Sector\n" +
                "0. Back to menu";
    }
    
    //extend session time
    // private void extendUserSession(String phoneNumber) {
    //     try {
    //         // Define known session keys instead of using wildcard search
    //         String[] knownKeys = {
    //             "currentFlow", "enrollmentOrgId", "searchTerm", "currentPage", 
    //             "totalPages", "org_ids", "selectedOrgId", "isMoreResultsFlow",
    //             "currentField", "waitingForContinue", "existingEnrollmentFlow",
    //             "currentSubMenu", "handlingExistingEnrollment", "viewingDetails"
    //         };
            
    //         for (String keyType : knownKeys) {
    //             String fullKey = phoneNumber + ":" + keyType;
    //             Boolean exists = redisTemplate.hasKey(fullKey);
    //             if (Boolean.TRUE.equals(exists)) {
    //                 redisTemplate.expire(fullKey, 10, TimeUnit.MINUTES);
    //             }
    //         }
    //         System.out.println("Session extended for phone: " + phoneNumber);
    //     } catch (Exception e) {
    //         System.err.println("Error extending user session: " + e.getMessage());
    //     }
    // }
    private void extendUserSession(String phoneNumber) {
        try {
            // Use pipeline for batch operations
            Set<String> keysToExtend = new HashSet<>();
            for (String keyType : SessionKeys.ALL_KEYS) {
                String fullKey = phoneNumber + ":" + keyType;
                if (Boolean.TRUE.equals(redisTemplate.hasKey(fullKey))) {
                    keysToExtend.add(fullKey);
                }
            }
            
            // Batch extend all keys
            if (!keysToExtend.isEmpty()) {
                for (String key : keysToExtend) {
                    redisTemplate.expire(key, 15, TimeUnit.MINUTES);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extending sessions: " + e.getMessage());
        }
    }


    // Add a method to completely reset user session when they want to start fresh
    // private void resetUserSession(String phoneNumber) {
    //     System.out.println("Complete session reset for: " + phoneNumber);
    //     try {
    //         // Clear ALL keys including any stragglers
    //         Set<String> allKeys = redisTemplate.keys(phoneNumber + ":*");
    //         if (allKeys != null && !allKeys.isEmpty()) {
    //             redisTemplate.delete(allKeys);
    //             System.out.println("Deleted " + allKeys.size() + " keys for " + phoneNumber);
    //         }
    //     } catch (Exception e) {
    //         System.err.println("Error in full reset: " + e.getMessage());
    //         // Fallback to individual deletion
    //         for (String key : SessionKeys.ALL_KEYS) {
    //             try {
    //                 redisTemplate.delete(phoneNumber + ":" + key);
    //             } catch (Exception ex) {
    //                 // silently ignore individual key deletion errors
    //                 System.err.println("Error deleting key " + key + ": " + ex.getMessage());
    //             }
    //         }
    //     }
    // }
    private void resetUserSession(String phoneNumber) {
        System.out.println("🔄 Session reset requested for: " + phoneNumber);
        
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            System.err.println("⚠️ Cannot reset session - phone number is null/empty");
            return;
        }
        
        try {
            // Try to get keys matching pattern
            Set<String> allKeys = redisTemplate.keys(phoneNumber + ":*");
            
            if (allKeys != null && !allKeys.isEmpty()) {
                redisTemplate.delete(allKeys);
                System.out.println("✅ Deleted " + allKeys.size() + " keys for " + phoneNumber);
            } else {
                System.out.println("ℹ️ No keys found to delete for " + phoneNumber);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error in bulk reset: " + e.getMessage());
            
            // Fallback: try individual deletion
            int deleted = 0;
            for (String key : SessionKeys.ALL_KEYS) {
                try {
                    String fullKey = phoneNumber + ":" + key;
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(fullKey))) {
                        redisTemplate.delete(fullKey);
                        deleted++;
                    }
                } catch (Exception ex) {
                    // Silently continue
                }
            }
            System.out.println("✅ Fallback: Deleted " + deleted + " keys individually");
        }
    }


    private String handleFHISEnrollmentFlow(String phoneNumber, String inputText) {
        System.out.println("FHIS Enrollment Flow - Phone: " + phoneNumber + ", Input: " + inputText);
        try {
            String viewingDetails = (String) retrieveFromSession(phoneNumber, "viewingDetails");
            if ("true".equals(viewingDetails) && "0".equals(inputText)) {
                saveToSession(phoneNumber, "viewingDetails", null);
                return checkExistingEnrollment(phoneNumber);
            }
    
            String lastChoice = "";
            if (inputText != null && !inputText.isEmpty()) {
                String[] parts = inputText.split("\\*");
                lastChoice = parts.length > 0 ? parts[parts.length - 1] : "";
            }
    
            String existingFlow = (String) retrieveFromSession(phoneNumber, "existingEnrollmentFlow");
            if (existingFlow == null) {
                String existingCheck = checkExistingEnrollment(phoneNumber);
                if (existingCheck != null) {
                    return existingCheck;
                }
            }
    
            // CRITICAL FIX: Try to get enrollment, if null, we're in sector selection
            FhisEnrollment enrollment = GetorCreateFhisEnrollment(phoneNumber);
            if (enrollment == null) {
                // No enrollment exists, we should be in sector selection
                System.out.println("No enrollment found, handling sector selection");
                return handleSectorSelection(phoneNumber, lastChoice);
            }
    
            String currentStep = enrollment.getCurrentStep();
            if (currentStep == null) {
                enrollment.setCurrentStep("sector_selection");
                FhisEnrollmentRepository.save(enrollment);
                currentStep = "sector_selection";
            }
    
            System.out.println("Current Step: " + currentStep + ", Last Choice: " + lastChoice);
            
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
                case "social_data_formal":
                    return handleFormalSocialData(phoneNumber, inputText, enrollment);
                case "corporate_data":
                    return handleCorporateData(phoneNumber, lastChoice, enrollment);
                case "professional_data":
                    return handleProfessionalData(phoneNumber, lastChoice, enrollment);
                case "dependants_data":
                    return handleDependantsData(phoneNumber, inputText, enrollment);
                case "healthcare_provider_data":
                    return handleHealthcareProviderData(phoneNumber, inputText, enrollment);
                case "completed":
                    return HandleEnrollmentCompletion(phoneNumber, lastChoice, enrollment);
                default:
                    clearenrollmentSession(phoneNumber);
                    return "END Invalid enrollment step. Please start over.";
            }
        } catch (Exception e) {
            System.err.println("Error in FHIS enrollment flow: " + e.getMessage());
            e.printStackTrace(); // Added stack trace for debugging
            clearenrollmentSession(phoneNumber);
            return "END An error occurred. Please try again.";
        }
    }
    
    
    
    

    private String determineCurrentFieldFromEnrollment(FhisEnrollment enrollment, String currentStep) {
        switch (currentStep) {
            case "personal_data":
                if (enrollment.getFhisNo() == null) return "fhisNo";
                if ("Informal".equals(enrollment.getEnrollmentType()) && enrollment.getTitle() == null) return "title";
                if (enrollment.getSurname() == null) return "surname";
                if (enrollment.getFirstName() == null) return "firstName";
                if (enrollment.getMiddleName() == null) return "middleName";
                if (enrollment.getDateOfBirth() == null) return "dateOfBirth";
                if ("Formal".equals(enrollment.getEnrollmentType()) && enrollment.getSex() == null) return "sex";
                if (enrollment.getBloodGroup() == null) return "bloodGroup";
                break;
                
            case "social_data":
                // For Informal sector
                if (enrollment.getMaritalStatus() == null) return "maritalStatus";
                if (enrollment.getEmail() == null) return "email";
                if ("Informal".equals(enrollment.getEnrollmentType()) && enrollment.getBloodGroup() == null) return "bloodGroup";
                if (enrollment.getResidentialAddress() == null) return "residentialAddress";
                if ("Informal".equals(enrollment.getEnrollmentType()) && enrollment.getOccupation() == null) return "occupation";
                break;
                
            case "social_data_formal":
                // For Formal sector - different order
                if (enrollment.getMaritalStatus() == null) return "maritalStatus";
                if (enrollment.getTelephoneNumber() == null) return "telephoneNumber";
                if (enrollment.getResidentialAddress() == null) return "residentialAddress";
                if (enrollment.getEmail() == null) return "email";
                break;
                
            case "corporate_data":
                // Only for Informal
                if ("Informal".equals(enrollment.getEnrollmentType())) {
                    if (enrollment.getNinNumber() == null) return "ninNumber";
                    if (enrollment.getTelephoneNumber() == null) return "telephoneNumber";
                    if (enrollment.getOrganizationName() == null) return "organizationName";
                }
                break;
                
            case "professional_data":
                // Only for Formal
                if ("Formal".equals(enrollment.getEnrollmentType())) {
                    if (enrollment.getDesignation() == null) return "designation";
                    if (enrollment.getOccupation() == null) return "occupation";
                    if (enrollment.getPresentStation() == null) return "presentStation";
                    if (enrollment.getRank() == null) return "rank";
                    if (enrollment.getPfNumber() == null) return "pfNumber";
                    if (enrollment.getSdaName() == null) return "sdaName";
                }
                break;
                
            case "dependants_data":
                if (enrollment.getNumberOfChildren() == null) return "numberOfChildren";
                break;
                
            case "healthcare_provider_data":
                // Changed to use JPA relationship
                if (enrollment.getHospital() == null) return "hospitalSearch";
                break;
        }
        return null;
    }
   
    private String checkExistingEnrollment(String phoneNumber) {
        try {
            Optional<FhisEnrollment> existing = FhisEnrollmentRepository.findByPhoneNumber(phoneNumber);
            if (existing.isPresent()) {
                FhisEnrollment enrollment = existing.get();
                String currentStep = enrollment.getCurrentStep();
    
                // Skip if already handling
                String handling = (String) retrieveFromSession(phoneNumber, "handlingExistingEnrollment");
                if ("true".equals(handling)) {
                    return null;
                }
    
                if ("completed".equals(currentStep)) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "completed");
                    return "CON You already have a completed FHIS enrollment!\n" +
                            "Name: " + enrollment.getFirstName() + " " + enrollment.getSurname() + "\n" +
                            "FHIS No: " + enrollment.getFhisNo() + "\n\n" +
                            "Do you want to continue with this enrollment?\n" +
                            "1. Yes - View Details\n" +
                            "2. No - Start Fresh Enrollment\n" +
                            "0. Exit";
                } else if (currentStep != null && !"sector_selection".equals(currentStep)) {
                    saveToSession(phoneNumber, "existingEnrollmentFlow", "incomplete");
                    return "CON Incomplete FHIS enrollment found!\n" +
                            "Progress: " + getProgressPercentage(currentStep) + "% complete\n" +
                            "Last step: " + getStepDescription(currentStep) + "\n\n" +
                            "Do you want to continue where you left off?\n" +
                            "1. Yes - Continue Enrollment\n" +
                            "2. No - Start Fresh Enrollment\n" +
                            "0. Exit";
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking existing enrollment: " + e.getMessage());
        }
        return null;
    }
    private String getStepDescription(String step) {
        switch (step) {
            case "personal_data":
                return "Personal Information";
            case "social_data":
            case "social_data_formal":
                return "Social Information";
            case "corporate_data":
                return "Corporate Information";
            case "professional_data":
                return "Professional Information";
            case "dependants_data":
                return "Dependants Information";
            case "healthcare_provider_data":
                return "Healthcare Provider Selection";
            default:
                return "Unknown";
        }
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
        
        // Check if we're already handling existing enrollment
        String handlingExisting = (String) retrieveFromSession(phone, "handlingExistingEnrollment");
        
        // If we're NOT already handling existing enrollment, check for existing enrollments first
        if (!"true".equals(handlingExisting)) {
            try {
                Optional<FhisEnrollment> existing = FhisEnrollmentRepository.findByPhoneNumber(phone);
                
                if (existing.isPresent()) {
                    System.out.println("Found existing enrollment, handling choice");
                    return handleExistingEnrollmentChoice(phone, choice);
                }
            } catch (Exception e) {
                System.err.println("Error checking existing enrollments: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // FRESH ENROLLMENT - Handle sector selection choices
        switch (choice) {
            case "1":
                System.out.println("Creating new Informal enrollment");
                return createNewEnrollment(phone, "Informal");
                
            case "2":
                System.out.println("Creating new Formal enrollment");
                return createNewEnrollment(phone, "Formal");
                
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
    private String createNewEnrollment(String phone, String enrollmentType) {
        try {
            System.out.println("Creating new " + enrollmentType + " enrollment for phone: " + phone);
            
            FhisEnrollment enrollment = new FhisEnrollment();
            enrollment.setPhoneNumber(phone);
            enrollment.setEnrollmentType(enrollmentType);
            enrollment.setCurrentStep("personal_data");
            enrollment.setCreatedAt(LocalDateTime.now());
            enrollment.setUpdatedAt(LocalDateTime.now());
            
            // Save to database
            FhisEnrollment savedEnrollment = FhisEnrollmentRepository.save(enrollment);
            System.out.println("Saved enrollment with ID: " + savedEnrollment.getId());
            
            // Set session variables
            saveToSession(phone, "currentFlow", "fhis_enrollment");
            saveToSession(phone, "currentField", "fhisNo");
            saveToSession(phone, "handlingExistingEnrollment", "true");
            
            return "CON " + enrollmentType.toUpperCase() + " SECTOR\nEnter your FHIS Number:";
            
        } catch (Exception e) {
            System.err.println("Error creating new enrollment: " + e.getMessage());
            e.printStackTrace();
            return "END Error creating enrollment. Please try again.";
        }
    }

    private String handleExistingEnrollmentChoice(String phone, String choice) {
        try {
            // Now there's only ONE repository call since we have unified model
            Optional<FhisEnrollment> existing = FhisEnrollmentRepository.findByPhoneNumber(phone);
            
            if (!existing.isPresent()) {
                clearenrollmentSession(phone);
                return "END Enrollment not found.";
            }
            
            FhisEnrollment enrollment = existing.get();
            String enrollmentType = enrollment.getEnrollmentType();
            String currentStep = enrollment.getCurrentStep();
            
            switch (choice) {
                case "1": // Continue existing enrollment
                    saveToSession(phone, "currentFlow", "fhis_enrollment"); // Single flow now
                    saveToSession(phone, "currentField", determineCurrentFieldFromEnrollment(enrollment, currentStep));
                    saveToSession(phone, "handlingExistingEnrollment", "true");
                    
                    // Use single resume method that handles both types
                    return resumeEnrollmentStep(enrollment);
                    
                case "2": // Start fresh
                    // Delete existing enrollment and return to sector selection
                    FhisEnrollmentRepository.delete(enrollment);
                    clearenrollmentSession(phone);
                    
                    // Return to sector selection for fresh start
                    saveToSession(phone, "currentFlow", "fhis_enrollment");
                    saveToSession(phone, "handlingExistingEnrollment", "true");
                    
                    return "CON FRESH START - Select enrollment type:\n" +
                           "1. Informal Sector\n" +
                           "2. Formal Sector\n" +
                           "0. Back to menu";
                           
                case "0": // Back to main menu
                    clearenrollmentSession(phone);
                    resetUserSession(phone);
                    return HandleLevel1(phone, new String[0], true);
                    
                default:
                    return "CON Invalid choice. Please try again.\n1. Continue\n2. Start fresh\n0. Back";
            }
            
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
    private String resumeEnrollmentStep(FhisEnrollment enrollment) {
        String currentStep = enrollment.getCurrentStep();
        String enrollmentType = enrollment.getEnrollmentType();
        
        switch (currentStep) {
            case "personal_data":
                if (enrollment.getFhisNo() == null) {
                    return "CON " + enrollmentType.toUpperCase() + " SECTOR\nEnter your FHIS Number:";
                } else if ("Informal".equals(enrollmentType) && enrollment.getTitle() == null) {
                    return "CON Enter your Title (Mr/Mrs/Ms/Dr):";
                } else if (enrollment.getSurname() == null) {
                    return "CON Enter your Surname:";
                } else if (enrollment.getFirstName() == null) {
                    return "CON Enter your First Name:";
                } else if (enrollment.getMiddleName() == null) {
                    return "CON Enter your Middle Name (optional):";
                } else if (enrollment.getDateOfBirth() == null) {
                    return "CON Enter your Date of Birth (YYYY-MM-DD):";
                } else if ("Formal".equals(enrollmentType) && enrollment.getSex() == null) {
                    return "CON Enter your Sex (M/F):";
                } else {
                    return "CON Enter your Blood Group:";
                }
                
            case "social_data":
                if (enrollment.getMaritalStatus() == null) {
                    return "CON Enter your Marital Status:";
                } else if (enrollment.getEmail() == null) {
                    return "CON Enter your Email Address:";
                } else if ("Informal".equals(enrollmentType) && enrollment.getBloodGroup() == null) {
                    return "CON Enter your Blood Group:";
                } else if (enrollment.getResidentialAddress() == null) {
                    return "CON Enter your Residential Address:";
                } else if ("Informal".equals(enrollmentType)) {
                    return "CON Enter your Occupation:";
                } else {
                    // Formal has different fields in social_data
                    return "CON Continue with social data:";
                }
                
            case "corporate_data":
                if ("Informal".equals(enrollmentType)) {
                    if (enrollment.getNinNumber() == null) {
                        return "CON Enter your NIN Number:";
                    } else if (enrollment.getTelephoneNumber() == null) {
                        return "CON Enter your Telephone Number:";
                    } else {
                        return "CON Enter your Organization Name:";
                    }
                } else {
                    // Formal doesn't have corporate_data step
                    return "CON Continue with enrollment:";
                }
                
            case "professional_data":
                // Only formal has this step
                if ("Formal".equals(enrollmentType)) {
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
                }
                break;
                
            default:
                return "CON Resume enrollment from where you left off:";
        }
        
        return "CON Resume " + enrollmentType.toLowerCase() + " enrollment:";
    }
    // Add this method to your UssdController class


    // 2. FIXED: Optimized handlePersonalData method - reduced database calls
    private String handlePersonalData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        
        if (currentField == null) {
            currentField = determineCurrentFieldFromEnrollment(enrollment, "personal_data");
            if (currentField == null) {
                return moveToNextStage(phone, enrollment);
            }
            saveToSession(phone, "currentField", currentField);
        }
    
        // Batch validation before saving
        if ((inputText == null || inputText.trim().isEmpty()) && !currentField.equals("middleName")) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
    
        boolean fieldValid = true;
        String errorMessage = null;
        
        // Validate BEFORE database operations
        switch (currentField) {
            case "fhisNo":
                if (!isValidFhisNumber(inputText.trim())) {
                    fieldValid = false;
                    errorMessage = "Invalid FHIS Number format. Please enter a valid FHIS Number:";
                }
                break;
            case "surname":
            case "firstName":
                if (!isValidName(inputText.trim())) {
                    fieldValid = false;
                    errorMessage = "Invalid name format. Please enter a valid name:";
                }
                break;
            case "dateOfBirth":
                if (!isValidDateOfBirth(inputText.trim())) {
                    fieldValid = false;
                    errorMessage = "Invalid date. Use DD-MM-YYYY or YYYY-MM-DD:";
                }
                break;
        }
        
        if (!fieldValid) {
            return "CON " + errorMessage;
        }
        
        // Now save once validation passes
        try {
            updateEnrollmentField(enrollment, currentField, inputText.trim());
            enrollment.setUpdatedAt(LocalDateTime.now());
            FhisEnrollmentRepository.save(enrollment);
            
            // Determine next field
            String nextField = getNextField(currentField, enrollment.getEnrollmentType());
            if (nextField != null) {
                saveToSession(phone, "currentField", nextField);
                return getFieldPrompt(nextField);
            } else {
                return moveToNextStage(phone, enrollment);
            }
        } catch (Exception e) {
            System.err.println("Database error: " + e.getMessage());
            return "CON System error. Please try again:";
        }
    }
    private String convertToStandardDate(String dateInput) {
        try {
            String cleanDate = dateInput.trim().replace("/", "-").replace(".", "-");
            
            if (cleanDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return cleanDate; // Already in standard format
            } else if (cleanDate.matches("\\d{1,2}-\\d{1,2}-\\d{4}")) {
                // Convert DD-MM-YYYY to YYYY-MM-DD
                String[] parts = cleanDate.split("-");
                String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                return parts[2] + "-" + month + "-" + day;
            }
            return cleanDate;
        } catch (Exception e) {
            return dateInput; // Return original if conversion fails
        }
    }
    // 6. ADD HELPER METHODS for cleaner code:
    private void updateEnrollmentField(FhisEnrollment enrollment, String field, String value) {
        switch (field) {
            case "fhisNo": enrollment.setFhisNo(value); break;
            case "title": enrollment.setTitle(value); break;
            case "surname": enrollment.setSurname(value); break;
            case "firstName": enrollment.setFirstName(value); break;
            case "middleName": enrollment.setMiddleName(value); break;
            case "dateOfBirth": enrollment.setDateOfBirth(convertToStandardDate(value)); break;
            case "sex": enrollment.setSex(value.toUpperCase()); break;
            case "bloodGroup": enrollment.setBloodGroup(value); break;
        }
    }
    private String getNextField(String currentField, String enrollmentType) {
        if ("Formal".equals(enrollmentType)) {
            switch (currentField) {
                case "fhisNo": return "surname";
                case "surname": return "firstName";
                case "firstName": return "middleName";
                case "middleName": return "dateOfBirth";
                case "dateOfBirth": return "sex";
                case "sex": return "bloodGroup";
                case "bloodGroup": return null;
            }
        } else { // Informal
            switch (currentField) {
                case "fhisNo": return "title";
                case "title": return "surname";
                case "surname": return "firstName";
                case "firstName": return "middleName";
                case "middleName": return "dateOfBirth";
                case "dateOfBirth": return "bloodGroup";
                case "bloodGroup": return null;
            }
        }
        return null;
    }
    private String getFieldPrompt(String field) {
        switch (field) {
            case "fhisNo": return "CON Enter your FHIS Number:";
            case "title": return "CON Enter your Title (Mr/Mrs/Ms/Dr):";
            case "surname": return "CON Enter your Surname:";
            case "firstName": return "CON Enter your First Name:";
            case "middleName": return "CON Enter your Middle Name (optional):";
            case "dateOfBirth": return "CON Enter Date of Birth (DD-MM-YYYY):";
            case "sex": return "CON Enter your Sex (M/F):";
            case "bloodGroup": return "CON Enter your Blood Group:";
            default: return "CON Enter " + getFieldDisplayName(field) + ":";
        }
    }    
    
    private String handleProfessionalData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        System.out.println("Professional Data - Field: " + currentField + ", Input: " + inputText);
    
        if (currentField == null) {
            currentField = determineCurrentFieldFromEnrollment(enrollment, "professional_data");
            if (currentField == null) {
                return moveToNextStage(phone, enrollment);
            }
            saveToSession(phone, "currentField", currentField);
        }
    
        if (inputText == null || inputText.trim().isEmpty()) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
    
        switch (currentField) {
            case "designation":
                enrollment.setDesignation(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "occupation");
                return "CON Enter your Occupation:";
                
            case "occupation":
                enrollment.setOccupation(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "presentStation");
                return "CON Enter your Present Station:";
                
            case "presentStation":
                enrollment.setPresentStation(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "rank");
                return "CON Enter your Rank:";
                
            case "rank":
                enrollment.setRank(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "pfNumber");
                return "CON Enter your PF Number:";
                
            case "pfNumber":
                enrollment.setPfNumber(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "sdaName");
                return "CON Enter your SDA Name:";
                
            case "sdaName":
                enrollment.setSdaName(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
                
            default:
                String correctField = determineCurrentFieldFromEnrollment(enrollment, "professional_data");
                if (correctField != null) {
                    saveToSession(phone, "currentField", correctField);
                    return "CON Please enter " + getFieldDisplayName(correctField) + ":";
                }
                return "END Invalid field. Please start over.";
        }
    }
    private String handleSocialData(String phone, String inputText, FhisEnrollment enrollment) {
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
                    return "CON Invalid marital status.\nPlease enter SingleMarried, Divorced, or Widowed:";
                }
                enrollment.setMaritalStatus(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "email");
                return "CON Enter your Email Address:";
            case "email":
                if (!isValidEmail(inputText.trim())) {
                    return "CON Invalid email format. Please enter a valid email address:";
                }
                enrollment.setEmail(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "bloodGroup");
                return "CON Enter your Blood Group:";
            case "bloodGroup":
                if (!isValidBloodGroup(inputText.trim())) {
                    return "CON Invalid blood group. Please enter A+, A-, B+, B-, AB+, AB-, O+, or O-:";
                }
                enrollment.setBloodGroup(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "residentialAddress");
                return "CON Enter your Residential Address:";
            case "residentialAddress":
                enrollment.setResidentialAddress(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "occupation");
                return "CON Enter your Occupation:";
            case "occupation":
                enrollment.setOccupation(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
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

    private String handleCorporateData(String phone, String inputText, FhisEnrollment enrollment) {
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
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "telephoneNumber");
                return "CON Enter your Telephone Number:";
            case "telephoneNumber":
                if (!isValidPhoneNumber(inputText.trim())) {
                    return "CON Invalid phone number format. Please enter a valid phone number:";
                }
                enrollment.setTelephoneNumber(inputText.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "organizationName");
                return "CON Enter your Organization Name:";
            case "organizationName":
                enrollment.setOrganizationName(inputText.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
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

    private String HandleEnrollmentCompletion(String phone, String inputText, FhisEnrollment enrollment) {
        switch (inputText) {
            case "1":
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
                clearenrollmentSession(phone);
                return "END Enrollment submitted successfully! Thank you for enrolling in the FHIS program. Your reference number is: " + enrollment.getFhisNo();
            case "2":
                // Allow editing - reset to personal data step
                enrollment.setCurrentStep("personal_data");
                FhisEnrollmentRepository.save(enrollment);
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

    private String handleFormalSocialData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        
        // Extract the last input from the full text
        String lastChoice = "";
        if (inputText != null && !inputText.isEmpty()) {
            String[] parts = inputText.split("\\*");
            lastChoice = parts.length > 0 ? parts[parts.length - 1] : "";
        }
        
        System.out.println("Formal Social Data - Field: " + currentField + ", Last Input: '" + lastChoice + "'");
        
        if (currentField == null) {
            currentField = determineCurrentFieldFromEnrollment(enrollment, "social_data_formal");
            if (currentField == null) {
                return moveToNextStage(phone, enrollment);
            }
            saveToSession(phone, "currentField", currentField);
        }
    
        if (lastChoice == null || lastChoice.trim().isEmpty()) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
    
        switch (currentField) {
            case "maritalStatus":
                if (!isValidMaritalStatus(lastChoice.trim())) {
                    return "CON Invalid marital status.\nPlease enter Single, Married, Divorced, or Widowed:";
                }
                enrollment.setMaritalStatus(lastChoice.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "telephoneNumber");
                return "CON Enter your Telephone Number:";
                
            case "telephoneNumber":
                if (!isValidPhoneNumber(lastChoice.trim())) {
                    return "CON Invalid phone number format. Please enter a valid phone number:";
                }
                enrollment.setTelephoneNumber(lastChoice.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "residentialAddress");
                return "CON Enter your Residential Address:";
                
            case "residentialAddress":
                enrollment.setResidentialAddress(lastChoice.trim());
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "currentField", "email");
                return "CON Enter your Email Address:";
                
            case "email":
                if (!isValidEmail(lastChoice.trim())) {
                    return "CON Invalid email format. Please enter a valid email address:";
                }
                enrollment.setEmail(lastChoice.trim());
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
                return moveToNextStage(phone, enrollment);
                
            default:
                String correctField = determineCurrentFieldFromEnrollment(enrollment, "social_data_formal");
                if (correctField != null) {
                    saveToSession(phone, "currentField", correctField);
                    return "CON Please enter " + getFieldDisplayName(correctField) + ":";
                }
                return "END Invalid field. Please start over.";
        }
    }
    
    
    private String moveToNextStage(String phone, FhisEnrollment enrollment) {
        String enrollmentType = enrollment.getEnrollmentType();
        String currentStep = enrollment.getCurrentStep();
        
        System.out.println("Moving to next stage from: " + currentStep + " for " + enrollmentType);
        
        switch (currentStep) {
            case "personal_data":
                if ("Formal".equals(enrollmentType)) {
                    enrollment.setCurrentStep("professional_data");
                    saveToSession(phone, "currentField", "designation");
                } else {
                    enrollment.setCurrentStep("social_data");
                    saveToSession(phone, "currentField", "maritalStatus");
                }
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "waitingForContinue", true);
                return "CON ✓ Personal Information Complete!\n" +
                       "Progress: 25% of " + enrollmentType.toLowerCase() + " enrollment\n\n" + 
                       "Ready to continue with " + getNextSectionName(enrollmentType, currentStep) + "?\n" +
                       "1. Yes - Continue\n" +
                       "2. No - Review/Edit\n" +
                       "0. Exit Enrollment";
                       
            case "professional_data":
                enrollment.setCurrentStep("social_data_formal");
                saveToSession(phone, "currentField", "maritalStatus");
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "waitingForContinue", true);
                return "CON ✓ Professional Information Complete!\n" +
                       "Progress: 50% of formal enrollment\n\n" +
                       "Ready to continue with Social Information?\n" +
                       "1. Yes - Continue\n" +
                       "2. No - Review/Edit\n" +
                       "0. Exit Enrollment";
                       
            case "social_data":
                enrollment.setCurrentStep("corporate_data");
                saveToSession(phone, "currentField", "ninNumber");
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "waitingForContinue", true);
                return "CON ✓ Social Information Complete!\n" +
                       "Progress: 50% of informal enrollment\n\n" +
                       "Ready to continue with Corporate Information?\n" +
                       "1. Yes - Continue\n" +
                       "2. No - Review/Edit\n" +
                       "0. Exit Enrollment";
                       
            case "social_data_formal":
                enrollment.setCurrentStep("dependants_data");
                saveToSession(phone, "currentField", "numberOfChildren");
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "waitingForContinue", true);
                return "CON ✓ Social Information Complete!\n" +
                       "Progress: 75% of formal enrollment\n\n" +
                       "Ready to continue with Dependants Information?\n" +
                       "1. Yes - Continue\n" +
                       "2. No - Review/Edit\n" +
                       "0. Exit Enrollment";
                       
            case "dependants_data":
                enrollment.setCurrentStep("healthcare_provider_data");
                saveToSession(phone, "currentField", "hospitalSearch"); 
                FhisEnrollmentRepository.save(enrollment);
                saveToSession(phone, "waitingForContinue", false);
                return "CON ✓ Dependants Information Complete!\n" +
                       "Progress: 90% of " + enrollmentType.toLowerCase() + " enrollment\n\n" +
                       "Final Step: Healthcare Provider Selection\n" +
                       "Enter Hospital Name:";
                       
            case "corporate_data":
            case "healthcare_provider_data":
                
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
                return "CON 🎉 FHIS Enrollment Complete!\n\n" +
                       showEnrollmentSummary(enrollment) +
                       "\n\nConfirm your enrollment details:\n" +
                       "1. Yes - Submit Enrollment\n" +
                       "2. No - Review/Edit Details\n" +
                       "0. Cancel Enrollment";
                       
            default:
                System.err.println("Unknown step in moveToNextStage: " + currentStep);
                return "END Enrollment submitted successfully! Thank you for enrolling in the FHIS program.";
        }
    }
    // Helper method for section names
    private String getNextSectionName(String enrollmentType, String currentStep) {
        if ("Formal".equals(enrollmentType) && "personal_data".equals(currentStep)) {
            return "Professional Information";
        } else if ("Informal".equals(enrollmentType) && "personal_data".equals(currentStep)) {
            return "Social Information";
        }
        return "next section";
    }
        
    // private String handleHealthcareProviderData(String phone, String inputText, FhisEnrollment enrollment) {
    //     String currentField = (String) retrieveFromSession(phone, "currentField");

    //     String lastinput = "";
    //     if (inputText != null && !inputText.isEmpty()) {
    //         String[] parts = inputText.split("\\*");
    //         lastinput = parts.length > 0 ? parts[parts.length - 1] : "";
    //     }
        
    //     if (currentField == null) {
    //         currentField = "hospitalName";
    //         saveToSession(phone, "currentField", currentField);
    //     }
    //     if (lastinput == null || lastinput.trim().isEmpty()) {
    //         return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
    //     }
    
    //     if (inputText == null || inputText.trim().isEmpty()) {
    //         return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
    //     }
    
    //     switch (currentField) {
    //         case "hospitalName":
    //             enrollment.setHospitalName(lastinput.trim());
    //             FhisEnrollmentRepository.save(enrollment);
    //             saveToSession(phone, "currentField", "hospitalLocation");
    //             return "CON Enter Hospital Location:";
                
    //         case "hospitalLocation":
    //             enrollment.setHospitalLocation(lastinput.trim());
    //             FhisEnrollmentRepository.save(enrollment);
    //             saveToSession(phone, "currentField", "hospitalCodeNo");
    //             return "CON Enter Hospital Code Number:";
                
    //         case "hospitalCodeNo":

    //             enrollment.setHospitalCodeNo(lastinput.trim());
    //             enrollment.setUpdatedAt(LocalDateTime.now());
    //             FhisEnrollmentRepository.save(enrollment);
    //             return moveToNextStage(phone, enrollment);
                
    //         default:
    //             return "END Invalid field. Please start over.";
    //     }
    // }
    private String handleHealthcareProviderData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        String lastInput = "";
        
        if (inputText != null && !inputText.isEmpty()) {
            String[] parts = inputText.split("\\*");
            lastInput = parts.length > 0 ? parts[parts.length - 1] : "";
        }
        
        if (currentField == null) {
            currentField = "hospitalSearch";
            saveToSession(phone, "currentField", currentField);
        }
        
        System.out.println("Healthcare Provider Data - Field: " + currentField + ", Input: " + lastInput);
        
        switch (currentField) {
            case "hospitalSearch":
                if (lastInput == null || lastInput.trim().isEmpty()) {
                    return "CON Enter hospital name to search\n(or type 'list' to see all):";
                }
                
                if ("list".equalsIgnoreCase(lastInput.trim())) {
                    return showHospitalList(phone, 0);
                } else {
                    return searchHospitals(phone, lastInput.trim());
                }
                
            case "hospitalSelection":
                return handleHospitalSelection(phone, lastInput, enrollment);
            
            case "hospitalConfirmation":
                return handleHospitalConfirmation(phone, lastInput, enrollment);
                
            default:
                return "END Invalid field. Please start over.";
        }
    }
    private String handleHospitalConfirmation(String phone, String choice, FhisEnrollment enrollment) {
        if ("1".equals(choice)) {
            Long pendingHospitalId = getLongFromSession(phone, "pendingHospitalId");
            if (pendingHospitalId == null) {
                return "CON No hospital selected. Please try again.";
            }
    
            Optional<Hospital> hospitalOpt = hospitalRepository.findById(pendingHospitalId);
            if (!hospitalOpt.isPresent()) {
                return "CON Hospital not found. Please try again.";
            }
    
            // CRITICAL FIX: Now save the hospital to enrollment
            enrollment.setHospital(hospitalOpt.get());
            enrollment.setUpdatedAt(LocalDateTime.now());
            FhisEnrollmentRepository.save(enrollment);
    
            // Clear pending selection and session data
            saveToSession(phone, "pendingHospitalId", null);
            saveToSession(phone, "currentField", null);
    
            // Move to completion stage
            return moveToNextStage(phone, enrollment);
        } 
        else if ("0".equals(choice)) {
            // Go back to hospital selection
            saveToSession(phone, "pendingHospitalId", null);
            saveToSession(phone, "currentField", "hospitalSearch");
            return "CON Enter hospital name to search\n(or type 'list' to see all):";
        } 
        else {
            return "CON Invalid choice:\n1. Confirm Selection\n0. Back to Search";
        }
    }
    
    private String showHospitalList(String phone, int page) {
        try {
            Pageable pageable = PageRequest.of(page, 5);
            Page<Hospital> hospitals = hospitalRepository.findAll(pageable);
            
            if (hospitals.isEmpty()) {
                return "END No hospitals found.";
            }
            
            saveToSession(phone, "hospitalPage", page);
            saveToSession(phone, "totalHospitalPages", (int) hospitals.getTotalPages());
            saveToSession(phone, "currentField", "hospitalSelection");
            
            // Store hospital IDs for this page
            List<Long> hospitalIds = hospitals.getContent().stream()
                    .map(Hospital::getId)
                    .collect(Collectors.toList());
            saveToSession(phone, "hospital_ids", hospitalIds);
            
            StringBuilder menu = new StringBuilder("CON Select Hospital:\n");
            int count = 1;
            
            for (Hospital hospital : hospitals.getContent()) {
                menu.append(count).append(". ").append(hospital.getName())
                    .append(" (").append(hospital.getLocation()).append(")\n");
                count++;
            }
            
            if (page < hospitals.getTotalPages() - 1) {
                menu.append("6. Next Page\n");
            }
            if (page > 0) {
                menu.append("7. Previous Page\n");
            }
            menu.append("0. Back");
            
            return menu.toString();
            
        } catch (Exception e) {
            System.err.println("Error showing hospital list: " + e.getMessage());
            return "END Error loading hospitals. Please try again.";
        }
    }
    private String searchHospitals(String phone, String searchTerm) {
        try {
            Pageable pageable = PageRequest.of(0, 5);
            Page<Hospital> hospitals = hospitalRepository.searchActiveHospitals(searchTerm, pageable);
            
            if (hospitals.isEmpty()) {
                return "CON No hospitals found for: " + searchTerm + 
                       "\n\nTry different keywords or:\n" +
                       "1. View all hospitals\n" +
                       "0. Back";
            }
            
            saveToSession(phone, "hospitalSearchTerm", searchTerm);
            saveToSession(phone, "hospitalPage", 0);
            saveToSession(phone, "totalHospitalPages", (int) hospitals.getTotalPages());
            saveToSession(phone, "currentField", "hospitalSelection");
            
            List<Long> hospitalIds = hospitals.getContent().stream()
                    .map(Hospital::getId)
                    .collect(Collectors.toList());
            saveToSession(phone, "hospital_ids", hospitalIds);
            
            StringBuilder menu = new StringBuilder("CON Found " + hospitals.getTotalElements() + " hospitals:\n");
            int count = 1;
            
            for (Hospital hospital : hospitals.getContent()) {
                menu.append(count).append(". ").append(hospital.getName())
                    .append(" (").append(hospital.getLocation()).append(")\n");
                count++;
            }
            
            if (hospitals.getTotalPages() > 1) {
                menu.append("6. More results\n");
            }
            menu.append("0. Back");
            
            return menu.toString();
            
        } catch (Exception e) {
            System.err.println("Error searching hospitals: " + e.getMessage());
            return "END Error searching hospitals. Please try again.";
        }
    }
    private String handleHospitalSelection(String phone, String choice, FhisEnrollment enrollment) {
        try {
            List<Long> hospitalIds = getHospitalIdsFromSession(phone);
            
            if (hospitalIds == null || hospitalIds.isEmpty()) {
                return "END Session expired. Please start over.";
            }
            
            int selection = Integer.parseInt(choice);
            
            if (selection == 0) {
                return moveToNextStage(phone, enrollment);
            }
            
            if (selection >= 1 && selection <= Math.min(hospitalIds.size(), 5)) {
                Long selectedHospitalId = hospitalIds.get(selection - 1);
                Optional<Hospital> hospitalOpt = hospitalRepository.findById(selectedHospitalId);
                
                if (!hospitalOpt.isPresent()) {
                    return "CON Hospital not found. Please try again:";
                }
                
                Hospital selectedHospital = hospitalOpt.get();
                
                // CRITICAL FIX: Don't save immediately, just store as pending
                saveToSession(phone, "pendingHospitalId", selectedHospitalId);
                saveToSession(phone, "currentField", "hospitalConfirmation");
                
                return "CON Hospital Selected: " + selectedHospital.getName() + 
                       "\nLocation: " + selectedHospital.getLocation() + 
                       "\n\n1. Confirm Selection\n0. Choose Different Hospital";
            }
            
            // Handle pagination
            if (selection == 6) {
                return handleHospitalPagination(phone, "next");
            } else if (selection == 7) {
                return handleHospitalPagination(phone, "previous");
            }
            
            return "CON Invalid selection. Please choose a number between 1-" + 
                   Math.min(hospitalIds.size(), 5) + ":";
                   
        } catch (NumberFormatException e) {
            return "CON Invalid input. Please enter a number:";
        } catch (Exception e) {
            System.err.println("Error in hospital selection: " + e.getMessage());
            return "END Error processing selection. Please try again.";
        }
    }

    private List<Long> getHospitalIdsFromSession(String phone) {
        List<?> rawList = (List<?>) retrieveFromSession(phone, "hospital_ids");
        if (rawList == null) return null;
        
        return rawList.stream()
                .map(obj -> {
                    if (obj instanceof Integer) {
                        return ((Integer) obj).longValue();
                    } else if (obj instanceof Long) {
                        return (Long) obj;
                    } else {
                        throw new ClassCastException("Unexpected type in hospital_ids");
                    }
                })
                .collect(Collectors.toList());
    }
    private String handleHospitalPagination(String phone, String direction) {
        Integer currentPage = (Integer) retrieveFromSession(phone, "hospitalPage");
        Integer totalPages = (Integer) retrieveFromSession(phone, "totalHospitalPages");
        
        if (currentPage == null || totalPages == null) {
            return "END Session expired. Please start over.";
        }
        
        int newPage = currentPage;
        if ("next".equals(direction) && currentPage < totalPages - 1) {
            newPage = currentPage + 1;
        } else if ("previous".equals(direction) && currentPage > 0) {
            newPage = currentPage - 1;
        }
        
        return showHospitalList(phone, newPage);
    }
    
    private String handleDependantsData(String phone, String inputText, FhisEnrollment enrollment) {
        String currentField = (String) retrieveFromSession(phone, "currentField");
        
        // Extract the last input from the full text
        String lastChoice = "";
        if (inputText != null && !inputText.isEmpty()) {
            String[] parts = inputText.split("\\*");
            lastChoice = parts.length > 0 ? parts[parts.length - 1] : "";
        }
        
        System.out.println("Dependants Data - Field: " + currentField + ", Last Input: '" + lastChoice + "'");
        System.out.println("Full input text: '" + inputText + "'");
        
        // Check for continuation choice first
        Boolean waiting = (Boolean) retrieveFromSession(phone, "waitingForContinue");
        if (Boolean.TRUE.equals(waiting)) {
            saveToSession(phone, "waitingForContinue", false);
            if ("1".equals(lastChoice)) {
                // User chose to continue, now ask for number of children
                saveToSession(phone, "currentField", "numberOfChildren");
                return "CON Enter number of children/dependants (enter 0 if none):";
            } else if ("0".equals(lastChoice)) {
                clearenrollmentSession(phone);
                resetUserSession(phone);
                return HandleLevel1(phone, new String[0], true);
            } else {
                saveToSession(phone, "waitingForContinue", true);  // Keep waiting
                return "CON Invalid choice. Please select:\n1. Continue to dependants data\n0. Back";
            }
        }
        
        // Now handle the actual data entry
        if (currentField == null || !currentField.equals("numberOfChildren")) {
            saveToSession(phone, "currentField", "numberOfChildren");
            return "CON Enter number of children/dependants (enter 0 if none):";
        }
    
        if (lastChoice == null || lastChoice.trim().isEmpty()) {
            return "CON Please enter number of children/dependants (enter 0 if none):";
        }
    
        // Handle common text inputs that mean "no children"
        String cleanInput = lastChoice.trim().toLowerCase();
        if (cleanInput.equals("no") || cleanInput.equals("none") || 
            cleanInput.equals("zero") || cleanInput.equals("nil")) {
            enrollment.setNumberOfChildren(0);
            enrollment.setUpdatedAt(LocalDateTime.now());
            FhisEnrollmentRepository.save(enrollment);
            System.out.println("Set numberOfChildren to 0 based on text input: " + lastChoice);
            return moveToNextStage(phone, enrollment);
        }
        
        // Handle "yes" - ask for the actual number
        if (cleanInput.equals("yes")) {
            return "CON How many children do you have? Please enter a number (0-20):";
        }
    
        try {
            int children = Integer.parseInt(lastChoice.trim());
            if (children < 0 || children > 20) {
                return "CON Invalid number. Please enter number of children (0-20):";
            }
            enrollment.setNumberOfChildren(children);
            enrollment.setUpdatedAt(LocalDateTime.now());
            FhisEnrollmentRepository.save(enrollment);
            System.out.println("Successfully set numberOfChildren to: " + children);
            return moveToNextStage(phone, enrollment);
            
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format: '" + lastChoice + "'");
            return "CON Invalid input. Please enter a number (0-20) or 'no' if you have no children:";
        } catch (Exception e) {
            System.err.println("Error in handleDependantsData: " + e.getMessage());
            e.printStackTrace();
            return "CON Error processing input. Please enter number of children (0-20):";
        }
    }
    
    private String handleContinuationChoice(String phone, String choice, FhisEnrollment enrollment) {
        Boolean waiting = (Boolean) retrieveFromSession(phone, "waitingForContinue");
        if (Boolean.TRUE.equals(waiting)) {
            saveToSession(phone, "waitingForContinue", false);
            
            switch (choice) {
                case "1": // Yes - Continue
                    String currentField = (String) retrieveFromSession(phone, "currentField");
                    return promptForNextField(currentField, enrollment);
                    
                case "2": // No - Review/Edit
                    return handleReviewEditOption(phone, enrollment);
                    
                case "0": // Exit
                    clearenrollmentSession(phone);
                    resetUserSession(phone);
                    return "CON Enrollment cancelled.\n\n" +
                           "Your progress has been saved. You can continue later.\n" +
                           "1. Return to Main Menu\n" +
                           "0. Exit";
                           
                default:
                    // Invalid choice - show options again
                    saveToSession(phone, "waitingForContinue", true);
                    String currentStep = enrollment.getCurrentStep();
                    String enrollmentType = enrollment.getEnrollmentType();
                    
                    return "CON Invalid choice. Please select:\n\n" +
                           "Progress: " + getProgressPercentage(currentStep) + "% of " + 
                           enrollmentType.toLowerCase() + " enrollment\n\n" +
                           "1. Yes - Continue Enrollment\n" +
                           "2. No - Review/Edit Details\n" +
                           "0. Exit Enrollment";
            }
        }
        return null;
    }
    private String handleReviewEditOption(String phone, FhisEnrollment enrollment) {
        // For now, just continue - you can enhance this later to show review options
        String currentField = (String) retrieveFromSession(phone, "currentField");
        return promptForNextField(currentField, enrollment);
    }
    private String promptForNextField(String currentField, FhisEnrollment enrollment) {
        String enrollmentType = enrollment.getEnrollmentType();
        String currentStep = enrollment.getCurrentStep();
        
        switch (currentStep) {
            case "professional_data":
                switch (currentField) {
                    case "designation":
                        return "CON PROFESSIONAL INFORMATION\n\nEnter your Designation:";
                    default:
                        return "CON Enter " + getFieldDisplayName(currentField) + ":";
                }
                
            case "social_data":
            case "social_data_formal":
                switch (currentField) {
                    case "maritalStatus":
                        return "CON SOCIAL INFORMATION\n\nEnter your Marital Status\n(Single/Married/Divorced/Widowed):";
                    default:
                        return "CON Enter " + getFieldDisplayName(currentField) + ":";
                }
                
            case "corporate_data":
                switch (currentField) {
                    case "ninNumber":
                        return "CON CORPORATE INFORMATION\n\nEnter your NIN Number (11 digits):";
                    default:
                        return "CON Enter " + getFieldDisplayName(currentField) + ":";
                }
                
            case "dependants_data":
                return "CON DEPENDANTS INFORMATION\n\nEnter number of children/dependants\n(Enter 0 if none):";
                
            default:
                return "CON Enter " + getFieldDisplayName(currentField) + ":";
        }
    }


    private String showEnrollmentSummary(FhisEnrollment enrollment) {
        StringBuilder summary = new StringBuilder();
        summary.append("REVIEW:\n");
        
        if (enrollment.getTitle() != null) {
            summary.append("Name: ").append(enrollment.getTitle()).append(" ");
        } else {
            summary.append("Name: ");
        }
        summary.append(enrollment.getFirstName()).append(" ").append(enrollment.getSurname()).append("\n");
        summary.append("FHIS: ").append(enrollment.getFhisNo()).append("\n");
        summary.append("Email: ").append(enrollment.getEmail()).append("\n");
        summary.append("Phone: ").append(enrollment.getTelephoneNumber());
        
        // Add hospital info if available
        if (enrollment.getHospital() != null) {
            summary.append("\nHospital: ").append(enrollment.getHospital().getName());
            if (enrollment.getHospital().getLocation() != null) {
                summary.append(" (").append(enrollment.getHospital().getLocation()).append(")");
            }
        }
        
        return summary.toString();
    }

    
    private FhisEnrollment GetorCreateFhisEnrollment(String phoneNumber) {
        try {
            System.out.println("Getting enrollment for phone: " + phoneNumber);
            Optional<FhisEnrollment> existingEnrollment = FhisEnrollmentRepository.findByPhoneNumber(phoneNumber);
            
            if (existingEnrollment.isPresent()) {
                System.out.println("Found existing enrollment for phone: " + phoneNumber);
                FhisEnrollment enrollment = existingEnrollment.get();
                System.out.println("Enrollment details: " + enrollment.toString());
                return enrollment;
            }
            
            System.out.println("No existing enrollment found for phone: " + phoneNumber);
            return null;
            
        } catch (Exception e) {
            System.err.println("Error getting FHIS enrollment: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    private void clearSessionKeys(String phoneNumber, String[] keys, String context) {
        if (keys == null) {
            // Use ALL_KEYS when keys parameter is null
            keys = SessionKeys.ALL_KEYS;
        }
        
        try {
            for (String keyType : keys) {
                String fullKey = phoneNumber + ":" + keyType;
                if (redisTemplate.hasKey(fullKey)) {
                    redisTemplate.delete(fullKey);
                }
            }
            System.out.println("Cleared " + keys.length + " session keys for: " + phoneNumber);
        } catch (Exception e) {
            System.err.println("Error clearing session keys: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearenrollmentSession(String phoneNumber) {
        clearSessionKeys(phoneNumber, null, phoneNumber);
    }
    private void clearNavigationSession(String phoneNumber) {
        clearSessionKeys(phoneNumber, SessionKeys.NAVIGATION_KEYS, "navigation");
    }
    private void handleFlowTransition(String phoneNumber, String[] parts) {
        if (parts.length >= 1) {
            String newFlow = parts[0].toLowerCase();
            String currentFlow = (String) retrieveFromSession(phoneNumber, "currentFlow");
            
            // If there's a flow change or no current flow, reset navigation session
            if (currentFlow == null || 
                (!newFlow.equals("fhis") && !newFlow.equals("nnpc")) || 
                (currentFlow.equals("fhis_enrollment") && !newFlow.equals("fhis"))) {
                
                System.out.println("Flow transition detected - clearing navigation session");
                clearNavigationSession(phoneNumber);
                
                // Don't clear enrollment session if user is returning to fhis after being in enrollment
                if (!currentFlow.equals("fhis_enrollment") || !newFlow.equals("fhis")) {
                    clearenrollmentSession(phoneNumber);
                }
            }
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
    if (dateStr == null || dateStr.trim().isEmpty()) {
        return false;
    }
    
    try {
        String cleanDate = dateStr.trim().replace("/", "-").replace(".", "-");
        LocalDate date;
        
        if (cleanDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            date = LocalDate.parse(cleanDate);
        } else if (cleanDate.matches("\\d{1,2}-\\d{1,2}-\\d{4}")) {
            String[] parts = cleanDate.split("-");
            String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
            String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
            date = LocalDate.parse(parts[2] + "-" + month + "-" + day);
        } else {
            return false;
        }
        
        LocalDate now = LocalDate.now();
        return !date.isAfter(now) && !date.isBefore(now.minusYears(100));
    } catch (Exception e) {
        return false; // Simplified error handling
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

    // 2. FIX DUPLICATE REQUEST DETECTION - Replace isDuplicateRequest:
    private boolean isDuplicateRequest(String requestId, String inputText) {
        try {
            String requestKey = "request:" + requestId;
            String lastProcessedKey = "lastProcessed:" + requestId.split(":")[0]; // phoneNumber part
            
            // Check if this exact request was recently processed
            Boolean exists = redisTemplate.hasKey(requestKey);
            if (Boolean.TRUE.equals(exists)) {
                return true; // Duplicate detected
            }
            
            // Mark this request as being processed (expires in 3 seconds)
            redisTemplate.opsForValue().set(requestKey, "processing", 3, TimeUnit.SECONDS);
            
            // Also track the last processed input for this user
            redisTemplate.opsForValue().set(lastProcessedKey, inputText, 10, TimeUnit.SECONDS);
            
            return false;
        } catch (Exception e) {
            System.err.println("Error in duplicate detection: " + e.getMessage());
            return false; // Don't block on errors
        }
    }
    // private boolean acquireUserLock(String phoneNumber) {
    //     try {
    //         String lockKey = phoneNumber + ":processing_lock";
    //         // ISSUE: 5 seconds is too long for USSD responses
    //         // FIX: Reduce to 2 seconds max
    //         Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 2, TimeUnit.SECONDS);
            
    //         if (!Boolean.TRUE.equals(lockAcquired)) {
    //             // OPTIMIZATION: Log and return immediately instead of waiting
    //             System.out.println("Lock not acquired for: " + phoneNumber + " - another request in progress");
    //         }
    //         return Boolean.TRUE.equals(lockAcquired);
    //     } catch (Exception e) {
    //         System.err.println("Error acquiring user lock: " + e.getMessage());
    //         // CRITICAL: Return true to avoid blocking on Redis errors
    //         return true;
    //     }

    // }
    // private void releaseUserLock(String phoneNumber) {
    //     try {
    //         String lockKey = phoneNumber + ":processing_lock";
    //         redisTemplate.delete(lockKey);
    //     } catch (Exception e) {
    //         System.err.println("Error releasing user lock: " + e.getMessage());
    //     }
    // }

    private String getFieldDisplayName(String fieldName) {
        switch (fieldName) {
            case "fhisNo": return "FHIS Number";
            case "title": return "Title";
            case "surname": return "Surname";
            case "firstName": return "First Name";
            case "middleName": return "Middle Name";
            case "dateOfBirth": return "Date of Birth";
            case "sex": return "Sex";
            case "maritalStatus": return "Marital Status";
            case "email": return "Email Address";
            case "bloodGroup": return "Blood Group";
            case "residentialAddress": return "Residential Address";
            case "occupation": return "Occupation";
            case "ninNumber": return "NIN Number";
            case "telephoneNumber": return "Telephone Number";
            case "organizationName": return "Organization Name";
            // Professional fields for Formal enrollment
            case "designation": return "Designation";
            case "presentStation": return "Present Station";
            case "rank": return "Rank";
            case "pfNumber": return "PF Number";
            case "sdaName": return "SDA Name";
            default: return "the required information";
        }
    }
 
}