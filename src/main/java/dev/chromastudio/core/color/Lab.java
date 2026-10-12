package dev.chromastudio.core.color;

/** A colour in CIELAB under a D65 white. Lightness runs 0 to 100. */
public record Lab(double l, double a, double b) {}
