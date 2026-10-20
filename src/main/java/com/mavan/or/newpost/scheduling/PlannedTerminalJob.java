package com.mavan.or.newpost.scheduling;

/** Processing of one shipment flow at one visited terminal. */
public record PlannedTerminalJob(
        int city,
        int shipmentIndex,
        int visitIndex,
        Kind kind,
        double tonnage,
        double startHour,
        double endHour,
        double cost
) {
    public enum Kind { ORIGIN, TRANSIT, DESTINATION }
}
