package com.example.tems.Tems.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tems.Tems.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AggregatorWebhookSecurityTest {
    private static final String URL = "/api/v1/tems/webhook/aggregator";
    private static final String BODY = "{\"type\":\"SYNC_NOTIFICATION\",\"product\":{\"identity\":\"PD-17625145556900\"},\"details\":{\"phone\":\"2347072603735\"}}";
    private static final String SECRET = "s3cr3t-key-123";
    private static final String HML_IP = "91.109.117.92";
    private SubscriptionService subscriptions;
    private AggregatorWebhookController controller;
    private MockMvc mvc;

    @BeforeEach void setup() {
        subscriptions = mock(SubscriptionService.class);
        controller = new AggregatorWebhookController(subscriptions);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.contentType("application/json").content(BODY));
    }

    @Test void nothingConfiguredAcceptsEveryone() throws Exception {
        send(post(URL)).andExpect(status().isOk());
        verify(subscriptions).activateSession("07072603735", 1440);
    }

    @Test void withASecretAMissingOrWrongKeyIsRejectedBeforeAnythingIsRecorded() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
        send(post(URL)).andExpect(status().isUnauthorized());
        send(post(URL + "?key=wrong")).andExpect(status().isUnauthorized());
        send(post(URL).header("X-Webhook-Key", "wrong")).andExpect(status().isUnauthorized());
        verify(subscriptions, never()).activateSession(anyString(), anyInt());
    }

    @Test void withASecretTheRightKeyInTheUrlOrTheHeaderIsAccepted() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
        send(post(URL + "?key=" + SECRET)).andExpect(status().isOk());
        send(post(URL).header("X-Webhook-Key", SECRET)).andExpect(status().isOk());
        verify(subscriptions, times(2)).activateSession("07072603735", 1440);
    }

    @Test void withAnIpListOnlyListedAddressesAreAccepted() throws Exception {
        ReflectionTestUtils.setField(controller, "allowedIps", HML_IP + ", 10.0.0.5");
        send(post(URL).header("X-Real-IP", "6.6.6.6")).andExpect(status().isForbidden());
        send(post(URL)).andExpect(status().isForbidden());                       // no proxy header: 127.0.0.1 is not listed
        send(post(URL).header("X-Real-IP", HML_IP)).andExpect(status().isOk());
        send(post(URL).header("X-Forwarded-For", HML_IP + ", 152.233.12.245")).andExpect(status().isOk());
        verify(subscriptions, times(2)).activateSession("07072603735", 1440);
    }

    @Test void whenBothAreSetBothMustPass() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", SECRET);
        ReflectionTestUtils.setField(controller, "allowedIps", HML_IP);
        send(post(URL + "?key=" + SECRET).header("X-Real-IP", "6.6.6.6")).andExpect(status().isForbidden());
        send(post(URL).header("X-Real-IP", HML_IP)).andExpect(status().isUnauthorized());
        send(post(URL + "?key=" + SECRET).header("X-Real-IP", HML_IP)).andExpect(status().isOk());
        verify(subscriptions, times(1)).activateSession("07072603735", 1440);
    }
}