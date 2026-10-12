package com.example.tems.Tems.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tems.Tems.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AggregatorWebhookControllerTest {
    private static final String TEMS_IDENTITY = "PD-17625145556900";   // product 576 on HML
    private SubscriptionService subscriptions;
    private MockMvc mvc;

    @BeforeEach void setup() {
        subscriptions = mock(SubscriptionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AggregatorWebhookController(subscriptions)).build();
    }

    private ResultActions send(String json) throws Exception {
        return mvc.perform(post("/api/v1/tems/webhook/aggregator").contentType("application/json").content(json));
    }

    private static String body(String type, String identity, String phone) {
        String details = phone == null ? "" : ",\"details\":{\"phone\":\"" + phone + "\"}";
        return "{\"type\":\"" + type + "\",\"telco\":\"MTN\",\"product\":{\"id\":576,\"identity\":\"" + identity + "\"}" + details + "}";
    }

    @Test void newSubscriptionForTemsProductActivatesSession() throws Exception {
        send(body("SYNC_NOTIFICATION", TEMS_IDENTITY, "2347072603735")).andExpect(status().isOk());
        verify(subscriptions).activateSession("07072603735", 120);
    }

    @Test void renewalActivatesSessionToo() throws Exception {
        send(body("RENEWAL_NOTIFICATION", TEMS_IDENTITY, "2347072603735")).andExpect(status().isOk());
        verify(subscriptions).activateSession("07072603735", 120);
    }

    @Test void theOldWrongIdentitiesAreNoLongerAccepted() throws Exception {
        // the MTN subscription id and the 2022 "Prince" test product used to be the only values that passed
        send(body("SYNC_NOTIFICATION", "234102200008395", "2347072603735")).andExpect(status().isBadRequest());
        send(body("SYNC_NOTIFICATION", "PD-16618033881000", "2347072603735")).andExpect(status().isBadRequest());
        verifyNoInteractions(subscriptions);
    }

    @Test void chargeFailuresAreAcknowledgedWithoutActivating() throws Exception {
        send(body("INSUFFICIENT_BALANCE", TEMS_IDENTITY, "2347072603735")).andExpect(status().isOk());
        send(body("CHARGING_ERROR", TEMS_IDENTITY, "2347072603735")).andExpect(status().isOk());
        verifyNoInteractions(subscriptions);
    }

    @Test void typesWithoutAPhoneAreAcknowledgedNotCrashed() throws Exception {
        send(body("TELCO_RAW_SYNC", TEMS_IDENTITY, null)).andExpect(status().isOk());
        verifyNoInteractions(subscriptions);
    }

    @Test void successWithoutPhoneIsRejected() throws Exception {
        send(body("SYNC_NOTIFICATION", TEMS_IDENTITY, null)).andExpect(status().isBadRequest());
        verifyNoInteractions(subscriptions);
    }

    @Test void malformedPayloadIsA400NotA500() throws Exception {
        send("{\"type\":\"SYNC_NOTIFICATION\"}").andExpect(status().isBadRequest());
        send("{\"product\":{\"identity\":\"" + TEMS_IDENTITY + "\"}}").andExpect(status().isBadRequest());
        verifyNoInteractions(subscriptions);
    }
}