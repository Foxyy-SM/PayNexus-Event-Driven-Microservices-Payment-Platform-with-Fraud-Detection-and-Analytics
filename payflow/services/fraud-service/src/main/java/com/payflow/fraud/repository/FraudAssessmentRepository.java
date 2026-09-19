package com.payflow.fraud.repository;

import com.payflow.fraud.domain.FraudAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface FraudAssessmentRepository extends JpaRepository<FraudAssessment, UUID> {
    Optional<FraudAssessment> findByPaymentId(UUID paymentId);
    List<FraudAssessment> findByDecisionOrderByCreatedAtAsc(String decision);
}
