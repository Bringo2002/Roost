package com.roost.exception;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a listing fails the publication policy. Extends
 * {@link ApiException} so the response keeps the same {@code error} message
 * existing clients already display, while {@link GlobalExceptionHandler} adds
 * the structured {@code violations} list so a client can say exactly what to fix.
 */
public class PublicationPolicyException extends ApiException {

    private final List<PublicationViolation> violations;

    /**
     * @param violations the failed requirements; must not be empty
     */
    public PublicationPolicyException(List<PublicationViolation> violations) {
        super(HttpStatus.BAD_REQUEST, summarize(violations));
        this.violations = List.copyOf(violations);
    }

    /** @return the failed requirements, in policy order */
    public List<PublicationViolation> getViolations() {
        return violations;
    }

    private static String summarize(List<PublicationViolation> violations) {
        return violations.stream()
                .map(PublicationViolation::message)
                .collect(Collectors.joining(" "));
    }
}
