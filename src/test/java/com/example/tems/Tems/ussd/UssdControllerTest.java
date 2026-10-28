package com.example.tems.Tems.ussd;

import com.example.tems.Tems.controller.ussdcontroller;
import com.example.tems.Tems.client.CbmUssdRelayClient;
import com.example.tems.Tems.service.Smsservice;
import com.example.tems.Tems.service.PaymentGate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class UssdControllerTest {
    private MockMvc mvc;
    private final Map<String, Object> state = new HashMap<>();
    private UssdGatewayProperties gateway;
    private CbmUssdRelayClient relay;
    private RedisTemplate<String, Object> redis;
    private String allowedPhone;
    private PaymentGate payment;
    private Smsservice sms;
    private UssdRouteResolver resolver;
    private static final String ORDINARY_PHONE = "08000000000";
    private static final String[] CBM_SUFFIXES = {"1", "2", "7", "27"};
    private static final String[] TEMS_SUFFIXES = {"6", "9", "20"};

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(a -> state.get(a.getArgument(0)));
        doAnswer(a -> { state.put(a.getArgument(0), a.getArgument(1)); return null; })
            .when(values).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(redis.hasKey(anyString())).thenAnswer(a -> state.containsKey(a.getArgument(0)));
        when(redis.delete(anyString())).thenAnswer(a -> state.remove(a.getArgument(0)) != null);
        when(redis.keys(anyString())).thenAnswer(a -> {
            String prefix = ((String) a.getArgument(0)).replace("*", "");
            Set<String> keys = new HashSet<>();
            state.keySet().stream().filter(k -> k.startsWith(prefix)).forEach(keys::add);
            return keys;
        });
        when(redis.delete(anyCollection())).thenAnswer(a -> {
            Collection<String> keys = a.getArgument(0);
            keys.forEach(state::remove);
            return (long) keys.size();
        });
        relay = mock(CbmUssdRelayClient.class);
        when(relay.relay(any(), anyString(), anyString())).thenReturn("CON Relay next question");
        sms = mock(Smsservice.class);
        payment = mock(PaymentGate.class);
        ussdcontroller controller = new ussdcontroller(null, null, null, null, null, null, null,
            null, null, sms, null, null, relay);
        gateway = new UssdGatewayProperties();
        gateway.getMessageTypes().put("BEGIN", UssdInboundRequest.SessionEvent.BEGIN);
        gateway.getMessageTypes().put("CONTINUE", UssdInboundRequest.SessionEvent.CONTINUE);
        gateway.getMessageTypes().put("END", UssdInboundRequest.SessionEvent.END);
        ReflectionTestUtils.setField(controller, "redisTemplate", redis);
        ReflectionTestUtils.setField(controller, "gateway", gateway);
        resolver = spy(new UssdRouteResolver());
        ReflectionTestUtils.setField(controller, "routeResolver", resolver);
        ReflectionTestUtils.setField(controller, "paymentGate", payment);
        ReflectionTestUtils.setField(controller, "routeProbe", new UssdRouteProbe(redis, gateway, new UssdRouteParser()));
        Set<String> allowed = (Set<String>) ReflectionTestUtils.getField(ussdcontroller.class, "TMF_TEST_PHONE_NUMBERS");
        allowedPhone = allowed.iterator().next();
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
    private static void assertTemsMenu(String response, String what) {
        assertTrue(response.startsWith("CON Welcome to TEMS"), what + " should show the TEMS menu but got: " + response);
        assertFalse(response.contains("CITY BOY MOVEMENT"), what + " must not open the CBM menu");
    }
    
    private String call(String phone, String session, String service, String input, String text, String event) throws Exception {
        return mvc.perform(post("/ussd").param("phoneNumber", phone).param("sessionId", session)
            .param("serviceCode", service).param("input", input).param("text", text).param("messageType", event)
            .header("Accept", "text/plain"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void allowedCallerUsesAuthoritativeServiceAndAdvancesWithRepeatedCode() throws Exception {
        assertTrue(call(allowedPhone, "s1", "*7447*10#", "7447", "10", "").contains("TEXT ME FOOD FOUNDATION"));
        assertTrue(call(allowedPhone, "s1", "*7447*10#", "1", "", "").contains("Enter 4-digit PIN"));
        assertEquals("pin", state.get(allowedPhone + ":tmfFlow"));
    }

    @Test void cumulativeTmfReplyDoesNotRestart() throws Exception {
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        call(allowedPhone, "s1", "*7447#", "", "10", "BEGIN");
        assertTrue(call(allowedPhone, "s1", "*7447#", "", "10*1", "CONTINUE").contains("Enter 4-digit PIN"));
    }

    @Test void cbmCumulativeChoiceOneStartsRelayAndChoiceTwoShowsAbout() throws Exception {
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        call(ORDINARY_PHONE, "s1", "*7447#", "", "27", "BEGIN");
        assertTrue(call(ORDINARY_PHONE, "s1", "*7447#", "", "7447*27*1", "CONTINUE").contains("Relay next"));
        verify(relay).relay("s1", ORDINARY_PHONE, "");
        call(ORDINARY_PHONE, "s2", "*7447*27#", "", "", "BEGIN");
        assertTrue(call(ORDINARY_PHONE, "s2", "*7447*27#", "", "27*2", "CONTINUE").contains("ABOUT CITY BOY"));
    }

    @Test void ordinaryReplyOneIsATemsMenuChoice() throws Exception {
        call(ORDINARY_PHONE, "s1", "*7447#", "", "", "");
        // Option 1 on the TEMS menu is City Boy Movement.
        assertTrue(call(ORDINARY_PHONE, "s1", "*7447#", "1", "", "").contains("CITY BOY MOVEMENT"));
        assertEquals("main_menu", state.get(ORDINARY_PHONE + ":cbmFlow"));
        verifyNoInteractions(relay);
    }

    // HML always sends shortcode *7447#; the dialled sub-code is only in input on the first message.
    private String hml(String input, String session) throws Exception {
        String sessionField = session == null ? "" : ",\"session_id\":\"" + session + "\"";
        return mvc.perform(post("/ussd").contentType("application/json").header("Accept", "text/plain")
            .content("{\"telco\":\"MTN\",\"shortcode\":\"*7447#\",\"product_id\":123,\"phone\":\"2348000000001\",\"input\":\""
                + input + "\"" + sessionField + "}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void hmlSubCodeAdvancesWithSingleOrFullInput() throws Exception {
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        assertTrue(hml("", "base").contains("Welcome to TEMS"));
        assertTrue(hml("7447*1", "x1").contains("CITY BOY MOVEMENT"));
        assertTrue(hml("7447*1*2", "x1").contains("ABOUT CITY BOY"));
        assertTrue(hml("7447*1", "x2").contains("CITY BOY MOVEMENT"));
        assertTrue(hml("2", "x2").contains("ABOUT CITY BOY"));
    }

    @Test void hmlSubCodeWorksWhenTelcoChargeAcceptanceIsForwarded() throws Exception {
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        // The key pressed on the telco charge screen can arrive appended to the dial string.
        assertTrue(hml("7447*1*1", "c1").contains("CITY BOY MOVEMENT"));
        assertTrue(hml("7447*1*1*2", "c1").contains("ABOUT CITY BOY"));
        assertTrue(hml("7447*1*1", "c2").contains("CITY BOY MOVEMENT"));
        assertTrue(hml("2", "c2").contains("ABOUT CITY BOY"));
        verifyNoInteractions(relay);
    }

    @Test void hmlSubCodeStartsFreshWhenSessionIdIsReusedOrMissing() throws Exception {
        hml("", "same");
        assertTrue(hml("7447*1", "same").contains("CITY BOY MOVEMENT"));
        state.clear();
        hml("", null);
        assertTrue(hml("7447*1", null).contains("CITY BOY MOVEMENT"));
    }
    @Test void hmlElevenPrefixWorksLikeOneHundred() throws Exception {
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        assertTrue(hml("7447*11", "p0").contains("Welcome to TEMS"));
        assertTrue(hml("7447*11*1", "p1").contains("CITY BOY MOVEMENT"));
        assertTrue(hml("7447*11*2", "p2").contains("CITY BOY MOVEMENT"));
        assertTrue(hml("7447*11*7", "p3").contains("CITY BOY MOVEMENT"));
        assertTrue(hml("7447*11*27", "p4").contains("CITY BOY MOVEMENT"));
    }
    @Test void hmlDialAfter11GoesToCbmAndOtherSuffixesStayOnTems() throws Exception {
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        for (String suffix : CBM_SUFFIXES) {
            assertTrue(hml("7447*11*" + suffix, "c" + suffix).contains("CITY BOY MOVEMENT"),
                "suffix " + suffix + " should open CBM");
            assertTrue(hml("2", "c" + suffix).contains("ABOUT CITY BOY"));
        }
        for (String suffix : TEMS_SUFFIXES) {
            assertTemsMenu(hml("7447*11*" + suffix, "t" + suffix), "suffix " + suffix);
        }
        assertTemsMenu(hml("7447*11", "plain"), "plain dial");
        verifyNoInteractions(relay);
    }

    @Test void deniedCallerDoesNotEnterTmf() throws Exception {
        assertTrue(call(ORDINARY_PHONE, "s1", "*7447*10#", "", "", "").contains("Welcome to TEMS"));
        call(ORDINARY_PHONE, "s1", "*7447*10#", "1", "", "");
        assertNull(state.get(ORDINARY_PHONE + ":tmfFlow"));
    }

    @Test void newSessionClearsOldFlowAndMissingIdDoesNotResetActiveFlow() throws Exception {
        call(allowedPhone, "old", "*7447*10#", "", "", "");
        call(allowedPhone, "new", "*7447*27#", "", "", "");
        assertNull(state.get(allowedPhone + ":tmfFlow"));
        assertEquals("CBM", state.get(allowedPhone + ":ussdRoute"));
        assertTrue(call(allowedPhone, "", "*7447*27#", "2", "", "").contains("ABOUT CITY BOY"));
    }

    @Test void explicitBeginOverridesStaleStateWithoutSessionId() throws Exception {
        call(allowedPhone, "", "*7447*27#", "", "", "");
        assertTrue(call(allowedPhone, "", "*7447#", "10", "", "BEGIN").contains("TEXT ME FOOD FOUNDATION"));
        assertNull(state.get(allowedPhone + ":cbmFlow"));
    }

    @Test void delayedContinuationDoesNotDeleteTheNewerSession() throws Exception {
        call(allowedPhone, "new", "*7447*10#", "", "", "BEGIN");
        assertTrue(call(allowedPhone, "old", "*7447*27#", "1", "", "CONTINUE").startsWith("END Session expired"));
        assertEquals("new", state.get(allowedPhone + ":ussdSessionId"));
        assertEquals("TMF", state.get(allowedPhone + ":ussdRoute"));
    }

    @Test void unknownRouteAndExpiredContinuationEndCleanly() throws Exception {
        assertTrue(call(ORDINARY_PHONE, "s1", "*7447*222#", "", "", "").startsWith("END Unknown"));
        assertTrue(call(ORDINARY_PHONE, "s2", "*7447#", "1", "", "CONTINUE").startsWith("END Session expired"));
    }

    @Test void phoneNormalizationPreservesAllowlistAndEndClearsState() throws Exception {
        String international = "+234" + allowedPhone.substring(1);
        assertTrue(call(international, "s1", "*7447*10#", "", "", "").contains("TEXT ME FOOD FOUNDATION"));
        assertTrue(call(international, "s1", "*7447*10#", "6", "", "").startsWith("END "));
        assertFalse(state.containsKey(allowedPhone + ":ussdRoute"));
    }

    @Test void missingIdBareOneIsRejectedAndStaleBareTenIsAnAnswer() throws Exception {
        assertTrue(call(ORDINARY_PHONE, "", "7447", "1", "", "").startsWith("END Unknown"));
        call(allowedPhone, "", "*7447*27#", "", "", "");
        call(allowedPhone, "", "*7447#", "10", "", "");
        assertEquals("CBM", state.get(allowedPhone + ":ussdRoute"));
        assertNull(state.get(allowedPhone + ":tmfFlow"));
    }

    @Test void redisFailureReturnsControlledErrorWithoutExceptionContents(CapturedOutput output) throws Exception {
        when(redis.opsForValue().get(anyString())).thenThrow(new IllegalStateException("SECRET_REDIS_DETAIL"));
        assertTrue(call(allowedPhone, "s1", "*7447*10#", "", "", "").startsWith("END Service temporarily"));
        assertFalse(output.getAll().contains("SECRET_REDIS_DETAIL"));
    }

    @Test void invalidFormAndMissingPhoneAreControlled() throws Exception {
        mvc.perform(post("/ussd").contentType("application/x-www-form-urlencoded").content("input=%ZZ"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.continue").value(false));
        assertTrue(call("", "s1", "*7447#", "", "", "").startsWith("END Invalid request"));
    }

    @Test void getAndFormAliasesAndJsonBodyAllWork() throws Exception {
        mvc.perform(get("/ussd").param("msisdn", ORDINARY_PHONE).param("shortCode", "7447")
            .param("ussdString", "27").param("sessionId", "s1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.continue").value(true));
        assertEquals("CBM", state.get(ORDINARY_PHONE + ":ussdRoute"));
        mvc.perform(post("/ussd").contentType("application/x-www-form-urlencoded")
            .content("msisdn=" + ORDINARY_PHONE + "&shortCode=%2A7447%2A1%23&sessionId=s2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.continue").value(true));
        mvc.perform(post("/ussd").contentType("application/json")
            .content("{\"phoneNumber\":\"" + allowedPhone + "\",\"sessionId\":\"s3\",\"serviceCode\":\"*7447*10#\",\"input\":\"7447\",\"text\":\"10\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("TEXT ME FOOD FOUNDATION")));
    }

    @Test void malformedInputUsesConfiguredResponseAndLogsNoSecrets(CapturedOutput output) throws Exception {
        gateway.setResponseMode(UssdGatewayProperties.ResponseMode.PLAIN);
        mvc.perform(post("/ussd").contentType("application/json").header("Authorization", "Bearer SECRET_AUTH")
            .content("{\"input\":\"SECRET_PIN"))
            .andExpect(status().isOk()).andExpect(content().string("END Invalid USSD request."));
        call(allowedPhone, "s1", "*7447*10#", "", "", "");
        call(allowedPhone, "s1", "*7447*10#", "1", "", "");
        call(allowedPhone, "s1", "*7447*10#", "SECRET_PIN", "", "");
        assertFalse(output.getOut().contains(allowedPhone));
        assertFalse(output.getAll().contains("SECRET_PIN"));
        assertFalse(output.getAll().contains("SECRET_AUTH"));
        gateway.setResponseMode(UssdGatewayProperties.ResponseMode.JSON);
        mvc.perform(post("/ussd").header("Accept", "text/plain").contentType("application/json").content("[]"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.continue").value(false));
    }

    private void enableProbe() {
        gateway.setAliasTestEnabled(true);
        gateway.setDiagnosticsEnabled(true);
        gateway.setAliasTestNumbers(Set.of(ORDINARY_PHONE));
    }

    @Test void probeCompletesTwoCallbacksWithoutApplicationDependencies(CapturedOutput output) throws Exception {
        enableProbe();
        // Stale application Redis must remain untouched by the transport experiment.
        state.put(ORDINARY_PHONE + ":ussdRoute", "TMF");
        state.put(ORDINARY_PHONE + ":ussdSessionId", "stale");
        state.put(ORDINARY_PHONE + ":selectedOrgId", 999L);
        assertEquals(UssdRouteProbe.MENU, call(ORDINARY_PHONE, "probe", "*7447*1#", "", "", "BEGIN"));
        assertEquals("END Route alias session test successful.",
            call(ORDINARY_PHONE, "probe", "*7447*1#", "1", "", "CONTINUE"));
        assertEquals("TMF", state.get(ORDINARY_PHONE + ":ussdRoute"));
        assertEquals(999L, state.get(ORDINARY_PHONE + ":selectedOrgId"));
        assertFalse(state.containsKey(UssdRouteProbe.key(ORDINARY_PHONE, "probe")));
        verifyNoInteractions(payment, sms, relay);
        assertTrue(output.getAll().contains("route-alias-test"));
        assertTrue(output.getAll().contains("FOLLOW_UP"));
        assertFalse(output.getAll().contains(ORDINARY_PHONE));
    }

    @Test void probeSamePhoneDifferentSessionCannotCompletePriorMenu() throws Exception {
        enableProbe();
        call(ORDINARY_PHONE, "first", "*7447*1#", "", "", "BEGIN");
        assertTrue(call(ORDINARY_PHONE, "second", "*7447*1#", "1", "", "CONTINUE").contains("expired"));
        assertTrue(state.containsKey(UssdRouteProbe.key(ORDINARY_PHONE, "first")));
        assertTrue(call(ORDINARY_PHONE, "first", "*7447#", "1", "", "CONTINUE").contains("successful"));
        verifyNoInteractions(payment, sms, relay);
    }

    @Test void probeExitTimeoutAndExplicitRestart() throws Exception {
        enableProbe();
        call(ORDINARY_PHONE, "exit", "*7447*1#", "", "", "BEGIN");
        assertEquals("END Route alias test exited.", call(ORDINARY_PHONE, "exit", "*7447*1#", "0", "", "CONTINUE"));
        call(ORDINARY_PHONE, "timeout", "*7447*1#", "", "", "BEGIN");
        state.remove(UssdRouteProbe.key(ORDINARY_PHONE, "timeout")); // Redis TTL expired.
        assertTrue(call(ORDINARY_PHONE, "timeout", "*7447*1#", "1", "", "CONTINUE").contains("expired"));
        assertTrue(call(ORDINARY_PHONE, "timeout", "*7447*1#", "1", "", "").contains("expired"));
        state.put(UssdRouteProbe.key(ORDINARY_PHONE, "corrupt"), "invalid-old-marker");
        assertTrue(call(ORDINARY_PHONE, "corrupt", "*7447*1#", "1", "", "CONTINUE").contains("expired"));
        assertEquals(UssdRouteProbe.MENU, call(ORDINARY_PHONE, "new", "*7447*1#", "", "", "BEGIN"));
        verify(redis.opsForValue(), atLeastOnce()).set(anyString(), anyMap(), eq(3L), eq(TimeUnit.MINUTES));
        verifyNoInteractions(payment, sms, relay);
    }

    @Test void probeMissingSessionFailsClosedAndEndClosesOnlyItsSession() throws Exception {
        enableProbe();
        assertTrue(call(ORDINARY_PHONE, "", "*7447*1#", "", "", "BEGIN").contains("stable session ID"));
        call(ORDINARY_PHONE, "first", "*7447*1#", "", "", "BEGIN");
        call(ORDINARY_PHONE, "second", "*7447*1#", "", "", "BEGIN");
        assertTrue(call(ORDINARY_PHONE, "first", "*7447*1#", "", "", "END").contains("closed"));
        assertTrue(state.containsKey(UssdRouteProbe.key(ORDINARY_PHONE, "second")));
        verifyNoInteractions(payment, sms, relay);
    }

    @Test void probeCumulativeInputAndParentWithSuffixAreSupported() throws Exception {
        enableProbe();
        gateway.setAliasTestNumbers(Set.of(ORDINARY_PHONE, "08000000001"));
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        assertEquals(UssdRouteProbe.MENU, call(ORDINARY_PHONE, "split", "*7447#", "1", "", "BEGIN"));
        assertTrue(call(ORDINARY_PHONE, "split", "*7447#", "1*1", "", "CONTINUE").contains("successful"));
        assertEquals(UssdRouteProbe.MENU, hml("7447*1", "hml-probe"));
        assertTrue(hml("7447*1*1", "hml-probe").contains("successful"));
        verifyNoInteractions(payment, sms, relay);
    }

    @Test void probeJsonFormAndQueryUseSameResponseFormatter() throws Exception {
        enableProbe();
        gateway.setResponseMode(UssdGatewayProperties.ResponseMode.JSON);
        mvc.perform(post("/ussd").contentType("application/json")
            .content("{\"msisdn\":\"" + ORDINARY_PHONE + "\",\"session_id\":\"json-probe\",\"shortcode\":\"*7447*1#\"}"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.continue").value(true))
            .andExpect(jsonPath("$.message").value("TEMS ROUTE ALIAS TEST\n\n1. Continue\n0. Exit"));
        mvc.perform(post("/ussd").contentType("application/x-www-form-urlencoded")
            .content("phone=" + ORDINARY_PHONE + "&session_id=json-probe&shortcode=%2A7447%2A1%23&ussd_string=1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.continue").value(false))
            .andExpect(jsonPath("$.message").value("Route alias session test successful."));
        gateway.setResponseMode(UssdGatewayProperties.ResponseMode.PLAIN);
        mvc.perform(get("/ussd").param("phone", ORDINARY_PHONE).param("session_id", "query-probe")
            .param("service_code", "*7447*1#").header("Accept", "*/*"))
            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/plain"))
            .andExpect(content().string(UssdRouteProbe.MENU));
        verifyNoInteractions(payment, sms, relay);
    }

    @Test void probeAllowlistAndDefaultOffPreserveExistingRoutes(CapturedOutput output) throws Exception {
        gateway.setDiagnosticsEnabled(true);
        assertTrue(call(ORDINARY_PHONE, "off", "*7447*1#", "", "", "BEGIN").contains("CITY BOY MOVEMENT"));
        enableProbe();
        gateway.setAliasTestNumbers(Set.of("08000000009"));
        assertTrue(call(ORDINARY_PHONE, "denied", "*7447*1#", "", "", "BEGIN").contains("CITY BOY MOVEMENT"));
        assertTrue(output.getAll().contains("allowlist-denied-use-existing-flow"));
        gateway.setAliasTestNumbers(Set.of(ORDINARY_PHONE));
        assertTrue(call(ORDINARY_PHONE, "legacy", "*7447*11*1#", "", "", "BEGIN").contains("CITY BOY MOVEMENT"));
        verify(payment, times(3)).check(eq(ORDINARY_PHONE), anyString());
    }

    @Test void existingLegacyAndPublicEntryAlreadyConvergeAndRepliesAdvance() throws Exception {
        String legacy = call(ORDINARY_PHONE, "legacy", "*7447*11*1#", "", "", "BEGIN");
        String direct = call(ORDINARY_PHONE, "direct", "*7447*1#", "", "", "BEGIN");
        assertEquals(legacy, direct);
        assertEquals("CBM", state.get(ORDINARY_PHONE + ":ussdRoute"));
        assertTrue(call(ORDINARY_PHONE, "direct", "*7447*1#", "2", "", "CONTINUE").contains("ABOUT CITY BOY"));
    }

    @Test void splitTransportMetadataUsesTheSameParsedCbmCanonicalTarget() throws Exception {
        String response = mvc.perform(post("/ussd").param("phone", ORDINARY_PHONE).param("sessionId", "split-legacy")
            .param("serviceCode", "*7447*11#").param("ussdString", "1").header("Accept", "text/plain"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("CITY BOY MOVEMENT"));
        assertEquals("11*1", state.get(ORDINARY_PHONE + ":ussdCanonicalRoute"));
    }

    @Test void bothTmfEntriesShareCanonicalStateAndFollowupOneNeverResolvesAgain(CapturedOutput output) throws Exception {
        gateway.setDiagnosticsEnabled(true);
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        for (String service : List.of("*7447*10#", "*7447*11*10#")) {
            String session = service.equals("*7447*10#") ? "short-tmf" : "canonical-tmf";
            String menu = call(allowedPhone, session, service, "", "", "BEGIN");
            assertTrue(menu.contains("TEXT ME FOOD FOUNDATION"));
            assertEquals("11*10", state.get(allowedPhone + ":ussdCanonicalRoute"));
            assertEquals("10", state.get(allowedPhone + ":ussdPublicRoute"));
            assertEquals(service.contains("11") ? "11" : "", state.get(allowedPhone + ":ussdTransportPrefix"));
            assertTrue(call(allowedPhone, session, service, "1", "", "CONTINUE").contains("Enter 4-digit PIN"));
            assertEquals("pin", state.get(allowedPhone + ":tmfFlow"));
            assertEquals("11*10", state.get(allowedPhone + ":ussdCanonicalRoute"));
        }
        verify(resolver, times(2)).resolveEntry(any(), anyBoolean(), anyBoolean());
        assertTrue(output.getAll().contains("\"originalServiceCode\":\"*7447*10#\""));
        assertTrue(output.getAll().contains("\"originalServiceCode\":\"*7447*11*10#\""));
        assertTrue(output.getAll().contains("\"canonicalRoute\":\"11*10\""));
        assertTrue(output.getAll().contains("\"publicRoute\":\"10\""));
        assertTrue(output.getAll().contains("\"transportPrefix\":\"11\""));
        assertTrue(output.getAll().contains("\"handler\":\"TMF\""));
        assertFalse(output.getAll().contains(allowedPhone));
        verify(redis, atLeastOnce()).expire(eq(allowedPhone + ":ussdCanonicalRoute"), eq(15L), eq(TimeUnit.MINUTES));
    }

    @Test void splitTmfCallbackCanonicalizesAndParentOnlyContinuationRetainsCanonicalState() throws Exception {
        mvc.perform(post("/ussd").param("phone", allowedPhone).param("sessionId", "split-tmf")
            .param("serviceCode", "*7447*11#").param("ussdString", "10").header("Accept", "text/plain"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("TEXT ME FOOD FOUNDATION")));
        assertEquals("11*10", state.get(allowedPhone + ":ussdCanonicalRoute"));
        assertTrue(call(allowedPhone, "split-tmf", "*7447#", "1", "", "CONTINUE").contains("Enter 4-digit PIN"));
        assertEquals("11*10", state.get(allowedPhone + ":ussdCanonicalRoute"));
        verify(resolver, times(1)).resolveEntry(any(), anyBoolean(), anyBoolean());
        assertTrue(call(allowedPhone, "split-tmf", "*7447#", "0", "", "CONTINUE").startsWith("END "));
        assertFalse(state.containsKey(allowedPhone + ":ussdCanonicalRoute"));
    }

    @Test void probeRequiresConfiguredNumberAndNormalizesApprovedInternationalNumber() throws Exception {
        gateway.setAliasTestEnabled(true);
        assertTrue(call(ORDINARY_PHONE, "no-config", "*7447*1#", "", "", "BEGIN").contains("CITY BOY MOVEMENT"));
        gateway.setAliasTestNumbers(Set.of("+234" + ORDINARY_PHONE.substring(1)));
        assertEquals(UssdRouteProbe.MENU, call(ORDINARY_PHONE, "approved", "*7447*1#", "", "", "BEGIN"));
    }

    @Test void retransmittedEntryCannotManufactureProbeSuccess() throws Exception {
        enableProbe();
        gateway.setInputMode(UssdGatewayProperties.InputMode.CUMULATIVE);
        assertEquals(UssdRouteProbe.MENU, call(ORDINARY_PHONE, "duplicate", "*7447#", "7447*1", "", ""));
        assertEquals(UssdRouteProbe.MENU, call(ORDINARY_PHONE, "duplicate", "*7447#", "7447*1", "", ""));
        assertTrue(call(ORDINARY_PHONE, "duplicate", "*7447#", "7447*1*1", "", "").contains("successful"));
        call(ORDINARY_PHONE, "ambiguous", "*7447#", "1", "", "BEGIN");
        assertEquals(UssdRouteProbe.MENU, call(ORDINARY_PHONE, "ambiguous", "*7447#", "1", "", ""));
        assertTrue(call(ORDINARY_PHONE, "ambiguous", "*7447#", "1", "", "CONTINUE").contains("successful"));
        verifyNoInteractions(payment, sms, relay);
    }

    @Test void diagnosticsRedactDialogueAndUnrecognizedValues(CapturedOutput output) throws Exception {
        gateway.setDiagnosticsEnabled(true);
        call(ORDINARY_PHONE, "session-with-SECRET", "*7447*1#", "", "", "BEGIN");
        call(ORDINARY_PHONE, "session-with-SECRET", "*7447*1#", "SECRET_PIN", "", "CONTINUE");
        call(ORDINARY_PHONE, "secret-route", "SECRET_SERVICE", "SECRET_INPUT", "SECRET_TEXT", "");
        assertFalse(output.getAll().contains("SECRET_PIN"));
        assertFalse(output.getAll().contains("session-with-SECRET"));
        assertFalse(output.getAll().contains("SECRET_SERVICE"));
        assertFalse(output.getAll().contains("SECRET_INPUT"));
        assertFalse(output.getAll().contains("SECRET_TEXT"));
        assertFalse(output.getAll().contains(ORDINARY_PHONE));
        assertTrue(output.getAll().contains("redisStateKeys"));
    }
}
