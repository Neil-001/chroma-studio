package com.moulberry.axiom.mask;

import net.minecraft.world.level.Level;

/**
 * A stand-in for one of Axiom's own classes, written for Chroma Studio's tests, with only the members Chroma Studio
 * reaches by reflection. It holds none of Axiom's code.
 */
public class MaskContext {
    public int x, y, z;
    /** As in Axiom, a context compiles a Lua mask's script on first use, and stops running it once it has failed. */
    public boolean compiled, broken;

    public MaskContext(Level level) {}

    public void setPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
