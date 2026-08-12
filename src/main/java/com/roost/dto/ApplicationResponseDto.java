package com.roost.dto;

import com.roost.model.Application;
import java.time.LocalDateTime;

/**
 * Response shape for all four ApplicationController endpoints. Deliberately
 * has no applicant field at all -- confirmed against the frontend
 * (lib/models/application.dart, lib/pages/landlord/landlord_dashboard_page.dart)
 * that Application.applicant is never read anywhere; the landlord dashboard
 * displays fullName (captured directly on the application) instead, and
 * nothing triggers contact from the embedded User object. Application
 * already carries sensitive fields of its own (nationalId, monthlyIncome)
 * that ARE the intended payload here -- those aren't a leak, they're the
 * whole point of a rental application -- but there's no reason to also drag
 * along the applicant's full account (email, role, savedPropertyIds) or,
 * via property.owner, a second unrelated leak of the property owner's
 * email. property routes through the existing PropertyResponseDto for the
 * same reason.
 */
public class ApplicationResponseDto {

    private final Long id;
    private final PropertyResponseDto property;
    private final String fullName;
    private final String nationalId;
    private final String employmentStatus;
    private final Double monthlyIncome;
    private final String status;
    private final LocalDateTime createdAt;

    public ApplicationResponseDto(Application a) {
        this.id = a.getId();
        this.property = PropertyResponseDto.from(a.getProperty());
        this.fullName = a.getFullName();
        this.nationalId = a.getNationalId();
        this.employmentStatus = a.getEmploymentStatus();
        this.monthlyIncome = a.getMonthlyIncome();
        this.status = a.getStatus();
        this.createdAt = a.getCreatedAt();
    }

    public static ApplicationResponseDto from(Application a) {
        return a == null ? null : new ApplicationResponseDto(a);
    }

    public static java.util.List<ApplicationResponseDto> from(java.util.List<Application> apps) {
        return apps.stream().map(ApplicationResponseDto::new).toList();
    }

    public Long getId() { return id; }
    public PropertyResponseDto getProperty() { return property; }
    public String getFullName() { return fullName; }
    public String getNationalId() { return nationalId; }
    public String getEmploymentStatus() { return employmentStatus; }
    public Double getMonthlyIncome() { return monthlyIncome; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
