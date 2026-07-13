package com.roost.service;

import com.roost.model.Property;
import com.roost.model.VerificationRecord;
import com.roost.repository.PropertyRepository;
import com.roost.repository.VerificationRepository;
import com.roost.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VerificationService {

    private final VerificationRepository verificationRepository;
    private final PropertyRepository propertyRepository;

    public VerificationService(VerificationRepository verificationRepository, PropertyRepository propertyRepository) {
        this.verificationRepository = verificationRepository;
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public VerificationRecord payHoldingFee(Long propertyId, String tenantPhone, User tenant) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));

        if (property.isHoldingFeePaid()) {
            throw new RuntimeException("Property already secured");
        }

        // Create record
        VerificationRecord record = new VerificationRecord();
        record.setPropertyId(propertyId);
        record.setTenantPhone(tenantPhone);
        record.setAmountPaid(2000.0); // Flat fee
        record.setMpesaReceiptNumber("MP" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        record.setStatus("SECURED");
        record.setTenant(tenant);
        
        verificationRepository.save(record);

        // Update property
        property.setHoldingFeePaid(true);
        propertyRepository.save(property);

        return record;
    }

    public List<VerificationRecord> getTenantPayments(User tenant) {
        return verificationRepository.findByTenant(tenant);
    }

    public List<VerificationRecord> getPropertyVerifications(Long propertyId) {
        return verificationRepository.findByPropertyId(propertyId);
    }
}
