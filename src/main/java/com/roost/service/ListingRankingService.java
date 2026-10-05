package com.roost.service;

import com.roost.model.ListingEventType;
import com.roost.model.ListingRankScore;
import com.roost.repository.ListingEventRepository;
import com.roost.repository.ListingRankScoreRepository;
import com.roost.repository.PropertyRepository;
import com.roost.service.ListingRankingFormula.Result;
import com.roost.service.ListingRankingFormula.Signals;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recomputes the stored ranking score of every visible listing: gathers each
 * listing's attributes and its event counts over a trailing window, feeds
 * them to {@link ListingRankingFormula}, and writes the result to
 * {@link ListingRankScore}. A scheduled task calls this; keeping the work
 * here (not in the task) lets it be tested with a fixed clock.
 *
 * <p>Cost is a fixed number of statements however many listings there are:
 * one grouped event aggregate, one risk-flag aggregate, one attribute scan,
 * one load of existing scores, and batched writes.
 */
@Service
public class ListingRankingService {

    /** Scores are written this many at a time so one huge transaction does not build up. */
    static final int WRITE_CHUNK_SIZE = 500;

    /**
     * Age assumed for a listing with no {@code listedAt} (data from before the
     * field existed): old enough that it earns no freshness advantage over
     * listings whose age is actually known.
     */
    static final double UNKNOWN_AGE_DAYS = 30.0;

    private final PropertyRepository propertyRepository;
    private final ListingEventRepository listingEventRepository;
    private final ListingRankScoreRepository listingRankScoreRepository;
    private final int windowDays;

    public ListingRankingService(
            PropertyRepository propertyRepository,
            ListingEventRepository listingEventRepository,
            ListingRankScoreRepository listingRankScoreRepository,
            @Value("${roost.ranking.window-days:7}") int windowDays) {
        if (windowDays < 1) {
            throw new IllegalArgumentException("roost.ranking.window-days must be at least 1");
        }
        this.propertyRepository = propertyRepository;
        this.listingEventRepository = listingEventRepository;
        this.listingRankScoreRepository = listingRankScoreRepository;
        this.windowDays = windowDays;
    }

    /**
     * Recomputes and stores scores for all published, available listings.
     * Safe to run on more than one instance at once: each run writes the same
     * deterministic values for the same inputs.
     *
     * @return how many listings were scored
     */
    @Transactional
    public int recompute(Clock clock) {
        Instant now = clock.instant();
        LocalDateTime nowLocal = LocalDateTime.now(clock);

        List<PropertyRepository.RankingInput> inputs = propertyRepository.findRankingInputs();
        if (inputs.isEmpty()) {
            return 0;
        }

        Map<Long, EventTotals> events = new HashMap<>();
        Instant since = now.minus(Duration.ofDays(windowDays));
        for (ListingEventRepository.EventCount c : listingEventRepository.countByPropertyAndTypeSince(since)) {
            events.computeIfAbsent(c.getPropertyId(), id -> new EventTotals())
                    .add(c.getEventType(), c.getEventCount());
        }

        Map<Long, Long> riskFlags = propertyRepository.countRiskFlagsOfPublished().stream()
                .collect(Collectors.toMap(
                        PropertyRepository.RiskFlagCount::getPropertyId,
                        PropertyRepository.RiskFlagCount::getFlagCount));

        // One row per scored listing, so loading them all is a single statement; an IN (...)
        // over every listing id would hit the database's bind-parameter limit on a large catalogue.
        Map<Long, ListingRankScore> existing = listingRankScoreRepository.findAll().stream()
                .collect(Collectors.toMap(ListingRankScore::getPropertyId, Function.identity()));

        List<ListingRankScore> toSave = new ArrayList<>(Math.min(inputs.size(), WRITE_CHUNK_SIZE));
        for (PropertyRepository.RankingInput in : inputs) {
            Result result = ListingRankingFormula.score(
                    signalsFor(in, events.get(in.getId()), riskFlags.getOrDefault(in.getId(), 0L), nowLocal));
            ListingRankScore row = existing.get(in.getId());
            if (row == null) {
                row = new ListingRankScore(
                        propertyRepository.getReferenceById(in.getId()),
                        result.score(), result.exposureBoost(), now);
            } else {
                row.update(result.score(), result.exposureBoost(), now);
            }
            toSave.add(row);
            if (toSave.size() == WRITE_CHUNK_SIZE) {
                listingRankScoreRepository.saveAll(toSave);
                toSave = new ArrayList<>(WRITE_CHUNK_SIZE);
            }
        }
        if (!toSave.isEmpty()) {
            listingRankScoreRepository.saveAll(toSave);
        }
        return inputs.size();
    }

    private static Signals signalsFor(PropertyRepository.RankingInput in, EventTotals events,
                                      long riskFlagCount, LocalDateTime nowLocal) {
        double ageDays = UNKNOWN_AGE_DAYS;
        if (in.getListedAt() != null) {
            // A listing dated in the future (clock skew) counts as brand new, not negative-aged.
            ageDays = Math.max(0.0, Duration.between(in.getListedAt(), nowLocal).toSeconds() / 86_400.0);
        }
        EventTotals e = events != null ? events : new EventTotals();
        return new Signals(
                ageDays,
                Boolean.TRUE.equals(in.getPhotoApproved()),
                Boolean.TRUE.equals(in.getVerified()),
                Boolean.TRUE.equals(in.getGpsVerified()),
                Boolean.TRUE.equals(in.getCommunityVerified()),
                (int) Math.min(riskFlagCount, Integer.MAX_VALUE),
                e.impressions, e.clicks, e.saves, e.chats, e.applications);
    }

    /** Per-listing event totals for the window; unknown types are ignored, not fatal. */
    private static final class EventTotals {
        long impressions;
        long clicks;
        long saves;
        long chats;
        long applications;

        void add(String type, long count) {
            ListingEventType t;
            try {
                t = ListingEventType.valueOf(type);
            } catch (IllegalArgumentException unknownType) {
                return;
            }
            switch (t) {
                case IMPRESSION -> impressions += count;
                case CLICK -> clicks += count;
                case SAVE -> saves += count;
                case CHAT_STARTED -> chats += count;
                case APPLICATION -> applications += count;
            }
        }
    }
}
