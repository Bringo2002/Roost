package com.roost.dto;

import com.roost.model.UserReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for reporting a user: why, and an optional note. */
public class ReportUserRequestDto {

    @NotNull(message = "Reason is required")
    private UserReport.Reason reason;

    @Size(max = UserReport.MAX_NOTE_LENGTH, message = "Note cannot exceed 500 characters")
    private String note;

    public ReportUserRequestDto() {}

    public UserReport.Reason getReason() {
        return reason;
    }

    public void setReason(UserReport.Reason reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
