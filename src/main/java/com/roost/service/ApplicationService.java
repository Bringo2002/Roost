package com.roost.service;

import com.roost.dto.ApplicationRequestDto;
import com.roost.dto.ListingEventBatchRequest;
import com.roost.exception.ApiException;
import com.roost.model.Application;
import com.roost.model.ListingEventType;
import com.roost.model.NotificationType;
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
    private final ListingEventService listingEventService;
    private final FirebasePushService firebasePushService;

    public ApplicationService(ApplicationRepository applicationRepository, PropertyService propertyService,
                              ListingEventService listingEventService, FirebasePushService firebasePushService) {
        this.applicationRepository = applicationRepository;
        this.propertyService = propertyService;
        this.listingEventService = listingEventService;
        this.firebasePushService = firebasePushService;
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

        // Notify the landlord that someone is interested.
        if (property.getOwner() != null) {
            String listingName = (property.getTitle() != null && !property.getTitle().isBlank())
                    ? property.getTitle() : "your listing";
            String applicantName = (applicant.getName() != null && !applicant.getName().isBlank())
                    ? applicant.getName() : "Someone";
            firebasePushService.sendToUser(
                    property.getOwner(),
                    "New application",
                    applicantName + " just applied for " + listingName + ". Tap to review their details.",
                    Map.of("type", NotificationType.APPLICATION_RECEIVED.key(),
                           "propertyId", String.valueOf(property.getId()),
                           "applicationId", String.valueOf(saved.getId()))
            );
        }

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
        Application saved = applicationRepository.save(app);

        // Notify the tenant about the landlord's decision.
        User applicant = saved.getApplicant();
        if (applicant != null) {
            String listingName = (saved.getProperty().getTitle() != null && !saved.getProperty().getTitle().isBlank())
                    ? saved.getProperty().getTitle() : "the listing you applied for";
            if ("APPROVED".equals(newStatus)) {
                firebasePushService.sendToUser(
                        applicant,
                        "You've been approved",
                        "Great news — your application for " + listingName + " has been accepted. The landlord will be in touch with next steps.",
                        Map.of("type", NotificationType.APPLICATION_STATUS_UPDATE.key(),
                               "applicationId", String.valueOf(saved.getId()),
                               "status", "APPROVED")
                );
            } else {
                firebasePushService.sendToUser(
                        applicant,
                        "Application update",
                        "Unfortunately your application for " + listingName + " wasn't accepted this time. Don't worry — there are plenty of other great places on Roost.",
                        Map.of("type", NotificationType.APPLICATION_STATUS_UPDATE.key(),
                               "applicationId", String.valueOf(saved.getId()),
                               "status", "REJECTED")
                );
            }
        }

        return saved;
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
