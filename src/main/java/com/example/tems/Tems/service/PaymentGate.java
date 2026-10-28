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
 * What happens for an unpaid caller:
 *   1. We record a "pending" attempt and ask HML to start the charge (Initiate Subscription).
 *   2. The dial ends at once with "a payment request was sent by SMS". MTN sends the user an SMS; the user replies 1.
 *   3. HML tells our webhook the payment succeeded (or failed). The webhook writes it to the database and sends a
 *      confirmation SMS.
 *   4. The user's next dial finds the payment and opens the menu. If the payment failed (for example not enough airtime),
 *      that dial shows the reason once, and the dial after it starts a fresh attempt.
 *
 * ussd.payment.wait-ms (default 0) is an optional short pause that lets a very fast answer open the menu in the same
 * dial. A person has to reply to the SMS first, so it normally helps nobody; leave it at 0.
 *
 * Rollout switch (ussd.payment.mode): OFF (default) | SHADOW (log only) | ALLOWLIST (only ussd.payment.test-numbers) | ON.
 */
@Service
public class PaymentGate {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentGate.class);

    public enum Mode { OFF, SHADOW, ALLOWLIST, ON }

        static final String MSG_PENDING = "END A N12 payment request was sent by SMS. Reply 1 to accept it, then dial *7447# again in a minute.";
    static final String MSG_AIRTIME = "END You do not have enough airtime for the N12 payment. Please recharge and dial *7447# again.";
    static final String MSG_ERROR = "END We could not complete your N12 payment. Please try again in a few minutes.";
    static final String MSG_START_FAILED = "END We could not send your N12 payment request. Please try again in a few minutes.";
    static final String MSG_UNKNOWN = "END Your N12 payment request may be delayed. If an SMS arrives, reply 1 to accept it. If not, dial *7447# again in a few minutes.";
    static final String MSG_UNAVAILABLE = "END Service temporarily unavailable. Please try again.";

    private static final long POLL_MS = 400;

    @Value("${ussd.payment.mode:OFF}")
    private String mode = "OFF";
    /** Comma-separated national numbers, no quotes (07012345678,08012345678). Only these are charged in ALLOWLIST mode. */
    @Value("${ussd.payment.test-numbers:}")
    private String testNumbers = "";
    /** Comma-separated national numbers that never pay (staff, partners). Empty by default. */
    @Value("${ussd.payment.exempt-numbers:}")
    private String exemptNumbers = "";
    /** Optional pause for a very fast answer. 0 = answer at once. */
    @Value("${ussd.payment.wait-ms:0}")
    private long waitMs = 0;
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

            // A failure that arrived since the last dial is shown once, before anything else. This must come BEFORE the
            // pending check: when a charge fails the webhook ends the pending record, so "pending" is already false.
            SubscriptionService.Failure earlier = subscriptions.recentFailure(phone);
            if (earlier == SubscriptionService.Failure.BALANCE || earlier == SubscriptionService.Failure.ERROR) {
                subscriptions.clearAttempts(phone); // shown once; the next dial starts a fresh attempt
                LOG.info("payment_gate failure_from_earlier_dial reason={} phone={}", earlier, mask(phone));
                return earlier == SubscriptionService.Failure.BALANCE ? MSG_AIRTIME : MSG_ERROR;
            }

            if (subscriptions.hasPending(phone)) {
                // A charge is already in flight for this number: never start a second one.
                LOG.info("payment_gate pending_exists phone={}", mask(phone));
                return waitForOutcome(phone);
            }

            subscriptions.clearAttempts(phone);               // forget stale attempts before trying again
            subscriptions.markPending(phone, pendingMinutes); // before Initiate, so a fast notification finds it
            AggregatorService.InitiateResult result = aggregator.initiate(phone, telco);

            if (result == AggregatorService.InitiateResult.SKIPPED) {
                // Payment is switched on here but the aggregator call is not (disabled, no key, unknown network).
                subscriptions.clearAttempts(phone);
                LOG.warn("payment_gate initiate_skipped phone={} letting_user_in", mask(phone));
                return null;
            }
            // Written by name so this file compiles with an AggregatorService that does not have UNKNOWN yet.
            // UNKNOWN = the request was sent but no answer came back (timeout), so HML may have processed it: keep the
            // pending attempt (no second request right away) and tell the user what to look for.
            if ("UNKNOWN".equals(String.valueOf(result))) {
                LOG.warn("payment_gate initiate_unknown phone={} keeping_pending", mask(phone));
                return MSG_UNKNOWN;
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