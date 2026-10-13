package com.example.tems.Tems.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentGateTest {
    private static final String PHONE = "07072603735";
    private SubscriptionService subs;
    private AggregatorService aggregator;
    private PaymentGate gate;

    @BeforeEach void setup() {
        subs = mock(SubscriptionService.class);
        aggregator = mock(AggregatorService.class);
        gate = new PaymentGate(subs, aggregator);
        ReflectionTestUtils.setField(gate, "mode", "ON");
        ReflectionTestUtils.setField(gate, "waitMs", 300L);
        when(subs.recentFailure(anyString())).thenReturn(SubscriptionService.Failure.NONE);
        when(aggregator.initiate(anyString(), any())).thenReturn(AggregatorService.InitiateResult.STARTED);
    }

    @Test void switchedOffLetsEveryoneIn() {
        ReflectionTestUtils.setField(gate, "mode", "OFF");
        assertNull(gate.check(PHONE, "MTN"));
        verify(subs, never()).hasActiveSessionStrict(anyString());
        verify(aggregator, never()).initiate(anyString(), any());
    }

    @Test void paidUserGoesStraightInWithoutANewCharge() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(true);
        assertNull(gate.check(PHONE, "MTN"));
        verify(aggregator, never()).initiate(anyString(), any());
    }

    @Test void unpaidUserIsChargedAndLetInWhenThePaymentArrives() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false, true); // unpaid at first, paid when we check again
        assertNull(gate.check(PHONE, "MTN"));
        verify(subs).markPending(PHONE, 5);
        verify(aggregator).initiate(PHONE, "MTN");
    }

    @Test void notEnoughAirtimeEndsWithTheAirtimeMessage() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false);
        when(subs.recentFailure(PHONE)).thenReturn(SubscriptionService.Failure.BALANCE);
        assertEquals(PaymentGate.MSG_AIRTIME, gate.check(PHONE, "MTN"));
    }

    @Test void anotherChargingErrorEndsWithAGenericMessage() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false);
        when(subs.recentFailure(PHONE)).thenReturn(SubscriptionService.Failure.ERROR);
        assertEquals(PaymentGate.MSG_ERROR, gate.check(PHONE, "MTN"));
    }

    @Test void noNotificationInTimeAsksTheUserToDialAgain() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false);
        assertEquals(PaymentGate.MSG_PENDING, gate.check(PHONE, "MTN"));
    }

    @Test void aChargeAlreadyInFlightIsNeverStartedTwice() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false);
        when(subs.hasPending(PHONE)).thenReturn(true);
        assertEquals(PaymentGate.MSG_PENDING, gate.check(PHONE, "MTN"));
        verify(aggregator, never()).initiate(anyString(), any());
    }

    @Test void aFailedInitiateEndsWithAStartFailureAndClearsThePendingAttempt() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false);
        when(aggregator.initiate(anyString(), any())).thenReturn(AggregatorService.InitiateResult.FAILED);
        assertEquals(PaymentGate.MSG_START_FAILED, gate.check(PHONE, "MTN"));
        verify(subs, times(2)).clearAttempts(PHONE); // once before the attempt, once after it failed
    }

    @Test void aSkippedInitiateLetsTheUserInBecauseBillingIsNotConfigured() {
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false);
        when(aggregator.initiate(anyString(), any())).thenReturn(AggregatorService.InitiateResult.SKIPPED);
        assertNull(gate.check(PHONE, "MTN"));
        verify(subs, times(2)).clearAttempts(PHONE);
    }

    @Test void aDatabaseOutageLetsTheUserInByDefaultAndNeverStartsACharge() {
        when(subs.hasActiveSessionStrict(PHONE)).thenThrow(new IllegalStateException("db down"));
        assertNull(gate.check(PHONE, "MTN"));
        verify(aggregator, never()).initiate(anyString(), any());
    }

    @Test void aDatabaseOutageCanBeConfiguredToBlock() {
        ReflectionTestUtils.setField(gate, "failOpen", false);
        when(subs.hasActiveSessionStrict(PHONE)).thenThrow(new IllegalStateException("db down"));
        assertEquals(PaymentGate.MSG_UNAVAILABLE, gate.check(PHONE, "MTN"));
    }

    @Test void allowlistModeOnlyChargesListedNumbers() {
        ReflectionTestUtils.setField(gate, "mode", "ALLOWLIST");
        ReflectionTestUtils.setField(gate, "testNumbers", "08011111111, 08022222222");
        assertNull(gate.check(PHONE, "MTN"));                       // not listed: never charged
        verify(aggregator, never()).initiate(anyString(), any());
        when(subs.hasActiveSessionStrict("08011111111")).thenReturn(false, true);
        assertNull(gate.check("08011111111", "MTN"));               // listed: charged
        verify(aggregator).initiate("08011111111", "MTN");
    }

    @Test void shadowModeOnlyLogsAndNeverBlocksOrCharges() {
        ReflectionTestUtils.setField(gate, "mode", "SHADOW");
        when(subs.hasActiveSessionStrict(PHONE)).thenReturn(false);
        assertNull(gate.check(PHONE, "MTN"));
        verify(aggregator, never()).initiate(anyString(), any());
    }

    @Test void exemptNumbersNeverPay() {
        ReflectionTestUtils.setField(gate, "exemptNumbers", "07000000001, " + PHONE);
        assertNull(gate.check(PHONE, "MTN"));
        verify(aggregator, never()).initiate(anyString(), any());
    }
}