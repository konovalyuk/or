package com.mavan.or.newpost.scheduling;

import java.util.List;

/** Aggregated workload of one terminal over the planning horizon. */
public record TerminalLoad(
        int city,
        double outboundTonnage,
        double transitTonnage,
        double inboundTonnage,
        double totalTonnage,
        double capacityPerHour,
        double busyHours,
        double peakRatePerHour,
        double utilizationPercent,
        Double firstStartHour,
        Double lastEndHour,
        double handlingCostPerTon,
        double cost
) {
    public static TerminalLoad of(int city, double capacityPerHour, double handlingCostPerTon,
                                  double horizonHours, List<PlannedTerminalJob> jobs) {
        double outbound = 0.0;
        double transit = 0.0;
        double inbound = 0.0;
        double busy = 0.0;
        double first = Double.NaN;
        double last = Double.NaN;
        for (PlannedTerminalJob job : jobs) {
            if (job.city() != city) continue;
            switch (job.kind()) {
                case ORIGIN -> outbound += job.tonnage();
                case TRANSIT -> transit += job.tonnage();
                case DESTINATION -> inbound += job.tonnage();
            }
            busy += job.endHour() - job.startHour();
            first = Double.isNaN(first) ? job.startHour() : Math.min(first, job.startHour());
            last = Double.isNaN(last) ? job.endHour() : Math.max(last, job.endHour());
        }
        double total = outbound + transit + inbound;
        double peak = busy <= 1e-12 ? 0.0 : total / busy;
        double utilization = horizonHours <= 1e-12 ? 0.0 : busy / horizonHours * 100.0;
        return new TerminalLoad(city, outbound, transit, inbound, total, capacityPerHour, busy, peak,
                utilization, Double.isNaN(first) ? null : first, Double.isNaN(last) ? null : last,
                handlingCostPerTon, total * handlingCostPerTon);
    }
}
