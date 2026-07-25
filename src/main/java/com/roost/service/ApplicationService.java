package com.roost.service;

import com.roost.exception.ApiException;
import com.roost.model.Application;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final PropertyService propertyService;

    public ApplicationService(ApplicationRepository applicationRepository, PropertyService propertyService) {
        this.applicationRepository = applicationRepository;
        this.propertyService = propertyService;
    }

    public Application submitApplication(User applicant, Map<String, Object> payload) {
        Long propertyId = Long.valueOf(payload.get("propertyId").toString());
        Property property = propertyService.getPropertyById(propertyId);

        if (applicationRepository.existsByPropertyAndApplicant(property, applicant)) {
            throw ApiException.badRequest("You have already applied for this property");
        }

        Application application = new Application();
        application.setProperty(property);
        application.setApplicant(applicant);
        application.setFullName(payload.get("fullName").toString());
        application.setNationalId(payload.get("nationalId") != null ? payload.get("nationalId").toString() : "");
        application.setEmploymentStatus(payload.get("employmentStatus") != null ? payload.get("employmentStatus").toString() : "");
        application.setMonthlyIncome(payload.get("monthlyIncome") != null ? Double.parseDouble(payload.get("monthlyIncome").toString()) : 0.0);
        application.setStatus("PENDING");
        application.setCreatedAt(LocalDateTime.now());

        return applicationRepository.save(application);
    }

    public List<Application> getPropertyApplications(User user, Long propertyId) {
        requireLandlord(user, "Only landlords can view applications");

        Property property = propertyService.getPropertyById(propertyId);
        requireOwner(property, user, "You don't own this property");

        return applicationRepository.findByPropertyOrderByCreatedAtDesc(property);
    }

    public List<Application> getMyApplications(User applicant) {
        return applicationRepository.findByApplicantOrderByCreatedAtDesc(applicant);
    }

    public Application updateStatus(User user, Long applicationId, String newStatus) {
        requireLandlord(user, "Only landlords can update application status");

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
        requireOwner(app.getProperty(), user, "You don't own this property");

        if (!"APPROVED".equals(newStatus) && !"REJECTED".equals(newStatus)) {
            throw ApiException.badRequest("Status must be APPROVED or REJECTED");
        }

        app.setStatus(newStatus);
        return applicationRepository.save(app);
    }

    // -- helpers --------------------------------------------------------

    private void requireLandlord(User user, String errorMessage) {
        if (user.getRole() != Role.LANDLORD) {
            throw ApiException.forbidden(errorMessage);
        }
    }

    private void requireOwner(Property property, User user, String errorMessage) {
        if (property.getOwner() == null || !property.getOwner().getId().equals(user.getId())) {
            throw ApiException.forbidden(errorMessage);
        }
    }
}
