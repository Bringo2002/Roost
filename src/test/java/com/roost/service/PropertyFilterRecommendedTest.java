package com.roost.service;

import com.roost.model.Property;
import com.roost.repository.PropertyRepository;
import com.roost.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyFilterRecommendedTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private PropertyService propertyService;

    private final Pageable page = PageRequest.of(1, 10);

    @Test
    @DisplayName("passes filters, search tokens, the unscored default and the page to the score-ordered query")
    void delegatesWithTokensAndUnscoredDefault() {
        Property p = new Property();
        p.setId(5L);
        when(propertyRepository.filterPropertiesRecommended(
                eq("RENTAL"), eq(10000.0), isNull(), eq(2), isNull(), eq(true), isNull(), isNull(), isNull(), eq(true),
                eq("sunny"), eq("2br"), isNull(), isNull(), isNull(), isNull(),
                eq(ListingRankingFormula.UNSCORED_SCORE), eq(page)))
                .thenReturn(List.of(p));
        when(reviewRepository.findRatingSummariesByPropertyIds(List.of(5L))).thenReturn(List.of());

        List<Property> result = propertyService.filterRecommended(
                "RENTAL", 10000.0, null, 2, null, true, null, null, null, true, "Sunny 2BR", page);

        assertEquals(List.of(p), result);
        verify(reviewRepository).findRatingSummariesByPropertyIds(List.of(5L));
    }

    @Test
    @DisplayName("a blank search sends no tokens, and never touches the newest-first query")
    void blankSearchSendsNoTokens() {
        when(propertyRepository.filterPropertiesRecommended(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(ListingRankingFormula.UNSCORED_SCORE), eq(page)))
                .thenReturn(List.of());

        List<Property> result = propertyService.filterRecommended(
                null, null, null, null, null, null, null, null, null, null, "   ", page);

        assertEquals(List.of(), result);
        verifyNoInteractions(reviewRepository);
    }
}
