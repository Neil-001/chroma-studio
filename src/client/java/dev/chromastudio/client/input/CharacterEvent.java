package dev.chromastudio.client.input;

/** A character typed. */
public record CharacterEvent(int codepoint, int modifiers) {}
