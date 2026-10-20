package com.mavan.or.newpost.model;

/** One marginal volume-discount segment for an arc/time load. */
public record DiscountBand(double upToTonnage, double tariffPerKm) {
    public DiscountBand {
        if (!(upToTonnage > 0.0) || !Double.isFinite(upToTonnage)) {
            throw new IllegalArgumentException("Discount band tonnage must be positive and finite");
        }
        if (tariffPerKm < 0.0 || !Double.isFinite(tariffPerKm)) {
            throw new IllegalArgumentException("Discount band tariff must be non-negative and finite");
        }
    }
}
