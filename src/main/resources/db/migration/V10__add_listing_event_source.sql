-- V10: record which feed surface showed a listing event.
--
-- listing_events says what a tenant did with a listing but not where the
-- listing was shown (home feed, search, which order). Without that, ranking
-- changes cannot be compared: a listing seen in the "recommended" search
-- order cannot be told apart from one seen in the default home feed.
--
-- Nullable and not backfilled: every existing row, and any event sent by a
-- client that does not report a source yet, simply has no source. The
-- values are the names of ListingEventSource, kept in a plain VARCHAR (like
-- event_type) so adding a value later never touches a CHECK constraint.
--
-- aws runs with ddl-auto=update, so Hibernate may already have added this
-- column from the entity; IF NOT EXISTS tolerates that.

ALTER TABLE listing_events ADD COLUMN IF NOT EXISTS source VARCHAR(30);
