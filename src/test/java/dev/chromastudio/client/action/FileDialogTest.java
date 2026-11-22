package dev.chromastudio.client.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FileDialogTest {
    @Test
    void aDialogThatFailsToOpenLeavesItsCallbackToBeFreed() throws Exception {
        AtomicInteger failed = new AtomicInteger();
        FileDialog.choose("x".repeat(1 << 17), "png", _ -> {}, failed::incrementAndGet);
        assertEquals(1, failed.get());
        Field f = FileDialog.class.getDeclaredField("ANSWERED");
        f.setAccessible(true);
        assertEquals(1, ((List<?>) f.get(null)).size());
    }
}
