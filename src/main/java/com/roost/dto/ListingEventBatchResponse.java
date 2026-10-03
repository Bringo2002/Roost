package com.roost.dto;

/**
 * Outcome of a listing-event batch. {@code dropped} counts events for
 * listings that no longer exist (deleted between display and report) --
 * reported rather than silently ignored so a client bug that sends bad
 * ids is visible instead of looking like success.
 */
public record ListingEventBatchResponse(int accepted, int dropped) {}
