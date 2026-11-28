package dev.chromastudio.client.action;

import java.util.Set;

public final class CommandReplies {
    static final Set<String> SUCCESS = Set.of("commands.item.entity.replace.success.single.known_item", "commands.item.entity.replace.success.single",
            "commands.give.success.single");
    static final int TIMEOUT = 200;

    private int awaiting, idle;

    public void sent() {
        awaiting++;
        idle = 0;
    }

    public void tick() {
        if (awaiting > 0 && ++idle > TIMEOUT) awaiting = 0;
    }

    public int awaiting() { return awaiting; }

    public boolean hides(String key) {
        if (awaiting == 0 || !SUCCESS.contains(key)) return false;
        awaiting--;
        idle = 0;
        return true;
    }
}
