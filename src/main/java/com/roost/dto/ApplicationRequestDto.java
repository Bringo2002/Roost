package com.roost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for submitting a rental application. Only the property and the
 * applicant's name are required; the other fields were already optional and
 * default to empty / zero when left out.
 */
public class ApplicationRequestDto {

    // The applications columns are plain varchar(255).
    static final int MAX_TEXT_LENGTH = 255;

    @NotNull(message = "propertyId is required")
    private Long propertyId;

    @NotBlank(message = "fullName is required")
    @Size(max = MAX_TEXT_LENGTH, message = "fullName cannot exceed 255 characters")
    private String fullName;

    @Size(max = MAX_TEXT_LENGTH, message = "nationalId cannot exceed 255 characters")
    private String nationalId;

    @Size(max = MAX_TEXT_LENGTH, message = "employmentStatus cannot exceed 255 characters")
    private String employmentStatus;

    @PositiveOrZero(message = "monthlyIncome cannot be negative")
    private Double monthlyIncome;

    public ApplicationRequestDto() {}

    public Long getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(Long propertyId) {
        this.propertyId = propertyId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public Double getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(Double monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }
}
