package com.roost.service;

import com.roost.dto.ListingEventBatchRequest;
import com.roost.dto.ListingEventBatchResponse;
import com.roost.model.ListingEvent;
import com.roost.model.Property;
import com.roost.model.User;
import com.roost.repository.ListingEventRepository;
import com.roost.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records tenant interactions with listings (the raw signal for ranking).
 * Existence of the listings is checked with one ids-only query for the
 * whole batch, and rows are attached by reference, so a batch costs two
 * statements regardless of size.
 */
@Service
public class ListingEventService {

    private final ListingEventRepository listingEventRepository;
    private final PropertyRepository propertyRepository;

    public ListingEventService(ListingEventRepository listingEventRepository,
                               PropertyRepository propertyRepository) {
        this.listingEventRepository = listingEventRepository;
        this.propertyRepository = propertyRepository;
    }

    /**
     * Stores one event per item whose listing exists, stamped with the
     * server clock and attributed to {@code user}.
     *
     * @return how many events were stored and how many were dropped
     *         because their listing does not exist
     */
    @Transactional
    public ListingEventBatchResponse record(User user, List<ListingEventBatchRequest.Item> items) {
        if (items.isEmpty()) {
            return new ListingEventBatchResponse(0, 0);
        }

        Set<Long> requestedIds = new HashSet<>();
        for (ListingEventBatchRequest.Item item : items) {
            requestedIds.add(item.propertyId());
        }
        Set<Long> existingIds = new HashSet<>(propertyRepository.findExistingIds(requestedIds));

        Instant now = Instant.now();
        Map<Long, Property> references = new HashMap<>();
        List<ListingEvent> rows = new ArrayList<>();
        int dropped = 0;
        for (ListingEventBatchRequest.Item item : items) {
            if (!existingIds.contains(item.propertyId())) {
                dropped++;
                continue;
            }
            Property property = references.computeIfAbsent(
                    item.propertyId(), propertyRepository::getReferenceById);
            rows.add(new ListingEvent(property, user.getId(), null, item.type(), now));
        }
        listingEventRepository.saveAll(rows);
        return new ListingEventBatchResponse(rows.size(), dropped);
    }
}
