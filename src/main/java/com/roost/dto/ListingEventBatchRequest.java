package com.roost.dto;

import com.roost.model.ListingEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A batch of listing interactions reported by the client (impressions are
 * far too frequent to send one request each). The timestamp is deliberately
 * not part of the contract: the server stamps events on arrival, so a
 * client cannot backdate or forge when something happened.
 */
public record ListingEventBatchRequest(
        @NotEmpty @Size(max = ListingEventBatchRequest.MAX_EVENTS) List<@Valid @NotNull Item> events) {

    /** Largest batch accepted in one request. */
    public static final int MAX_EVENTS = 50;

    /** One interaction: what happened ({@link ListingEventType}) to which listing. */
    public record Item(@NotNull Long propertyId, @NotNull ListingEventType type) {}
}
