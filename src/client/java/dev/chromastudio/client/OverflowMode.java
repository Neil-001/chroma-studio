package dev.chromastudio.client;

/**
 * When a hotbar fill packs the blocks past the ninth into shulker boxes. Never, they go into free inventory slots and
 * what's left is dropped; when full, only those with no slot are boxed; always, all of them are.
 */
public enum OverflowMode { NEVER, WHEN_FULL, ALWAYS }
