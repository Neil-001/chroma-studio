package dev.chromastudio.client;

/**
 * Which colour space the Match tab's sliders work in. The field under the hex code that would repeat them shows
 * OKLCH in its place. It changes only what the player sees and types; matching always measures in its own space.
 */
public enum ColourFormat { OKLCH, RGB, HSL }
