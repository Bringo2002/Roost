package com.roost.model;

/** Where a lister's identity verification stands. */
public enum IdentityStatus {
    /** No complete set of identity documents has been submitted. */
    NOT_SUBMITTED,
    /** A complete set was submitted and is waiting for a reviewer. */
    PENDING,
    /** A reviewer accepted the documents. */
    VERIFIED,
    /** A reviewer rejected the documents; the lister can submit new ones. */
    REJECTED
}
