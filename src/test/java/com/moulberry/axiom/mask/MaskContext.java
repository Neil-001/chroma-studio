package com.moulberry.axiom.mask;

import net.minecraft.world.level.Level;

public class MaskContext {
    public int x, y, z;
    public boolean compiled, broken;

    public MaskContext(Level level) {}

    public void setPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
