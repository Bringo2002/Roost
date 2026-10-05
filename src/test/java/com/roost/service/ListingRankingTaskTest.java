package com.roost.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingRankingTaskTest {

    @Mock
    private ListingRankingService rankingService;

    @Test
    @DisplayName("a run triggers one recompute")
    void runRecomputes() {
        new ListingRankingTask(rankingService, true).run();

        verify(rankingService).recompute(any(Clock.class));
    }

    @Test
    @DisplayName("when disabled, a run does nothing")
    void disabledDoesNothing() {
        new ListingRankingTask(rankingService, false).run();

        verify(rankingService, never()).recompute(any(Clock.class));
    }

    @Test
    @DisplayName("a failed recompute is logged, not thrown into the scheduler")
    void failureDoesNotPropagate() {
        when(rankingService.recompute(any(Clock.class))).thenThrow(new IllegalStateException("db down"));

        new ListingRankingTask(rankingService, true).run();

        verify(rankingService).recompute(any(Clock.class));
    }
}
