package com.roost.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for PropertyService#tokenizeSearchQuery -- pure function,
 *  no Spring context or DB needed. See PropertyRepository's
 *  filterProperties doc for how these tokens are used in the query. */
class PropertySearchTokenizerTest {

    @Test
    void nullOrBlankQueryProducesAllNullTokens() {
        assertThat(PropertyService.tokenizeSearchQuery(null)).containsOnlyNulls();
        assertThat(PropertyService.tokenizeSearchQuery("")).containsOnlyNulls();
        assertThat(PropertyService.tokenizeSearchQuery("   ")).containsOnlyNulls();
    }

    @Test
    void singleWordBecomesFirstTokenLowercased() {
        String[] tokens = PropertyService.tokenizeSearchQuery("Kilimani");
        assertThat(tokens[0]).isEqualTo("kilimani");
        assertThat(tokens).hasSize(6);
        for (int i = 1; i < tokens.length; i++) assertThat(tokens[i]).isNull();
    }

    @Test
    void fillerWordsAreDropped() {
        String[] tokens = PropertyService.tokenizeSearchQuery("Kilimani apartment for rent");
        assertThat(tokens[0]).isEqualTo("kilimani");
        assertThat(tokens[1]).isNull();
    }

    @Test
    void allFillerQueryProducesAllNullTokens() {
        assertThat(PropertyService.tokenizeSearchQuery("a house for rent")).containsOnlyNulls();
    }

    @Test
    void punctuationIsSplitOn() {
        String[] tokens = PropertyService.tokenizeSearchQuery("pet-friendly, kileleshwa!");
        assertThat(tokens[0]).isEqualTo("pet");
        assertThat(tokens[1]).isEqualTo("friendly");
        assertThat(tokens[2]).isEqualTo("kileleshwa");
    }

    @Test
    void duplicateWordsAreNotRepeated() {
        String[] tokens = PropertyService.tokenizeSearchQuery("bedsitter bedsitter kilimani");
        assertThat(tokens[0]).isEqualTo("bedsitter");
        assertThat(tokens[1]).isEqualTo("kilimani");
        assertThat(tokens[2]).isNull();
    }

    @Test
    void moreThanSixWordsIsTruncatedNotRejected() {
        String[] tokens = PropertyService.tokenizeSearchQuery("one two three four five six seven eight");
        assertThat(tokens).containsExactly("one", "two", "three", "four", "five", "six");
    }
}
