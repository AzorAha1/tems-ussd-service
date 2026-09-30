package com.example.tems.Tems.ussd;

import com.example.tems.Tems.controller.ussdcontroller;
import com.example.tems.Tems.client.CbmUssdRelayClient;
import com.example.tems.Tems.service.Smsservice;
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
    private static final String ORDINARY_PHONE = "08000000000";

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
        ussdcontroller controller = new ussdcontroller(null, null, null, null, null, null, null,
            null, null, mock(Smsservice.class), null, null, relay);
        gateway = new UssdGatewayProperties();
        gateway.getMessageTypes().put("BEGIN", UssdInboundRequest.SessionEvent.BEGIN);
        gateway.getMessageTypes().put("CONTINUE", UssdInboundRequest.SessionEvent.CONTINUE);
        gateway.getMessageTypes().put("END", UssdInboundRequest.SessionEvent.END);
        ReflectionTestUtils.setField(controller, "redisTemplate", redis);
        ReflectionTestUtils.setField(controller, "gateway", gateway);
        Set<String> allowed = (Set<String>) ReflectionTestUtils.getField(ussdcontroller.class, "TMF_TEST_PHONE_NUMBERS");
        allowedPhone = allowed.iterator().next();
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
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

    @Test void ordinaryReplyOneDoesNotSelectCbm() throws Exception {
        call(ORDINARY_PHONE, "s1", "*7447#", "", "", "");
        call(ORDINARY_PHONE, "s1", "*7447#", "1", "", "");
        assertNull(state.get(ORDINARY_PHONE + ":cbmFlow"));
        assertEquals("TEMS", state.get(ORDINARY_PHONE + ":ussdRoute"));
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
}
