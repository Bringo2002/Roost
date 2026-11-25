package com.roost.service;

import com.roost.dto.ApplicationRequestDto;
import com.roost.dto.ListingEventBatchRequest;
import com.roost.exception.ApiException;
import com.roost.model.Application;
import com.roost.model.ListingEventType;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final PropertyService propertyService;
    private final ListingEventService listingEventService;

    public ApplicationService(ApplicationRepository applicationRepository, PropertyService propertyService,
                              ListingEventService listingEventService) {
        this.applicationRepository = applicationRepository;
        this.propertyService = propertyService;
        this.listingEventService = listingEventService;
    }

    public Application submitApplication(User applicant, ApplicationRequestDto request) {
        Property property = propertyService.getPropertyById(request.getPropertyId());

        if (applicationRepository.existsByPropertyAndApplicant(property, applicant)) {
            throw ApiException.badRequest("You have already applied for this property");
        }

        Application application = new Application();
        application.setProperty(property);
        application.setApplicant(applicant);
        application.setFullName(request.getFullName());
        application.setNationalId(request.getNationalId() != null ? request.getNationalId() : "");
        application.setEmploymentStatus(request.getEmploymentStatus() != null ? request.getEmploymentStatus() : "");
        application.setMonthlyIncome(request.getMonthlyIncome() != null ? request.getMonthlyIncome() : 0.0);
        application.setStatus("PENDING");
        application.setCreatedAt(LocalDateTime.now());

        Application saved = applicationRepository.save(application);

        // The ranking job counts APPLICATION events; nothing else records them.
        listingEventService.record(applicant, List.of(
                new ListingEventBatchRequest.Item(property.getId(), ListingEventType.APPLICATION)));
        return saved;
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
