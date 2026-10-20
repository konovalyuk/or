package com.mavan.or.newpost.scheduling;

/** One terminal-processing operation for a fixed shipment-flow visit. */
public final class TerminalOperation {
    private final int shipmentIndex;
    private final int city;
    private final int visitIndex;
    private final double tonnage;
    private final int startSlot;
    private final int endSlot;
    private final double startHour;
    private final double endHour;
    private final double cost;

    public TerminalOperation(
            int shipmentIndex,
            int city,
            int visitIndex,
            double tonnage,
            int startSlot,
            int endSlot,
            double startHour,
            double endHour,
            double cost
    ) {
        this.shipmentIndex = shipmentIndex;
        this.city = city;
        this.visitIndex = visitIndex;
        this.tonnage = tonnage;
        this.startSlot = startSlot;
        this.endSlot = endSlot;
        this.startHour = startHour;
        this.endHour = endHour;
        this.cost = cost;
    }

    public int getShipmentIndex() { return shipmentIndex; }
    public int getCity() { return city; }
    public int getVisitIndex() { return visitIndex; }
    public double getTonnage() { return tonnage; }
    public int getStartSlot() { return startSlot; }
    public int getEndSlot() { return endSlot; }
    public double getStartHour() { return startHour; }
    public double getEndHour() { return endHour; }
    public double getCost() { return cost; }
}
