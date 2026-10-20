package com.mavan.or.newpost.scheduling;

/** Scheduled amount on one leg of a fixed shipment path. */
public final class ShipmentLegSchedule {
    private final int shipmentIndex;
    private final int legIndex;
    private final int from;
    private final int to;
    private final double tonnage;
    private final int departureSlot;
    private final int arrivalSlot;
    private final double departureHour;
    private final double arrivalHour;

    public ShipmentLegSchedule(
            int shipmentIndex,
            int legIndex,
            int from,
            int to,
            double tonnage,
            int departureSlot,
            int arrivalSlot,
            double departureHour,
            double arrivalHour
    ) {
        this.shipmentIndex = shipmentIndex;
        this.legIndex = legIndex;
        this.from = from;
        this.to = to;
        this.tonnage = tonnage;
        this.departureSlot = departureSlot;
        this.arrivalSlot = arrivalSlot;
        this.departureHour = departureHour;
        this.arrivalHour = arrivalHour;
    }

    public int getShipmentIndex() { return shipmentIndex; }
    public int getLegIndex() { return legIndex; }
    public int getFrom() { return from; }
    public int getTo() { return to; }
    public double getTonnage() { return tonnage; }
    public int getDepartureSlot() { return departureSlot; }
    public int getArrivalSlot() { return arrivalSlot; }
    public double getDepartureHour() { return departureHour; }
    public double getArrivalHour() { return arrivalHour; }
}
