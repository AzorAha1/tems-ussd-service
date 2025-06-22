package com.example.tems.Tems.service;
import java.time.LocalDateTime;
import java.util.List;
import com.example.tems.Tems.model.Subscription;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.tems.Tems.repository.SubscriptionRepository;

@Service
public class SubscriptionService {
    
    private final SubscriptionRepository subscriptionRepository;
    
    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public void activateSession(String phoneNumber, int minutes) {
         // Deactivate any existing session for the phone number
        deactivateSession(phoneNumber);
        // Create a new subscription with the provided phone number and minutes
        Subscription subscription = new Subscription();
        subscription.setPhoneNumber(phoneNumber);
        subscription.setStatus("active");
        subscription.setCreatedat(LocalDateTime.now());
        subscription.setExpiresat(LocalDateTime.now().plusMinutes(minutes));
        subscriptionRepository.save(subscription);
    }
    // deactivateSession method to set the status of a subscription to expired
    public void deactivateSession(String phoneNumber) {
       List<Subscription> subscriptions = subscriptionRepository.findByPhoneNumberAndStatus(phoneNumber, "active");
       for (Subscription subscription : subscriptions) {
           subscription.setStatus("expired");
           subscriptionRepository.save(subscription);
       }
    }
    public boolean hasActiveSession(String phoneNumber) {
        // get all active subscriptions for a given phone number
        try {
            List<Subscription> activeSubscriptions = subscriptionRepository.findByPhoneNumberAndStatus(phoneNumber, "active");
            // Check if there are any active subscriptions
            LocalDateTime now = LocalDateTime.now();
            for (Subscription subscription : activeSubscriptions) {
                if (subscription.getExpiresat().isAfter(now)) {
                    return true; // Active session exists
                } else {
                    // If the subscription has expired, deactivate it
                    subscription.setStatus("expired");
                    subscriptionRepository.save(subscription);
                }
            }
            return false; // No active session found
            
        } catch (Exception e) {
            System.err.println("Error checking active session: " + e.getMessage());
            return false; // In case of any error, assume no active session
        }


    }
}
