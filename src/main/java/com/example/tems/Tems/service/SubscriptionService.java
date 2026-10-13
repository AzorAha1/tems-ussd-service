package com.example.tems.Tems.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.tems.Tems.model.Subscription;
import com.example.tems.Tems.repository.SubscriptionRepository;

/**
 * Records who has paid. One row per attempt, using the existing "status" text column, so no schema change:
 *   active          - paid, valid until expiresat
 *   pending         - we asked HML to charge, waiting for the notification
 *   failed_balance  - HML said the user did not have enough airtime
 *   failed_error    - HML reported another charging error
 *   expired         - finished / replaced
 */
@Service
public class SubscriptionService {

    public static final String ACTIVE = "active";
    public static final String EXPIRED = "expired";
    public static final String PENDING = "pending";
    public static final String FAILED_BALANCE = "failed_balance";
    public static final String FAILED_ERROR = "failed_error";

    /** How long a recorded failure is remembered (minutes). A new dial clears it and tries again. */
    private static final int FAILURE_MEMORY_MINUTES = 10;

    public enum Failure { NONE, BALANCE, ERROR }

    private final SubscriptionRepository subscriptionRepository;

    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /** A paid session. Ends any earlier active session and any pending or failed attempt. */
    public void activateSession(String phoneNumber, int minutes) {
        deactivateSession(phoneNumber);
        clearAttempts(phoneNumber);
        saveRow(phoneNumber, ACTIVE, minutes);
    }

    // deactivateSession method to set the status of a subscription to expired
    public void deactivateSession(String phoneNumber) {
        expireAll(phoneNumber, ACTIVE);
    }

    /** Old behaviour: any error means "no active session". */
    public boolean hasActiveSession(String phoneNumber) {
        try {
            return hasActiveSessionStrict(phoneNumber);
        } catch (Exception e) {
            System.err.println("Error checking active session: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Same check, but a database error is thrown instead of being read as "not paid".
     * The payment gate needs this: treating an outage as "unpaid" would start a second charge for someone who already paid.
     */
    public boolean hasActiveSessionStrict(String phoneNumber) {
        return liveRowExists(phoneNumber, ACTIVE);
    }

    public void markPending(String phoneNumber, int minutes) {
        saveRow(phoneNumber, PENDING, minutes);
    }

    public boolean hasPending(String phoneNumber) {
        return liveRowExists(phoneNumber, PENDING);
    }

    /** HML reported that the charge failed. insufficientBalance=true means "not enough airtime". */
    public void markFailed(String phoneNumber, boolean insufficientBalance) {
        expireAll(phoneNumber, PENDING);
        saveRow(phoneNumber, insufficientBalance ? FAILED_BALANCE : FAILED_ERROR, FAILURE_MEMORY_MINUTES);
    }

    public Failure recentFailure(String phoneNumber) {
        if (liveRowExists(phoneNumber, FAILED_BALANCE)) return Failure.BALANCE;
        if (liveRowExists(phoneNumber, FAILED_ERROR)) return Failure.ERROR;
        return Failure.NONE;
    }

    /** Forget pending and failed attempts (before a new attempt, or after a successful payment). */
    public void clearAttempts(String phoneNumber) {
        expireAll(phoneNumber, PENDING);
        expireAll(phoneNumber, FAILED_BALANCE);
        expireAll(phoneNumber, FAILED_ERROR);
    }

    // ---- helpers ----

    private boolean liveRowExists(String phoneNumber, String status) {
        List<Subscription> rows = subscriptionRepository.findByPhoneNumberAndStatus(phoneNumber, status);
        LocalDateTime now = LocalDateTime.now();
        boolean live = false;
        for (Subscription row : rows) {
            if (row.getExpiresat() != null && row.getExpiresat().isAfter(now)) {
                live = true;
            } else {
                row.setStatus(EXPIRED);
                subscriptionRepository.save(row);
            }
        }
        return live;
    }

    private void expireAll(String phoneNumber, String status) {
        List<Subscription> rows = subscriptionRepository.findByPhoneNumberAndStatus(phoneNumber, status);
        for (Subscription row : rows) {
            row.setStatus(EXPIRED);
            subscriptionRepository.save(row);
        }
    }

    private void saveRow(String phoneNumber, String status, int minutes) {
        Subscription subscription = new Subscription();
        subscription.setPhoneNumber(phoneNumber);
        subscription.setStatus(status);
        subscription.setCreatedat(LocalDateTime.now());
        subscription.setExpiresat(LocalDateTime.now().plusMinutes(minutes));
        subscriptionRepository.save(subscription);
    }
}