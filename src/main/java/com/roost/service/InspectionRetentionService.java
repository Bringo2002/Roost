package com.roost.service;

import com.roost.model.MoveInInspection;
import com.roost.repository.MoveInInspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Deletes move-in inspection records once they are {@value #RETENTION_MONTHS}
 * months old, counted from completion (or from creation, for one never
 * completed). The retention period is stated in the privacy policy.
 *
 * <p>An inspection is kept this long, with the tenant's name on it even after
 * their account is erased, because the landlord may need it to settle a
 * deposit dispute at the end of a tenancy.
 */
@Service
public class InspectionRetentionService {

    /** How long an inspection is kept after it was completed. */
    static final int RETENTION_MONTHS = 24;

    private final MoveInInspectionRepository inspectionRepository;

    public InspectionRetentionService(MoveInInspectionRepository inspectionRepository) {
        this.inspectionRepository = inspectionRepository;
    }

    /**
     * Deletes every inspection past its retention period.
     *
     * @return how many were deleted
     */
    @Transactional
    public int deleteExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(RETENTION_MONTHS);
        List<MoveInInspection> expired = inspectionRepository.findExpired(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }
        inspectionRepository.deleteAll(expired);
        return expired.size();
    }
}
