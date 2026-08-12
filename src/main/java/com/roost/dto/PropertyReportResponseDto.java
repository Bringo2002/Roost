package com.roost.dto;

import com.roost.model.PropertyReport;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin-facing view of a report. Exists because PropertyReport has two
 * @ManyToOne entity references (property, reportedBy) that Jackson would
 * otherwise cascade into on a raw-entity response -- property.owner in
 * particular re-leaked the full owner User (see PropertyResponseDto)
 * through this second path even after PropertyController was fixed.
 * Routes the nested property through PropertyResponseDto for the same
 * reason it exists there, and the reporter through ReporterDto (which,
 * unlike PropertyOwnerDto, keeps email -- admins reviewing reports need
 * to recognize repeat/abusive reporters).
 */
public class PropertyReportResponseDto {

    private final Long id;
    private final String reason;
    private final String details;
    private final LocalDateTime createdAt;
    private final ReporterDto reportedBy;
    private final PropertyResponseDto property;

    public PropertyReportResponseDto(PropertyReport r) {
        this.id = r.getId();
        this.reason = r.getReason();
        this.details = r.getDetails();
        this.createdAt = r.getCreatedAt();
        this.reportedBy = ReporterDto.from(r.getReportedBy());
        this.property = PropertyResponseDto.from(r.getProperty());
    }

    public static PropertyReportResponseDto from(PropertyReport r) {
        return r == null ? null : new PropertyReportResponseDto(r);
    }

    public static List<PropertyReportResponseDto> from(List<PropertyReport> reports) {
        return reports.stream().map(PropertyReportResponseDto::new).toList();
    }

    public Long getId() {
        return id;
    }

    public String getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ReporterDto getReportedBy() {
        return reportedBy;
    }

    public PropertyResponseDto getProperty() {
        return property;
    }
}
