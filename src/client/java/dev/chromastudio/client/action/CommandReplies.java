package dev.chromastudio.client.action;

import java.util.Set;

/**
 * Which of the game's chat lines answer the commands a hotbar fill sent, so they can be kept out of chat: an
 * operator's fill is up to ten commands, and the game says "Replaced a slot" for each. A line is known by its
 * translation key, and one is hidden for each command still unanswered.
 *
 * <p>It's a best effort, and when in doubt the line shows. An error is never hidden, so a command that failed stays
 * unanswered until the count is given up on, and a command of the player's own typed in that time loses its reply.
 */
public final class CommandReplies {
    /** What the game says when {@code /item replace} or {@code /give} worked on one player. */
    static final Set<String> SUCCESS = Set.of("commands.item.entity.set.success.single", "commands.give.success.single");
    /** Ticks without a command sent or a reply seen before the rest are given up on. */
    static final int TIMEOUT = 200;

    private int awaiting, idle;

    /** Call for each of the mod's commands as it's sent. */
    public void sent() {
        awaiting++;
        idle = 0;
    }

    /** Call once a client tick. */
    public void tick() {
        if (awaiting > 0 && ++idle > TIMEOUT) awaiting = 0;
    }

    /** How many of the mod's commands haven't been answered yet. */
    public int awaiting() { return awaiting; }

    /** Whether to hide a line, by its translation key. */
    public boolean hides(String key) {
        if (awaiting == 0 || !SUCCESS.contains(key)) return false;
        awaiting--;
        idle = 0;
        return true;
    }
}
