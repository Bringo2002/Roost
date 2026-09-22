package com.roost.dto;

import com.roost.model.PropertyReport;

import java.time.LocalDateTime;

public class ReportSubmissionResponseDto {

    private final Long reportId;
    private final Long propertyId;
    private final String reason;
    private final String message;
    private final LocalDateTime createdAt;

    public ReportSubmissionResponseDto(Long reportId, Long propertyId, String reason, String message, LocalDateTime createdAt) {
        this.reportId = reportId;
        this.propertyId = propertyId;
        this.reason = reason;
        this.message = message;
        this.createdAt = createdAt;
    }

    public static ReportSubmissionResponseDto from(PropertyReport report, String message) {
        return new ReportSubmissionResponseDto(
                report.getId(),
                report.getProperty() != null ? report.getProperty().getId() : null,
                report.getReason(),
                message,
                report.getCreatedAt()
        );
    }

    public Long getReportId() {
        return reportId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public String getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
