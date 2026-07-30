package com.example.tems.Tems.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.tems.Tems.model.CacRegistration;
import com.example.tems.Tems.model.CbmRegistration;
import com.example.tems.Tems.model.FfsRegistration;
import com.example.tems.Tems.model.FhisEnrollment;
import com.example.tems.Tems.model.Hospital;
import com.example.tems.Tems.model.Organization;
import com.example.tems.Tems.repository.CacRegistrationRepository;
import com.example.tems.Tems.repository.CbmRegistrationRepository;
import com.example.tems.Tems.repository.FfsRegistrationRepository;
import com.example.tems.Tems.repository.FhisEnrollmentRepository;
import com.example.tems.Tems.repository.HospitalRepository;
import com.example.tems.Tems.repository.OrganizationRepository;
import com.example.tems.Tems.service.AggregatorService;
import com.example.tems.Tems.service.SubscriptionService;

@RestController
public class ussdcontroller {

    // FIXED: Renamed variable to follow camelCase convention
    private OrganizationRepository organizationRepository;
    // private AggregatorService aggregatorService;
    // private SubscriptionService subscriptionService;
    private FhisEnrollmentRepository FhisEnrollmentRepository;
    private final HospitalRepository hospitalRepository;
    private final FfsRegistrationRepository ffsRegistrationRepository;
    private final CacRegistrationRepository cacRegistrationRepository;
    private final CbmRegistrationRepository cbmRegistrationRepository;

   

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // all sessions 
    private static final Set<String> CBM_SPECIAL_NUMBERS = Set.of("07072603735");
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
            "menuShown", "lastInteraction", "awaitingSearchTerm", // Added for menu tracking
            "ffsRegFlow", "ffsRegType", "ffsRegField",
            "ffsRegName", "ffsRegAddress", "ffsRegState",
            "ffsRegOccupation", "ffsRegOrg","cacRegFlow", "cacRegType", "cacRegField",
            "cacRegName", "cacRegEmail", "cacRegState", "cacRegOccupation",
            "cacVerifyType","cbmFlow", "cbmField", "cbmFirstName", "cbmLastName", "cbmEmail", 
            "cbmVin", "cbmGender", "cbmOrgName", "cbmSupportType", "cbmSpread", "cbmReferral"
        };

        // FFS registration keys can be added here if needed
        public static final String[] FFS_REGISTRATION_KEYS = {
            "ffsRegFlow", "ffsRegType", "ffsRegField", 
            "ffsRegName", "ffsRegAddress", "ffsRegState", 
            "ffsRegOccupation", "ffsRegOrg"
        };

    }

    // check if is cbmspecial number
    private boolean isCbmSpecialNumber(String phoneNumber) {
        boolean match = phoneNumber != null && CBM_SPECIAL_NUMBERS.contains(phoneNumber);
        System.out.println("🔍 CBM check - incoming: '" + phoneNumber + "', set: " + CBM_SPECIAL_NUMBERS + ", match: " + match);
        return match;
    }
    // show cbm menu
   private String showCBMMenu() {
        return "CON CITY BOY MOVEMENT\n\n" +
            "1. Join\n" +
            "2. About\n" +
            "0. Exit";
    }

    // FIXED: Renamed constructor parameter and assignment
    @Autowired
    public ussdcontroller(OrganizationRepository organizationRepository, AggregatorService aggregatorService, SubscriptionService subscriptionService, FhisEnrollmentRepository FhisEnrollmentRepository, HospitalRepository hospitalRepository, FfsRegistrationRepository ffsRegistrationRepository, CacRegistrationRepository cacRegistrationRepository, CbmRegistrationRepository cbmRegistrationRepository) {
        this.organizationRepository = organizationRepository;
        this.cbmRegistrationRepository = cbmRegistrationRepository;
        // this.aggregatorService = aggregatorService;
        // this.subscriptionService = subscriptionService;
        this.FhisEnrollmentRepository = FhisEnrollmentRepository;
        this.hospitalRepository = hospitalRepository;
        this.ffsRegistrationRepository = ffsRegistrationRepository;
        this.cacRegistrationRepository = cacRegistrationRepository;
    }
    
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
    private String handleCBMJoinMovement(String phone, String input) {
        String currentField = (String) retrieveFromSession(phone, "cbmField");
        if (currentField == null) {
            currentField = "firstName";
            saveToSession(phone, "cbmField", currentField);
        }

        if (input == null || input.trim().isEmpty()) {
            return "CON Field cannot be empty. Please enter " + getCbmFieldDisplayName(currentField) + ":";
        }
        String value = input.trim();

        switch (currentField) {
            case "firstName":
                if (!isValidName(value)) {
                    return "CON Invalid name. Please enter First Name:";
                }
                saveToSession(phone, "cbmFirstName", value);
                saveToSession(phone, "cbmField", "lastName");
                return "CON Enter Last Name:";

            case "lastName":
                if (!isValidName(value)) {
                    return "CON Invalid name. Please enter Last Name:";
                }
                saveToSession(phone, "cbmLastName", value);
                saveToSession(phone, "cbmField", "email");
                return "CON Enter Email Address:";

            case "email":
                if (!isValidEmail(value)) {
                    return "CON Invalid email. Please enter a valid Email Address:";
                }
                saveToSession(phone, "cbmEmail", value);
                saveToSession(phone, "cbmField", "gender");
                return "CON Select Gender:\n1. Male\n2. Female";

            case "gender":
                String gender;
                switch (value) {
                    case "1": gender = "MALE"; break;
                    case "2": gender = "FEMALE"; break;
                    default: return "CON Invalid choice.\n\nSelect Gender:\n1. Male\n2. Female";
                }
                saveToSession(phone, "cbmGender", gender);
                saveToSession(phone, "cbmField", "vin");
                return "CON Enter your Membership/Verification Number\n(Enter 0 if none):";

            case "vin":
                saveToSession(phone, "cbmVin", "0".equals(value) ? null : value);
                saveToSession(phone, "cbmField", "orgName");
                return "CON Enter your Organization/Group Name\n(Enter 0 to skip):";

            case "orgName":
                saveToSession(phone, "cbmOrgName", "0".equals(value) ? null : value);
                saveToSession(phone, "cbmField", "supportType");
                return "CON Select Support Type:\n1. Volunteer\n2. Donor\n3. Partner\n4. General Member";

            case "supportType":
                String support;
                switch (value) {
                    case "1": support = "VOLUNTEER"; break;
                    case "2": support = "DONOR"; break;
                    case "3": support = "PARTNER"; break;
                    case "4": support = "GENERAL_MEMBER"; break;
                    default: return "CON Invalid choice.\n\nSelect Support Type:\n1. Volunteer\n2. Donor\n3. Partner\n4. General Member";
                }
                saveToSession(phone, "cbmSupportType", support);
                saveToSession(phone, "cbmField", "spread");
                return "CON Enter your State/Location:";

            case "spread":
                saveToSession(phone, "cbmSpread", value);
                saveToSession(phone, "cbmField", "referral");
                return "CON Enter Referral Name/Code\n(Enter 0 if none):";

            case "referral":
                saveToSession(phone, "cbmReferral", "0".equals(value) ? null : value);
                return saveCBMRegistration(phone);

            default:
                return "END Invalid form state.";
        }
    }
    private String saveCBMRegistration(String phone) {
        try {
            CbmRegistration reg = new CbmRegistration();
            reg.setPhoneNumber(phone);
            reg.setReferenceId(generateCBMReferenceId());
            reg.setFirstName((String) retrieveFromSession(phone, "cbmFirstName"));
            reg.setLastName((String) retrieveFromSession(phone, "cbmLastName"));
            reg.setEmail((String) retrieveFromSession(phone, "cbmEmail"));
            reg.setVin((String) retrieveFromSession(phone, "cbmVin"));
            reg.setGender((String) retrieveFromSession(phone, "cbmGender"));
            reg.setOrgName((String) retrieveFromSession(phone, "cbmOrgName"));
            reg.setSupportType((String) retrieveFromSession(phone, "cbmSupportType"));
            reg.setSpread((String) retrieveFromSession(phone, "cbmSpread"));
            reg.setReferral((String) retrieveFromSession(phone, "cbmReferral"));
            reg.setCreatedAt(LocalDateTime.now());

            cbmRegistrationRepository.save(reg);
            clearCBMJoinSession(phone);

            return "END Welcome to City Boy Movement!\n\nRef: " + reg.getReferenceId();
        } catch (Exception e) {
            System.err.println("Error saving CBM registration: " + e.getMessage());
            return "END Error saving registration. Please try again.";
        }
    }
    private String generateCBMReferenceId() {
        int random = (int) (Math.random() * 9000) + 1000;
        return "CBM-REG-" + random;
    }
    private void clearCBMJoinSession(String phone) {
        saveToSession(phone, "cbmFlow", null);
        saveToSession(phone, "cbmField", null);
        saveToSession(phone, "cbmFirstName", null);
        saveToSession(phone, "cbmLastName", null);
        saveToSession(phone, "cbmEmail", null);
        saveToSession(phone, "cbmVin", null);
        saveToSession(phone, "cbmGender", null);
        saveToSession(phone, "cbmOrgName", null);
        saveToSession(phone, "cbmSupportType", null);
        saveToSession(phone, "cbmSpread", null);
        saveToSession(phone, "cbmReferral", null);
    }
    private String getCbmFieldDisplayName(String field) {
        switch (field) {
            case "firstName": return "First Name";
            case "lastName": return "Last Name";
            case "email": return "Email Address";
            case "gender": return "Gender";
            case "vin": return "Membership Number";
            case "orgName": return "Organization Name";
            case "supportType": return "Support Type";
            case "spread": return "State/Location";
            case "referral": return "Referral";
            default: return "the required information";
        }
    }
    private String handleCBMFlow(String phone, String input) {
        String cbmFlow = (String) retrieveFromSession(phone, "cbmFlow");

        if (cbmFlow == null) {
            saveToSession(phone, "cbmFlow", "main_menu");
            return showCBMMenu();
        }

        switch (cbmFlow) {
            case "main_menu":
                return handleCBMMainMenu(phone, input);
            case "join_movement":
                return handleCBMJoinMovement(phone, input);
            default:
                saveToSession(phone, "cbmFlow", null);
                return showCBMMenu();
        }
    }

    private String handleCBMMainMenu(String phone, String choice) {
        switch (choice) {
            case "1":
                saveToSession(phone, "cbmFlow", "join_movement");
                saveToSession(phone, "cbmField", "firstName");
                return "CON JOIN CITY BOY MOVEMENT\n\nEnter First Name:";
                
            case "2":
                saveToSession(phone, "cbmFlow", "about");
                return "CON ABOUT CITY BOY\n\n" +
                    "1. About CBM\n" +
                    "2. Grand Patron\n" +
                    "3. Director General\n" +
                    "4. State Directors\n" +
                    "0. Back";
                    
            case "3":
                saveToSession(phone, "cbmFlow", "support_reg");
                return "CON SUPPORT GROUP REGISTRATION\n\nComing soon.\n\n0. Back";
                
            case "4":
                saveToSession(phone, "cbmFlow", "achievements");
                return "CON ACHIEVEMENTS\n\n" +
                    "1. Presidential Achievements\n" +
                    "2. Ministerial Achievements\n" +
                    "0. Back";
                    
            case "5":
                saveToSession(phone, "cbmFlow", "news");
                return "CON NEWS\n\nComing soon.\n\n0. Back";
                
            case "6":
                return "END CONTACTS\n\n" +
                    "162 Aminu Kano Crescent,\n" +
                    "Abuja-FCT, Nigeria\n" +
                    "080 3714 3337\n" +
                    "081 8000 8077\n" +
                    "info@cbmnigeria.org";
                    
            case "7":
                return "END NEWS AND UPDATE\n\nCheck back later for updates.\n\nwww.cbm.com";
                
            case "0":
                saveToSession(phone, "cbmFlow", null);
                resetUserSession(phone);
                return "END Thank you for using City Boy Movement.";
                
            default:
                return showCBMMenu();
        }
    }
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
        // 🔥 FIX: Move FFS Registration flow check HERE, before selectedOrgId
        String ffsRegFlow = (String) retrieveFromSession(normalizedPhoneNumber, "ffsRegFlow");
        if (ffsRegFlow != null) {
            System.out.println("Routing to FFS Registration flow: " + ffsRegFlow);
            return handleRegistrationFlow(normalizedPhoneNumber, inputedText);
        }
        // move CAC Registration flow check here
        String cacRegFlow = (String) retrieveFromSession(normalizedPhoneNumber, "cacRegFlow");
        if (cacRegFlow != null) {
            System.out.println("Routing to CAC Registration flow: " + cacRegFlow);
            return handleCACRegistrationFlow(normalizedPhoneNumber, inputedText);
        }
        // cbm movement flow check
        if (isCbmSpecialNumber(normalizedPhoneNumber) || retrieveFromSession(normalizedPhoneNumber, "cbmFlow") != null) {
            System.out.println("Routing to CBM Movement flow");
            return handleCBMFlow(normalizedPhoneNumber, inputedText);
        }
        // handle cbm flow 
        
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
            }else if ("register_verify".equals(currentSubMenu) || "verify_menu".equals(currentSubMenu) || "verify_form".equals(currentSubMenu) || "request_service".equals(currentSubMenu) || "report_incident".equals(currentSubMenu) || "guidelines".equals(currentSubMenu) || "faqs".equals(currentSubMenu) || "alerts".equals(currentSubMenu) || "more_info_ffs".equals(currentSubMenu) || "account_profile".equals(currentSubMenu) || "cac_register_verify".equals(currentSubMenu) || "cac_verify_menu".equals(currentSubMenu) || "cac_verify_form".equals(currentSubMenu)) {
                System.out.println("User is in FFS submenu");
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
    
    private String HandleLevel1(String phone, String[] parts, boolean isInitial) {
        if (isInitial) {
            // cbm special number check
            if (isCbmSpecialNumber(phone)) {
                saveToSession(phone, "menuShown", "true");
                saveToSession(phone, "lastInteraction", System.currentTimeMillis());
                saveToSession(phone, "cbmFlow", "main_menu");
                return showCBMMenu();
            }
            // Check if user is in the middle of FHIS enrollment
            String currentFlow = (String) retrieveFromSession(phone, "currentFlow");
            if (currentFlow != null && (currentFlow.equals("fhis_enrollment") || currentFlow.equals("formal_fhis_enrollment"))) {
                return "CON You have an ongoing enrollment.\n1. Continue\n2. Start Fresh\n0. Exit";
            }
            String ffsRegFlow = (String) retrieveFromSession(phone, "ffsRegFlow");
            if (ffsRegFlow != null) {
                return "CON You have an ongoing FFS registration.\n1. Continue\n2. Start Fresh\n0. Exit";
            }
            
            // CRITICAL FIX: Save a session marker to indicate we've shown the menu
            saveToSession(phone, "menuShown", "true");
            saveToSession(phone, "lastInteraction", System.currentTimeMillis());
        }

        // Welcome menu - this will be shown when input is "7447"
        return "CON Welcome to TEMS SERVICE\n\n" +
            "1. Search Organizations\n" +
            "2. About TEMS\n" +
            "0. Exit";
    }

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
        boolean isFFS = orgofchoice.getName().toUpperCase().contains("FIRE") 
                    || orgofchoice.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") 
                    || orgofchoice.getName().toUpperCase().contains("FFS");

        boolean isCAC = orgofchoice.getName().toUpperCase().contains("CAC") 
                    || orgofchoice.getName().toUpperCase().contains("CORPORATE AFFAIRS");
        
        StringBuilder menu = new StringBuilder("CON " + orgofchoice.getName() + "\n" +
                "1. Register/Verify\n" +
                "2. Request\n" +
                "3. Report\n" +
                "4. Guidelines\n" +
                "5. FAQs & Links\n" +
                "6. Call Lines\n" +
                "7. Tips & Updates\n" +
                "8. About\n" +
                "9. More Info\n");
        
        if (isFFS) {
            menu.append("10. Account / Profile\n");
        }
        
        if(isCAC) {
            menu.append("11. Contact CAC\n");
            menu.append("12. About CAC\n");
            menu.append("13. Account/Profile\n");
        }
        menu.append("0. Main Menu");
        return menu.toString();
    }

    private String HandleLevel3(String choice, String phone, String[] parts) {
        String currentSubMenu = (String) retrieveFromSession(phone, "currentSubMenu");
        if ("register_verify".equals(currentSubMenu) || "verify_menu".equals(currentSubMenu) || "verify_form".equals(currentSubMenu)) {
            return handleLevel4(choice, phone, parts);
        }
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

            case "1": // Register/Verify
                if (org.getName().toUpperCase().contains("FHIS") || org.getName().toUpperCase().contains("FCT HEALTH") || org.getName().toUpperCase().contains("FCT HEALTH INSURANCE")) {
                    return showMoreOptions(org, phone);
                } else if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    saveToSession(phone, "currentSubMenu", "register_verify");
                    return "CON Register / Verify\n\n" +
                        "1. Register\n" +
                        "2. Verify\n" +
                        "0. Back";
                    } else if (org.getName().toUpperCase().contains("CAC") || org.getName().toUpperCase().contains("CORPORATE AFFAIRS")) {
                        saveToSession(phone, "currentSubMenu", "cac_register_verify");
                        return "CON Register / Verify\n\n" +
                            "1. Register\n" +
                            "2. Verify\n" +
                            "0. Back";
                    } else {
                        return "CON Register/Verify:\nThis service is coming soon.\n\n0. Back";    
                }
                   
                
            case "2": // Request
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                saveToSession(phone, "currentSubMenu", "request_service");
                return "CON Request Service\n\n" +
                    "1. Fire Safety Inspection\n" +
                    "2. Facility Assessment\n" +
                    "3. Fire Safety Training\n" +
                    "4. Public Awareness Visit\n" +
                    "5. Fire Truck Demonstration\n" +
                    "6. Emergency Preparedness Consultation\n" +
                    "7. Callback Request\n" +
                    "0. Back";
            } else {
                return "CON Request:\nThis service is coming soon.\n\n0. Back";
            }
                
            case "3": // Report
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    saveToSession(phone, "currentSubMenu", "report_incident");
                    return "CON Report Incident\n\n" +
                        "1. Fire Outbreak\n" +
                        "2. Gas Explosion\n" +
                        "3. Electrical Fire\n" +
                        "4. Bush Fire\n" +
                        "5. Building Collapse\n" +
                        "6. Hazardous Materials Incident\n" +
                        "7. False Alarm Report\n" +
                        "0. Back";
                } else {
                    return "CON Report:\nThis service is coming soon.\n\n0. Back";
                }
                
            case "4": // Guidelines & Procedures
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    saveToSession(phone, "currentSubMenu", "guidelines");
                    return "CON Fire Safety Guidelines\n\n" +
                        "1. Home Fire Safety\n" +
                        "2. Office Fire Safety\n" +
                        "3. School Fire Safety\n" +
                        "4. Market Fire Prevention\n" +
                        "5. Fuel and Gas Safety\n" +
                        "6. Emergency Evacuation Procedures\n" +
                        "0. Back";
                } else {
                    return "CON Guidelines & Procedures:\nNot available at this time.\n\n0. Back";
                }
                
            case "5": // FAQs/Links
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    saveToSession(phone, "currentSubMenu", "faqs");
                    return "CON FAQs\n\n" +
                        "1. How to obtain a Fire Certificate\n" +
                        "2. How to request inspection\n" +
                        "3. Fire safety requirements\n" +
                        "4. Emergency response procedures\n" +
                        "5. Contact information\n" +
                        "0. Back";
                } else {
                    return "CON FAQs/Links:\nNot available at this time.\n\n0. Back";
                }
                
            case "6": // Call Lines
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    return "END Emergency Call Lines\n\n" +
                        "National Emergency: 112\n" +
                        "Federal Fire Service: 0703-590-4570\n" +
                        "Rescue Support: 0803-200-1234\n\n" +
                        "State Fire Service:\n" +
                        "Contact your state command.\n\n" +
                        "Dial 112 for all emergencies.";
                } else {
                    return "CON Call Lines:\n" + 
                            (org.getContactTelephone() != null ? org.getContactTelephone() : "Not available") +
                            "\n\n0. Back";
                }
                
            case "7": // Tips/Updates
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    saveToSession(phone, "currentSubMenu", "alerts");
                    return "CON Alerts & Updates\n\n" +
                        "1. Fire Safety Tips\n" +
                        "2. Seasonal Fire Warnings\n" +
                        "3. Harmattan Fire Advisories\n" +
                        "4. Flood and Disaster Alerts\n" +
                        "5. Public Safety Announcements\n" +
                        "6. Emergency Preparedness Campaigns\n" +
                        "0. Back";
                } else {
                    return "CON Tips/Updates:\nNo updates at this time.\n\n0. Back";
                }
                
            case "8": // About Organization
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    return "END About Federal Fire Service\n\n" +
                        "Mandate:\n" +
                        "To prevent and fight fires, " +
                        "protect life and property.\n\n" +
                        "Responsibilities:\n" +
                        "- Fire prevention education\n" +
                        "- Emergency response\n" +
                        "- Rescue operations\n" +
                        "- Fire safety inspections\n\n" +
                        "Contact: 0703-590-4570";
                } else {
                    return "CON About " + org.getName() + ":\n" +
                            (org.getDescription() != null ? org.getDescription() : "Not available") +
                            "\n\nAddress: " + (org.getContactAddress() != null ? org.getContactAddress() : "Not available") +
                            "\n\n0. Back";
                }
                
            case "9": // More Info
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    saveToSession(phone, "currentSubMenu", "more_info_ffs");
                    return "CON More Information\n\n" +
                        "1. Service Locations\n" +
                        "2. State Commands\n" +
                        "3. Office Addresses\n" +
                        "4. Approved Fire Safety Consultants\n" +
                        "5. Office Hours\n" +
                        "0. Back";
                } else {
                    return showMoreOptions(org, phone);
                }
            
            case "10": // Account / Profile
                if (org.getName().toUpperCase().contains("FIRE") || org.getName().toUpperCase().contains("FEDERAL FIRE SERVICE") || org.getName().toUpperCase().contains("FFS")) {
                    saveToSession(phone, "currentSubMenu", "account_profile");
                    return "CON Account / Profile\n\n" +
                        "1. Update Profile\n" +
                        "2. Change PIN\n" +
                        "3. Notification Preferences\n" +
                        "4. View Recent Requests\n" +
                        "5. Account Recovery\n" +
                        "0. Back";
                } else {
                    return "CON Account:\nNot available for this organization.\n\n0. Back";
                }
                
            case "0": // Main Menu
                clearNavigationSession(phone);
                return HandleLevel1(phone, new String[0], false);
                
            default:
                return showorgmenu(org); // Re-show menu on invalid input
        }
    }
    private String handleCACRegistrationFlow(String phone, String choice) {
        String regFlow = (String) retrieveFromSession(phone, "cacRegFlow");
        if ("register_menu".equals(regFlow)) {
            return handleCACRegistrationTypeSelection(phone, choice);
        }
        if ("register_form".equals(regFlow)) {
            return handleCACRegistrationForm(phone, choice);
        }
        return "END Invalid CAC registration state.";
    }

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
        
        // FFS Register/Verify submenu
        if ("register_verify".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSRegisterVerifyMenu(choice, phone);
        }
        if ("request_service".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSRequestService(choice, phone);
        }
        if ("report_incident".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSReportIncident(choice, phone);
        }

        // FFS Verification flow
        if ("verify_menu".equals(currentSubMenu) || "verify_form".equals(currentSubMenu)) {
            return handleVerificationFlow(phone, choice);
        }
        if ("guidelines".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSGuidelines(choice, phone);
        }
        if ("faqs".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSFAQs(choice, phone);
        }
        if ("alerts".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSAlerts(choice, phone);
        }
        if ("more_info_ffs".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSMoreInfo(choice, phone);
        }
        if ("account_profile".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleFFSAccountProfile(choice, phone);
        }
        // CAC Register/Verify submenu
        if ("cac_register_verify".equals(currentSubMenu)) {
            saveToSession(phone, "currentSubMenu", null);
            return handleCACRegisterVerifyMenu(choice, phone);
        }
        if ("cac_verify_menu".equals(currentSubMenu) || "cac_verify_form".equals(currentSubMenu)) {
            return handleCACVerifyFlow(phone, choice);
        }
        
        // EXISTING: FHIS More Info
        if (currentSubMenu != null && currentSubMenu.equals("more_info")) {
            saveToSession(phone, "currentSubMenu", null);
            
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
                    return HandleLevel1(phone, new String[0], false);
                default:
                    return "END Invalid choice for More Info menu.";
            }
        }
    
        // Regular organization menu
        return handleOrganizationMenu(choice, org, phone);
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

    // ==================== CAC MENU 1: REGISTER / VERIFY ====================

    
    private String showCACRegisterSubMenu(String phone) {
        saveToSession(phone, "cacRegFlow", "register_menu");
        return "CON REGISTER\n\n" +
            "Select category:\n" +
            "1. Entrepreneur\n" +
            "2. Business Owner\n" +
            "3. Organization\n" +
            "0. Back";
    }

    private String showCACVerifySubMenu(String phone) {
        saveToSession(phone, "currentSubMenu", "cac_verify_menu");
        return "CON VERIFY\n\n" +
            "1. Business Name\n" +
            "2. Company Registration\n" +
            "3. Incorporated Trustee\n" +
            "4. Application Status\n" +
            "5. Compliance Status\n" +
            "0. Back";
    }

    

    private String handleCACRegistrationTypeSelection(String phone, String choice) {
        String regType;
        switch (choice) {
            case "1": regType = "ENTREPRENEUR"; break;
            case "2": regType = "BUSINESS_OWNER"; break;
            case "3": regType = "ORGANIZATION"; break;
            case "0":
                saveToSession(phone, "cacRegFlow", null);
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" + showCACRegisterSubMenu(phone).substring(4);
        }
        saveToSession(phone, "cacRegFlow", "register_form");
        saveToSession(phone, "cacRegType", regType);
        saveToSession(phone, "cacRegField", "name");
        return "CON " + regType.replace("_", " ") + "\n\nEnter Full Name:";
    }

    private String handleCACRegistrationForm(String phone, String input) {
        String currentField = (String) retrieveFromSession(phone, "cacRegField");
        if (input == null || input.trim().isEmpty()) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
        switch (currentField) {
            case "name":
                saveToSession(phone, "cacRegName", input.trim());
                saveToSession(phone, "cacRegField", "email");
                return "CON Enter Email Address:";
            case "email":
                if (!isValidEmail(input.trim())) {
                    return "CON Invalid email. Please enter a valid email:";
                }
                saveToSession(phone, "cacRegEmail", input.trim());
                saveToSession(phone, "cacRegField", "state");
                return "CON Enter State:";
            case "state":
                saveToSession(phone, "cacRegState", input.trim());
                saveToSession(phone, "cacRegField", "occupation");
                return "CON Enter Occupation:";
            case "occupation":
                saveToSession(phone, "cacRegOccupation", input.trim());
                return saveCACRegistration(phone);
            default:
                return "END Invalid form state.";
        }
    }

    private String saveCACRegistration(String phone) {
        try {
            CacRegistration reg = new CacRegistration();
            reg.setPhoneNumber(phone);
            reg.setRegistrationType((String) retrieveFromSession(phone, "cacRegType"));
            reg.setReferenceId(generateCACReferenceId());
            reg.setFullName((String) retrieveFromSession(phone, "cacRegName"));
            reg.setEmail((String) retrieveFromSession(phone, "cacRegEmail"));
            reg.setState((String) retrieveFromSession(phone, "cacRegState"));
            reg.setOccupation((String) retrieveFromSession(phone, "cacRegOccupation"));
            reg.setCreatedAt(LocalDateTime.now());
            cacRegistrationRepository.save(reg);
            clearCACRegistrationSession(phone);
            return "END Registration Successful\n\nRef: " + reg.getReferenceId();
        } catch (Exception e) {
            System.err.println("Error saving CAC registration: " + e.getMessage());
            return "END Error saving registration. Please try again.";
        }
    }

    private String handleCACVerifyFlow(String phone, String choice) {
        String verifyMenu = (String) retrieveFromSession(phone, "currentSubMenu");
        if ("cac_verify_menu".equals(verifyMenu)) {
            switch (choice) {
                case "1": case "2": case "3": case "4": case "5":
                    saveToSession(phone, "currentSubMenu", "cac_verify_form");
                    saveToSession(phone, "cacVerifyType", choice);
                    return "CON Enter Registration Number:";
                case "0":
                    saveToSession(phone, "currentSubMenu", null);
                    return showFFSOrgMenu(phone);
                default:
                    return "CON Invalid choice.\n\n" + showCACVerifySubMenu(phone).substring(4);
            }
        }
        if ("cac_verify_form".equals(verifyMenu)) {
            String verifyType = (String) retrieveFromSession(phone, "cacVerifyType");
            String regNumber = choice.trim();
            saveToSession(phone, "currentSubMenu", null);
            saveToSession(phone, "cacVerifyType", null);
            boolean isValid = regNumber.length() >= 5;
            if (isValid) {
                return "END VERIFICATION RESULT\n\n" +
                    "Reg No: " + regNumber.toUpperCase() + "\n" +
                    "Status: ACTIVE\n" +
                    "Type: " + getCACVerifyTypeDisplay(verifyType) + "\n" +
                    "Verified by CAC";
            } else {
                return "END NOT FOUND\n\n" +
                    "Reg No: " + regNumber + "\n" +
                    "No record found.";
            }
        }
        return "END Invalid verification state.";
    }

    private String getCACVerifyTypeDisplay(String type) {
        switch (type) {
            case "1": return "Business Name";
            case "2": return "Company Registration";
            case "3": return "Incorporated Trustee";
            case "4": return "Application Status";
            case "5": return "Compliance Status";
            default: return "Unknown";
        }
    }

    private String generateCACReferenceId() {
        int random = (int) (Math.random() * 9000) + 1000;
        return "CAC-REG-" + random;
    }

    private void clearCACRegistrationSession(String phone) {
        saveToSession(phone, "cacRegFlow", null);
        saveToSession(phone, "cacRegType", null);
        saveToSession(phone, "cacRegField", null);
        saveToSession(phone, "cacRegName", null);
        saveToSession(phone, "cacRegEmail", null);
        saveToSession(phone, "cacRegState", null);
        saveToSession(phone, "cacRegOccupation", null);
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
                return "CON Personal Information Complete!\n" +
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
                return "CON Professional Information Complete!\n" +
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
                return "CON Social Information Complete!\n" +
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
                return "CON Social Information Complete!\n" +
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
                return "CON Dependants Information Complete!\n" +
                       "Progress: 90% of " + enrollmentType.toLowerCase() + " enrollment\n\n" +
                       "Final Step: Healthcare Provider Selection\n" +
                       "Enter Hospital Name:";
                       
            case "corporate_data":
            case "healthcare_provider_data":
                
                enrollment.setCurrentStep("completed");
                enrollment.setUpdatedAt(LocalDateTime.now());
                FhisEnrollmentRepository.save(enrollment);
                return "CON FHIS Enrollment Complete!\n\n" +
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
                if ("1".equals(lastInput.trim())) {
                    String lastSearch = (String) retrieveFromSession(phone, "lastHospitalSearchTerm");
                    if (lastSearch !=null ) {
                        return showHospitalList(phone, 0);
                    }
                }
                if ("0".equals(lastInput.trim())) {
                    return moveToNextStage(phone, enrollment);
                }
                
                if ("list".equalsIgnoreCase(lastInput.trim())) {
                    return showHospitalList(phone, 0);
                } else {
                    saveToSession(phone, "lastHospitalSearchTerm", lastInput.trim());
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
                // keep the last search term in session so to detect menu choice
                saveToSession(phone, "lastHospitalSearchTerm", searchTerm);
                return "CON No hospitals found for: " + searchTerm + 
                       "\n\nTry different keywords or:\n" +
                       "1. View all hospitals\n" +
                       "0. Back";
            }
            
            // Clear last search term
            saveToSession(phone, "lastHospitalSearchTerm", null);
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

    private String handleCACRegisterVerifyMenu(String choice, String phone) {
        switch (choice) {
            case "1":
                return showCACRegisterSubMenu(phone);
            case "2":
                return showCACVerifySubMenu(phone);
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n1. Register\n2. Verify\n0. Back";
        }
    }

    // start of ffs handling
    private String handleFFSRegisterVerifyMenu(String choice, String phone) {
        switch (choice) {
            case "1":
                return showRegisterSubMenu(phone);
            case "2":
                return showVerifySubMenu(phone);
            case "0":
                clearNavigationSession(phone);
                return HandleLevel1(phone, new String[0], false);
            default:
                return "CON Invalid choice.\n\n1. Register\n2. Verify\n0. Back";
        }
    }
    private String showRegisterSubMenu(String phone) {
        saveToSession(phone, "ffsRegFlow", "register_menu");
        return "CON REGISTER\n\n" +
            "1. Fire Safety Training\n" +
            "2. Volunteer Programme\n" +
            "3. Community Fire Marshal\n" +
            "4. School Fire Safety\n" +
            "5. Public Awareness Campaign\n" +
            "0. Back";
    }
    

    private String showVerifySubMenu(String phone) {
        saveToSession(phone, "currentSubMenu", "verify_menu");
        return "CON VERIFY\n\n" +
            "1. Fire Certificate\n" +
            "2. Fire Clearance\n" +
            "3. Inspection Status\n" +
            "4. Facility Compliance\n" +
            "5. Training Certificate\n" +
            "0. Back";
    }
    private String handleRegistrationFlow(String phone, String choice) {
        String regFlow = (String) retrieveFromSession(phone, "ffsRegFlow");
        
        if ("register_menu".equals(regFlow)) {
            // User is selecting registration type
            return handleRegistrationTypeSelection(phone, choice);
        }
        
        if ("register_form".equals(regFlow)) {
            // User is filling the form
            return handleRegistrationForm(phone, choice);
        }
        
        return "END Invalid registration state.";
    }
    private String handleRegistrationTypeSelection(String phone, String choice) {
        String regType;
        switch (choice) {
            case "1": regType = "TRAINING"; break;
            case "2": regType = "VOLUNTEER"; break;
            case "3": regType = "MARSHAL"; break;
            case "4": regType = "SCHOOL"; break;
            case "5": regType = "AWARENESS"; break;
            case "0":
                saveToSession(phone, "ffsRegFlow", null);
                return showFFSOrgMenu(phone); // Go back to FFS menu
            default:
                return "CON Invalid choice.\n\n" + showRegisterSubMenu(phone).substring(4);
        }
        
        saveToSession(phone, "ffsRegFlow", "register_form");
        saveToSession(phone, "ffsRegType", regType);
        saveToSession(phone, "ffsRegField", "name");
        
        return "CON " + getRegistrationTypeDisplay(regType) + "\n\nEnter Full Name:";
    }
    private String handleRegistrationForm(String phone, String input) {
        String currentField = (String) retrieveFromSession(phone, "ffsRegField");
        String regType = (String) retrieveFromSession(phone, "ffsRegType");
        
        if (input == null || input.trim().isEmpty()) {
            return "CON Field cannot be empty. Please enter " + getFieldDisplayName(currentField) + ":";
        }
        
        // Save current field
        switch (currentField) {
            case "name":
                saveToSession(phone, "ffsRegName", input.trim());
                saveToSession(phone, "ffsRegField", "address");
                return "CON Enter Address:";
                
            case "address":
                saveToSession(phone, "ffsRegAddress", input.trim());
                saveToSession(phone, "ffsRegField", "state");
                return "CON Enter State:";
                
            case "state":
                saveToSession(phone, "ffsRegState", input.trim());
                saveToSession(phone, "ffsRegField", "occupation");
                return "CON Enter Occupation:";
                
            case "occupation":
                saveToSession(phone, "ffsRegOccupation", input.trim());
                saveToSession(phone, "ffsRegField", "organization");
                return "CON Enter Organization (Optional):\n(Enter 0 to skip)";
                
            case "organization":
                String org = input.trim();
                if ("0".equals(org)) org = null;
                saveToSession(phone, "ffsRegOrg", org);
                
                // All fields collected — save to database
                return saveRegistration(phone, regType);
                
            default:
                return "END Invalid form state.";
        }
        
    }
    private String saveRegistration(String phone, String regType) {
        try {
            FfsRegistration reg = new FfsRegistration();
            reg.setPhoneNumber(phone);
            reg.setRegistrationType(regType);
            reg.setReferenceId(generateReferenceId("REG"));
            reg.setFullName((String) retrieveFromSession(phone, "ffsRegName"));
            reg.setAddress((String) retrieveFromSession(phone, "ffsRegAddress"));
            reg.setState((String) retrieveFromSession(phone, "ffsRegState"));
            reg.setOccupation((String) retrieveFromSession(phone, "ffsRegOccupation"));
            reg.setOrganization((String) retrieveFromSession(phone, "ffsRegOrg"));
            reg.setCreatedAt(LocalDateTime.now());
            
            ffsRegistrationRepository.save(reg);
            
            // Clear registration session
            clearRegistrationSession(phone);
            
            return "END Registration completed!\n\n" +
                "Ref: " + reg.getReferenceId() + "\n" +
                "You will receive SMS confirmation shortly.";
                
        } catch (Exception e) {
            System.err.println("Error saving registration: " + e.getMessage());
            return "END Error saving registration. Please try again.";
        }
    }
    // ==================== VERIFICATION FLOW (Static for now) ====================

    private String handleVerificationFlow(String phone, String choice) {
        String verifyMenu = (String) retrieveFromSession(phone, "currentSubMenu");
        
        if ("verify_menu".equals(verifyMenu)) {
            // User selected a verification type
            switch (choice) {
                case "1": case "2": case "3": case "4": case "5":
                    saveToSession(phone, "currentSubMenu", "verify_form");
                    saveToSession(phone, "verifyType", choice);
                    return "CON Enter Certificate/Registration Number:";
                case "0":
                    saveToSession(phone, "currentSubMenu", null);
                    return showFFSOrgMenu(phone);
                default:
                    return "CON Invalid choice.\n\n" + showVerifySubMenu(phone).substring(4);
            }
        }
        
        if ("verify_form".equals(verifyMenu)) {
            // Mock verification — in production, query actual DB
            String verifyType = (String) retrieveFromSession(phone, "verifyType");
            String certNumber = choice.trim();
            
            // Clear verification state
            saveToSession(phone, "currentSubMenu", null);
            saveToSession(phone, "verifyType", null);
            
            // Mock response — 50% chance verified for demo
            boolean isVerified = certNumber.length() >= 5;
            
            if (isVerified) {
                return "END Verification Status: VERIFIED\n\n" +
                    "Certificate No: " + certNumber.toUpperCase() + "\n" +
                    "Status: Active\n" +
                    "Expiry: 31-12-2026";
            } else {
                return "END Verification Status: NOT FOUND\n\n" +
                    "Certificate No: " + certNumber + "\n" +
                    "No record found. Please contact FFS.";
            }
        }
        
        return "END Invalid verification state.";
    }
    // ==================== HELPERS ====================

    private String showFFSOrgMenu(String phone) {
        Long selectedOrgId = getLongFromSession(phone, "selectedOrgId");
        if (selectedOrgId != null) {
            Optional<Organization> orgOpt = organizationRepository.findById(selectedOrgId);
            if (orgOpt.isPresent()) {
                return showorgmenu(orgOpt.get());
            }
        }
        // Fallback to main menu
        clearNavigationSession(phone);
        return HandleLevel1(phone, new String[0], false);
    }

    private String getRegistrationTypeDisplay(String type) {
        switch (type) {
            case "TRAINING": return "Fire Safety Training";
            case "VOLUNTEER": return "Volunteer Programme";
            case "MARSHAL": return "Community Fire Marshal";
            case "SCHOOL": return "School Fire Safety";
            case "AWARENESS": return "Public Awareness Campaign";
            default: return "Registration";
        }
    }
    private String generateReferenceId(String prefix) {
        int random = (int) (Math.random() * 9000) + 1000;
        return "FFS-" + prefix + "-" + random;
    }
    

    private void clearRegistrationSession(String phone) {
        saveToSession(phone, "ffsRegFlow", null);
        saveToSession(phone, "ffsRegType", null);
        saveToSession(phone, "ffsRegField", null);
        saveToSession(phone, "ffsRegName", null);
        saveToSession(phone, "ffsRegAddress", null);
        saveToSession(phone, "ffsRegState", null);
        saveToSession(phone, "ffsRegOccupation", null);
        saveToSession(phone, "ffsRegOrg", null);
    }
    private String handleFFSRequestService(String choice, String phone) {
        switch (choice) {
            case "1":
                return "CON Fire Safety Inspection\n\n" +
                    "Request received.\n" +
                    "Ref: FFS-REQ-" + generateReferenceId("REQ").substring(8) + "\n" +
                    "Our team will contact you.\n\n0. Back";
            case "2":
                return "CON Facility Assessment\n\n" +
                    "Request received.\n" +
                    "Ref: FFS-REQ-" + generateReferenceId("REQ").substring(8) + "\n" +
                    "Our team will contact you.\n\n0. Back";
            case "3":
                return "CON Fire Safety Training\n\n" +
                    "Request received.\n" +
                    "Ref: FFS-REQ-" + generateReferenceId("REQ").substring(8) + "\n" +
                    "Our team will contact you.\n\n0. Back";
            case "4":
                return "CON Public Awareness Visit\n\n" +
                    "Request received.\n" +
                    "Ref: FFS-REQ-" + generateReferenceId("REQ").substring(8) + "\n" +
                    "Our team will contact you.\n\n0. Back";
            case "5":
                return "CON Fire Truck Demonstration\n\n" +
                    "Request received.\n" +
                    "Ref: FFS-REQ-" + generateReferenceId("REQ").substring(8) + "\n" +
                    "Our team will contact you.\n\n0. Back";
            case "6":
                return "CON Emergency Preparedness Consultation\n\n" +
                    "Request received.\n" +
                    "Ref: FFS-REQ-" + generateReferenceId("REQ").substring(8) + "\n" +
                    "Our team will contact you.\n\n0. Back";
            case "7":
                return "CON Callback Request\n\n" +
                    "Request received.\n" +
                    "Ref: FFS-REQ-" + generateReferenceId("REQ").substring(8) + "\n" +
                    "Our team will contact you.\n\n0. Back";
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" +
                    "1. Fire Safety Inspection\n" +
                    "2. Facility Assessment\n" +
                    "3. Fire Safety Training\n" +
                    "4. Public Awareness Visit\n" +
                    "5. Fire Truck Demonstration\n" +
                    "6. Emergency Preparedness Consultation\n" +
                    "7. Callback Request\n" +
                    "0. Back";
        }
    }
    private String handleFFSReportIncident(String choice, String phone) {
        String refId = "FFS-RPT-" + ((int) (Math.random() * 9000) + 1000);
        
        switch (choice) {
            case "1":
                return "END FIRE OUTBREAK REPORTED\n\n" +
                    "Reference: " + refId + "\n" +
                    "Nearest fire station notified.\n" +
                    "Stay calm. Help is on the way.";
            case "2":
                return "END GAS EXPLOSION REPORTED\n\n" +
                    "Reference: " + refId + "\n" +
                    "Evacuate immediately.\n" +
                    "Do not use electrical switches.\n" +
                    "Emergency team dispatched.";
            case "3":
                return "END ELECTRICAL FIRE REPORTED\n\n" +
                    "Reference: " + refId + "\n" +
                    "Turn off power if safe.\n" +
                    "Do not use water.\n" +
                    "Use CO2 extinguisher only.";
            case "4":
                return "END BUSH FIRE REPORTED\n\n" +
                    "Reference: " + refId + "\n" +
                    "Move away from fire path.\n" +
                    "Rangers and fire service notified.";
            case "5":
                return "END BUILDING COLLAPSE REPORTED\n\n" +
                    "Reference: " + refId + "\n" +
                    "Rescue team mobilized.\n" +
                    "Clear the area for emergency access.";
            case "6":
                return "END HAZARDOUS MATERIALS INCIDENT\n\n" +
                    "Reference: " + refId + "\n" +
                    "Hazmat team alerted.\n" +
                    "Avoid contact. Evacuate area.";
            case "7":
                return "END FALSE ALARM REPORTED\n\n" +
                    "Reference: " + refId + "\n" +
                    "Thank you for the update.\n" +
                    "Please avoid false reports.";
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" +
                    "1. Fire Outbreak\n" +
                    "2. Gas Explosion\n" +
                    "3. Electrical Fire\n" +
                    "4. Bush Fire\n" +
                    "5. Building Collapse\n" +
                    "6. Hazardous Materials Incident\n" +
                    "7. False Alarm Report\n" +
                    "0. Back";
        }
    }
    private String handleFFSGuidelines(String choice, String phone) {
        switch (choice) {
            case "1":
                return "END HOME FIRE SAFETY\n\n" +
                    "- Install smoke alarms\n" +
                    "- Keep fire extinguishers handy\n" +
                    "- Never leave cooking unattended\n" +
                    "- Plan escape routes\n" +
                    "- Check electrical wiring regularly";
            case "2":
                return "END OFFICE FIRE SAFETY\n\n" +
                    "- Know fire exits\n" +
                    "- No overloaded sockets\n" +
                    "- Store chemicals safely\n" +
                    "- Conduct fire drills\n" +
                    "- Maintain fire extinguishers";
            case "3":
                return "END SCHOOL FIRE SAFETY\n\n" +
                    "- Clear exit paths\n" +
                    "- Train staff and students\n" +
                    "- Regular fire drills\n" +
                    "- Safe storage of lab chemicals\n" +
                    "- Report faulty wiring";
            case "4":
                return "END MARKET FIRE PREVENTION\n\n" +
                    "- No open flames near stalls\n" +
                    "- Proper waste disposal\n" +
                    "- Accessible fire exits\n" +
                    "- Ban illegal electrical connections\n" +
                    "- Install fire alarms";
            case "5":
                return "END FUEL AND GAS SAFETY\n\n" +
                    "- Store fuel away from heat\n" +
                    "- Check gas cylinders for leaks\n" +
                    "- Never smoke near fuel\n" +
                    "- Use approved containers\n" +
                    "- Report gas leaks immediately";
            case "6":
                return "END EMERGENCY EVACUATION\n\n" +
                    "- Stay calm\n" +
                    "- Use stairs, not elevators\n" +
                    "- Help children and elderly\n" +
                    "- Go to assembly point\n" +
                    "- Do not re-enter building";
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" +
                    "1. Home Fire Safety\n" +
                    "2. Office Fire Safety\n" +
                    "3. School Fire Safety\n" +
                    "4. Market Fire Prevention\n" +
                    "5. Fuel and Gas Safety\n" +
                    "6. Emergency Evacuation Procedures\n" +
                    "0. Back";
        }
    }
    private String handleFFSFAQs(String choice, String phone) {
        switch (choice) {
            case "1":
                return "END HOW TO OBTAIN A FIRE CERTIFICATE\n\n" +
                    "1. Apply online or visit FFS office\n" +
                    "2. Submit building plans\n" +
                    "3. Schedule inspection\n" +
                    "4. Pay required fees\n" +
                    "5. Certificate issued after compliance";
            case "2":
                return "END HOW TO REQUEST INSPECTION\n\n" +
                    "Dial *7447#\n" +
                    "Select Federal Fire Service\n" +
                    "Choose Request Service\n" +
                    "Select Fire Safety Inspection\n" +
                    "Fill required details";
            case "3":
                return "END FIRE SAFETY REQUIREMENTS\n\n" +
                    "- Fire extinguishers\n" +
                    "- Smoke detectors\n" +
                    "- Emergency exits\n" +
                    "- Fire alarm systems\n" +
                    "- Trained fire wardens";
            case "4":
                return "END EMERGENCY RESPONSE PROCEDURES\n\n" +
                    "1. Raise alarm\n" +
                    "2. Call 112 or FFS hotline\n" +
                    "3. Evacuate calmly\n" +
                    "4. Use extinguisher if safe\n" +
                    "5. Wait for emergency services";
            case "5":
                return "END CONTACT INFORMATION\n\n" +
                    "Federal Fire Service HQ:\n" +
                    "Abuja, Nigeria\n\n" +
                    "Hotline: 0703-590-4570\n" +
                    "Email: info@federal.gov.ng\n" +
                    "Website: www.federal.gov.ng";
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" +
                    "1. How to obtain a Fire Certificate\n" +
                    "2. How to request inspection\n" +
                    "3. Fire safety requirements\n" +
                    "4. Emergency response procedures\n" +
                    "5. Contact information\n" +
                    "0. Back";
        }
    }
    private String handleFFSAlerts(String choice, String phone) {
        switch (choice) {
            case "1":
                return "END FIRE SAFETY TIPS\n\n" +
                    "Tip of the week:\n" +
                    "Always check your electrical " +
                    "appliances before leaving home.\n" +
                    "Unplug devices not in use.";
            case "2":
                return "END SEASONAL FIRE WARNINGS\n\n" +
                    "Current Season: Harmattan\n" +
                    "Risk Level: HIGH\n\n" +
                    "Avoid bush burning.\n" +
                    "Keep water sources accessible.\n" +
                    "Report smoke sightings immediately.";
            case "3":
                return "END HARMATTAN FIRE ADVISORIES\n\n" +
                    "Dry season precautions:\n" +
                    "- Clear dry vegetation\n" +
                    "- No open burning\n" +
                    "- Store flammables safely\n" +
                    "- Keep fire service contacts handy";
            case "4":
                return "END FLOOD AND DISASTER ALERTS\n\n" +
                    "No active flood warnings.\n\n" +
                    "For updates dial *7447#\n" +
                    "Select Alerts & Updates.";
            case "5":
                return "END PUBLIC SAFETY ANNOUNCEMENTS\n\n" +
                    "All markets must install\n" +
                    "fire extinguishers by Dec 2026.\n\n" +
                    "Compliance inspections begin Jan 2027.";
            case "6":
                return "END EMERGENCY PREPAREDNESS\n\n" +
                    "Join our community training:\n" +
                    "Every first Saturday.\n\n" +
                    "Dial *7447# → Register →\n" +
                    "Fire Safety Training to sign up.";
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" +
                    "1. Fire Safety Tips\n" +
                    "2. Seasonal Fire Warnings\n" +
                    "3. Harmattan Fire Advisories\n" +
                    "4. Flood and Disaster Alerts\n" +
                    "5. Public Safety Announcements\n" +
                    "6. Emergency Preparedness Campaigns\n" +
                    "0. Back";
        }
    }
    private String handleFFSMoreInfo(String choice, String phone) {
        switch (choice) {
            case "1":
                return "END SERVICE LOCATIONS\n\n" +
                    "Federal Fire Service operates\n" +
                    "in all 36 states + FCT.\n\n" +
                    "Major stations:\n" +
                    "- Abuja (HQ)\n" +
                    "- Lagos\n" +
                    "- Kano\n" +
                    "- Port Harcourt\n" +
                    "- Enugu";
            case "2":
                return "END STATE COMMANDS\n\n" +
                    "Each state has a Commanding Officer.\n\n" +
                    "Contact your state fire service\n" +
                    "for local emergencies.\n\n" +
                    "Dial *7447# → Call Lines\n" +
                    "for state contacts.";
            case "3":
                return "END OFFICE ADDRESSES\n\n" +
                    "Headquarters:\n" +
                    "Federal Fire Service\n" +
                    "Mabushi, Abuja\n\n" +
                    "Regional offices in\n" +
                    "all geopolitical zones.";
            case "4":
                return "END APPROVED CONSULTANTS\n\n" +
                    "List of approved fire safety\n" +
                    "consultants available at FFS HQ.\n\n" +
                    "Apply for accreditation:\n" +
                    "consultants@federal.gov.ng";
            case "5":
                return "END OFFICE HOURS\n\n" +
                    "Monday - Friday: 8am - 4pm\n" +
                    "Emergency: 24/7\n\n" +
                    "Call 112 anytime.\n" +
                    "Visit www.federal.gov.ng";
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" +
                    "1. Service Locations\n" +
                    "2. State Commands\n" +
                    "3. Office Addresses\n" +
                    "4. Approved Fire Safety Consultants\n" +
                    "5. Office Hours\n" +
                    "0. Back";
        }
    }
    private String handleFFSAccountProfile(String choice, String phone) {
        switch (choice) {
            case "1":
                return "END UPDATE PROFILE\n\n" +
                    "Profile update coming soon.\n" +
                    "Visit FFS office with ID.\n\n" +
                    "Ref: FFS-ACC-UPD-" + ((int) (Math.random() * 9000) + 1000);
            case "2":
                return "END CHANGE PIN\n\n" +
                    "PIN change not available on USSD.\n\n" +
                    "Use web portal or visit office.";
            case "3":
                return "END NOTIFICATION PREFERENCES\n\n" +
                    "You will receive:\n" +
                    "- Fire alerts: YES\n" +
                    "- Safety tips: YES\n" +
                    "- Seasonal warnings: YES\n\n" +
                    "To change: visit FFS office.";
            case "4":
                return "END VIEW RECENT REQUESTS\n\n" +
                    "No recent requests found.\n\n" +
                    "Make a request via *7447# →\n" +
                    "Federal Fire Service → Request Service.";
            case "5":
                return "END ACCOUNT RECOVERY\n\n" +
                    "Contact FFS support:\n" +
                    "0703-590-4570\n\n" +
                    "Provide phone number and ID.";
            case "0":
                return showFFSOrgMenu(phone);
            default:
                return "CON Invalid choice.\n\n" +
                    "1. Update Profile\n" +
                    "2. Change PIN\n" +
                    "3. Notification Preferences\n" +
                    "4. View Recent Requests\n" +
                    "5. Account Recovery\n" +
                    "0. Back";
        }
    }
}

