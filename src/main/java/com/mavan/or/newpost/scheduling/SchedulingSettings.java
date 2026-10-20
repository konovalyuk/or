package com.mavan.or.newpost.scheduling;

/**
 * Time-discretization settings for the exact fixed-route scheduling model.
 *
 * The current scheduler is exact for this discrete-time model:
 * every processing operation occupies whole time slots and every travel
 * duration is rounded up to the required number of slots.  A smaller slot
 * duration gives a tighter approximation of continuous time.
 */
public final class SchedulingSettings {
    private final double periodDurationHours;

    public SchedulingSettings(double periodDurationHours) {
        if (!(periodDurationHours > 0.0) || !Double.isFinite(periodDurationHours)) {
            throw new IllegalArgumentException("Period duration must be positive and finite");
        }
        this.periodDurationHours = periodDurationHours;
    }

    public double getPeriodDurationHours() {
        return periodDurationHours;
    }
}
