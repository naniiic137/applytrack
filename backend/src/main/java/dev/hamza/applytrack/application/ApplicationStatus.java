package dev.hamza.applytrack.application;

import java.util.EnumSet;
import java.util.Set;

public enum ApplicationStatus {
    WISHLIST,
    APPLIED,
    INTERVIEW,
    OFFER,
    REJECTED,
    GHOSTED;

    /** Statuses where the candidate is still waiting on, or working on, something. */
    public static final Set<ApplicationStatus> ACTIVE = EnumSet.of(WISHLIST, APPLIED, INTERVIEW);

    /** Reaching any of these means the company replied (positively or not). */
    public static final Set<ApplicationStatus> RESPONSES = EnumSet.of(INTERVIEW, OFFER, REJECTED);

    /** Reaching any of these means the application turned into at least one interview. */
    public static final Set<ApplicationStatus> INTERVIEWED = EnumSet.of(INTERVIEW, OFFER);
}
