package com.mavan.or.newpost.scheduling;

/** Part of one shipment flow carried by one physical truck trip. */
public record TripLoad(int shipmentIndex, int origin, int destination, int legIndex, double tonnage) {}
