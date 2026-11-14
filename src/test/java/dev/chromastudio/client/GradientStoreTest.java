package dev.chromastudio.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chromastudio.core.palette.StoredGradient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradientStoreTest {
    @TempDir
    Path dir;

    private static StoredGradient named(String name, int steps) {
        return new StoredGradient(name, List.of(StoredGradient.Stop.colour(0), StoredGradient.Stop.colour(0xFFFFFF)), steps, "LINEAR", "SHORTEST", false, "", List.of());
    }

    @Test
    void gradientsAreKeptByNameWhateverTheirCase() {
        Path file = dir.resolve("chromastudio").resolve("gradients.json");
        GradientStore store = new GradientStore(file);
        assertEquals(List.of(), store.all());
        assertTrue(store.put(named("sunset", 5)));
        assertTrue(store.put(named("Autumn", 4)));
        assertTrue(store.put(named("Sunset", 9)));
        assertEquals(List.of("Autumn", "Sunset"), store.all().stream().map(StoredGradient::name).toList());
        assertEquals(9, store.get(" SUNSET ").steps());

        GradientStore again = new GradientStore(file);
        assertEquals(store.all(), again.all(), "they are saved to the file");
        assertTrue(again.delete("autumn"));
        assertFalse(again.delete("autumn"));
        assertNull(again.get("Autumn"));
        store.reload();
        assertEquals(List.of("Sunset"), store.all().stream().map(StoredGradient::name).toList());
    }

    @Test
    void aFileThatCantBeReadIsKeptAside() throws IOException {
        Path file = dir.resolve("gradients.json");
        Files.writeString(file, "{\"format\": 99}");
        GradientStore store = new GradientStore(file);
        assertEquals(List.of(), store.all());
        assertTrue(Files.exists(file), "nothing is written until something is saved");
        assertTrue(store.put(named("New", 3)));
        assertEquals("{\"format\": 99}", Files.readString(dir.resolve("gradients.json.unreadable")));
        assertEquals(1, new GradientStore(file).all().size());
    }
}
