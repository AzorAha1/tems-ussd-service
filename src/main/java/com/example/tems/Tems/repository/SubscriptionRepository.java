package com.example.tems.Tems.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.tems.Tems.model.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    // Custom query methods can be added here if needed
    // For example, to find subscriptions by phone number or status
    Optional<Subscription> findByPhoneNumber(String phoneNumber);
    List<Subscription> findByStatus(String status);
    List<Subscription> findByPhoneNumberAndStatus(String phoneNumber, String status);
    
}
