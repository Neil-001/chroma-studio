package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class CommandRepliesTest {
    private static final String REPLACED = "commands.item.entity.replace.success.single.known_item", EMPTIED = "commands.item.entity.replace.success.single", GAVE = "commands.give.success.single";

    @Test
    void oneReplyIsHiddenForEachCommandSent() {
        CommandReplies r = new CommandReplies();
        assertFalse(r.hides(REPLACED), "nothing was sent, so the line is the player's own");
        r.sent();
        r.sent();
        assertTrue(r.hides(REPLACED));
        assertFalse(r.hides("commands.item.target.no_changes"), "an error shows");
        assertFalse(r.hides("chat.type.text"));
        assertTrue(r.hides(GAVE));
        assertFalse(r.hides(REPLACED), "both are answered");
        r.sent();
        assertTrue(r.hides(EMPTIED), "an undo that empties a slot names no item");
        assertEquals(0, r.awaiting());
    }

    @Test
    void anUnansweredCommandIsGivenUpOn() {
        CommandReplies r = new CommandReplies();
        r.sent();
        for (int i = 0; i < CommandReplies.TIMEOUT; i++) r.tick();
        assertEquals(1, r.awaiting());
        r.tick();
        assertEquals(0, r.awaiting());
        assertFalse(r.hides(REPLACED));
    }

    @Test
    void onlyAPlainSystemLineInChatIsAReply() {
        assertFalse(HotbarFill.hidesFeedback(Component.translatable(REPLACED), false), "nothing was sent");
        assertFalse(HotbarFill.hidesFeedback(Component.literal("Replaced a slot"), false));
        assertFalse(HotbarFill.hidesFeedback(Component.translatable(REPLACED), true), "a line above the hotbar is never a command's reply");
    }
}
