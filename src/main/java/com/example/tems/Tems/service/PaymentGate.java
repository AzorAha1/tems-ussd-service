package com.example.tems.Tems.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Decides whether a fresh dial may see the menu.
 *
 * check() returns null to let the caller in, or an "END ..." message to show instead of the menu.
 * Unpaid callers never see the menu.
 *
 * Flow for an unpaid caller:
 *   1. record a "pending" attempt, ask HML to start the charge (Initiate Subscription)
 *   2. wait a few seconds for HML's notification (it arrives at the webhook and writes to the database)
 *        paid                -> return null (the menu opens in the same dial)
 *        not enough airtime  -> "Not enough airtime to process your payment..."
 *        other charge error  -> generic payment error
 *        nothing yet         -> "Your payment is being processed. Please dial again in a minute."
 *
 * Rollout switch (ussd.payment.mode): OFF (default) | SHADOW (log only) | ALLOWLIST (only ussd.payment.test-numbers) | ON.
 */
@Service
public class PaymentGate {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentGate.class);

    public enum Mode { OFF, SHADOW, ALLOWLIST, ON }
    static final String MSG_PENDING = "END A N12 payment request is waiting on your phone. Please accept it within 2 minutes, then dial again.";

    static final String MSG_AIRTIME = "END Not enough airtime to process your payment. Please recharge and dial again.";
    static final String MSG_ERROR = "END We could not process your payment. Please try again later.";
    static final String MSG_START_FAILED = "END We could not start your payment. Please try again later.";
    static final String MSG_UNAVAILABLE = "END Service temporarily unavailable. Please try again.";

    private static final long POLL_MS = 400;

    @Value("${ussd.payment.mode:OFF}")
    private String mode = "OFF";
    /** Comma-separated national numbers (07012345678). Only these are charged in ALLOWLIST mode. */
    @Value("${ussd.payment.test-numbers:}")
    private String testNumbers = "";
    /** Comma-separated national numbers that never pay (staff, partners). Empty by default. */
    @Value("${ussd.payment.exempt-numbers:}")
    private String exemptNumbers = "";
    /** How long a dial waits for HML's notification before telling the user to dial again. */
    @Value("${ussd.payment.wait-ms:6000}")
    private long waitMs = 6000;
    /** How long a "pending" attempt blocks a second Initiate for the same number. */
    @Value("${ussd.payment.pending-minutes:5}")
    private int pendingMinutes = 5;
    /** If the payment check itself breaks (database down): true lets the caller in, false shows MSG_UNAVAILABLE. */
    @Value("${ussd.payment.fail-open:true}")
    private boolean failOpen = true;

    private final SubscriptionService subscriptions;
    private final AggregatorService aggregator;

    @Autowired
    public PaymentGate(SubscriptionService subscriptions, AggregatorService aggregator) {
        this.subscriptions = subscriptions;
        this.aggregator = aggregator;
    }

    /** @return null to show the menu, or an "END ..." message. */
    public String check(String phone, String telco) {
        Mode current = parseMode(mode);
        if (current == Mode.OFF) return null;
        if (listContains(exemptNumbers, phone)) return null;
        if (current == Mode.ALLOWLIST && !listContains(testNumbers, phone)) return null;

        try {
            if (subscriptions.hasActiveSessionStrict(phone)) return null;

            if (current == Mode.SHADOW) {
                LOG.info("payment_gate shadow unpaid phone={} would_initiate", mask(phone));
                return null;
            }

            if (subscriptions.hasPending(phone)) {
                SubscriptionService.Failure earlier = subscriptions.recentFailure(phone);
                if (earlier == SubscriptionService.Failure.BALANCE || earlier == SubscriptionService.Failure.ERROR) {
                    subscriptions.clearAttempts(phone); // shown once; the next dial starts a fresh attempt
                    LOG.info("payment_gate failure_from_earlier_dial reason={} phone={}", earlier, mask(phone));
                    return earlier == SubscriptionService.Failure.BALANCE ? MSG_AIRTIME : MSG_ERROR;
                }
                // A charge is already in flight for this number: never start a second one.
                LOG.info("payment_gate pending_exists phone={} waiting", mask(phone));
                return waitForOutcome(phone);
            }

            subscriptions.clearAttempts(phone);            // forget an earlier failure before trying again
            subscriptions.markPending(phone, pendingMinutes); // before Initiate, so a fast notification finds it
            AggregatorService.InitiateResult result = aggregator.initiate(phone, telco);

            if (result == AggregatorService.InitiateResult.SKIPPED) {
                // Payment is switched on here but the aggregator call is not (disabled, no key, unknown network).
                subscriptions.clearAttempts(phone);
                LOG.warn("payment_gate initiate_skipped phone={} letting_user_in", mask(phone));
                return null;
            }
            if (result == AggregatorService.InitiateResult.FAILED) {
                subscriptions.clearAttempts(phone);
                LOG.warn("payment_gate initiate_failed phone={}", mask(phone));
                return MSG_START_FAILED;
            }
            LOG.info("payment_gate initiated phone={} waiting_ms={}", mask(phone), waitMs);
            return waitForOutcome(phone);
        } catch (RuntimeException e) {
            LOG.error("payment_gate check_failed type={} fail_open={}", e.getClass().getSimpleName(), failOpen);
            return failOpen ? null : MSG_UNAVAILABLE;
        }
    }

    private String waitForOutcome(String phone) {
        long deadline = System.currentTimeMillis() + waitMs;
        while (true) {
            if (subscriptions.hasActiveSessionStrict(phone)) {
                LOG.info("payment_gate paid phone={}", mask(phone));
                return null; // paid: the menu opens in this same dial
            }
            SubscriptionService.Failure failure = subscriptions.recentFailure(phone);
            if (failure == SubscriptionService.Failure.BALANCE) {
                LOG.info("payment_gate failed reason=balance phone={}", mask(phone));
                subscriptions.clearAttempts(phone); // shown once
                return MSG_AIRTIME;
            }
            if (failure == SubscriptionService.Failure.ERROR) {
                LOG.info("payment_gate failed reason=error phone={}", mask(phone));
                subscriptions.clearAttempts(phone); // shown once
                return MSG_ERROR;
            }
            if (System.currentTimeMillis() >= deadline) {
                LOG.info("payment_gate still_pending phone={}", mask(phone));
                return MSG_PENDING;
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return MSG_PENDING;
            }
        }
    }

    // ---- helpers ----

    static Mode parseMode(String raw) {
        if (raw == null) return Mode.OFF;
        try {
            return Mode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("payment_gate unknown mode, treating as OFF");
            return Mode.OFF;
        }
    }

    static boolean listContains(String csv, String phone) {
        if (csv == null || csv.isBlank() || phone == null) return false;
        for (String entry : csv.split(",")) {
            if (entry.trim().equals(phone)) return true;
        }
        return false;
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 3);
    }
}