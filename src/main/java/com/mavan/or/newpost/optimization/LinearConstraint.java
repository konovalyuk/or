package com.mavan.or.newpost.optimization;

final class LinearConstraint {
    final double[] a;
    final double b;

    LinearConstraint(double[] a, double b) {
        this.a = a;
        this.b = b;
    }
}
