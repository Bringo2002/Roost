package com.roost.service;

import com.roost.model.Property;
import com.roost.model.VerificationRecord;
import com.roost.repository.PropertyRepository;
import com.roost.repository.VerificationRepository;
import com.roost.model.User;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Aggregates a landlord's dashboard stats: total listings, how many
     * have a holding fee secured, and total revenue collected across all
     * of them. Moved here from VerificationController, which had this
     * loop directly in the controller. Uses PropertyRepository directly
     * (already a dependency of this class) rather than adding a new
     * cross-service dependency on PropertyService for a single lookup.
     */
    public Map<String, Object> getLandlordStats(User landlord) {
        List<Property> properties = propertyRepository.findByOwner(landlord);
        int totalListings = properties.size();
        int totalSecured = 0;
        double totalRevenue = 0;

        for (Property p : properties) {
            if (p.isHoldingFeePaid()) {
                totalSecured++;
                for (VerificationRecord r : getPropertyVerifications(p.getId())) {
                    totalRevenue += r.getAmountPaid();
                }
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalListings", totalListings);
        stats.put("totalSecured", totalSecured);
        stats.put("totalRevenue", totalRevenue);
        return stats;
    }
}
