package dev.chromastudio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceRulesTest {
    private static final Set<String> SKIPPED = Set.of(".git", ".gradle", ".idea", "bin", "build", "out", "run");
    private static final String API = "src/client/java/dev/chromastudio/api/";
    private static final Map<String, Integer> ALLOWED = Map.of(
            "src/client/java/dev/chromastudio/client/ChromaStudioClient.java", 1,
            "src/client/java/dev/chromastudio/client/action/HotbarFill.java", 1,
            "src/client/java/dev/chromastudio/client/axiom/AxiomEditor.java", 1,
            "src/client/java/dev/chromastudio/client/axiom/SamplerTool.java", 1,
            "src/client/java/dev/chromastudio/client/ui/Tooltips.java", 1,
            "src/client/java/dev/chromastudio/client/ui/tabs/GradientTab.java", 1,
            "src/client/java/dev/chromastudio/client/ui/tabs/PaletteDialogs.java", 1,
            "src/main/java/dev/chromastudio/core/color/Distribution.java", 1);

    @Test
    void everyClaudeMdHasAnAgentsMdLinkBesideIt() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path file : files("CLAUDE.md")) {
            Path agents = file.resolveSibling("AGENTS.md");
            if (!Files.isSymbolicLink(agents) || !Files.readSymbolicLink(agents).toString().equals("CLAUDE.md")) {
                missing.add(name(agents) + " is not a symlink to CLAUDE.md");
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void javaSourcesHaveNoCommentsOutsideTheAllowlist() throws IOException {
        Map<String, List<Integer>> found = new TreeMap<>();
        for (Path file : files(".java")) {
            String name = name(file);
            List<Integer> lines = commentLines(Files.readString(file));
            if (!lines.isEmpty() && !name.startsWith(API)) found.put(name, lines);
        }
        List<String> problems = new ArrayList<>();
        found.forEach((name, lines) -> {
            if (lines.size() != ALLOWED.getOrDefault(name, 0)) {
                problems.add(name + " has comments at lines " + lines + ", and the allowlist permits " + ALLOWED.getOrDefault(name, 0));
            }
        });
        ALLOWED.forEach((name, count) -> {
            if (!found.containsKey(name)) problems.add(name + " is allowed " + count + " comments but has none, so remove its entry");
        });
        assertEquals(List.of(), problems);
    }

    @Test
    void theScannerTellsCommentsFromStrings() {
        String slash = "/", star = "*";
        String source = String.join("\n",
                "class A {",
                "    String a = \"" + slash + slash + "replace stone\";",
                "    char b = '\\'';",
                "    String c = \"\"\"",
                "        \\\"\"\" " + slash + star + " not a comment",
                "        \"\"\";",
                "    int d; " + slash + slash + " one",
                "    " + slash + slash + " the same run",
                "",
                "    " + slash + star + star + " two",
                "     " + star + slash,
                "    " + slash + slash + " three",
                "}");
        assertEquals(List.of(7, 10, 12), commentLines(source));
    }

    private static List<Integer> commentLines(String source) {
        List<Integer> starts = new ArrayList<>();
        int line = 1, lastLineCommentEnd = -1;
        int i = 0, n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("\"\"\"", i)) {
                i += 3;
                while (!source.startsWith("\"\"\"", i)) {
                    if (source.charAt(i) == '\n') line++;
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                i += 3;
            } else if (c == '"' || c == '\'') {
                i++;
                while (source.charAt(i) != c) i += source.charAt(i) == '\\' ? 2 : 1;
                i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                if (lastLineCommentEnd != line - 1) starts.add(line);
                lastLineCommentEnd = line;
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                starts.add(line);
                int end = source.indexOf("*/", i + 2);
                for (int j = i; j < end; j++) if (source.charAt(j) == '\n') line++;
                i = end + 2;
            } else {
                if (c == '\n') line++;
                i++;
            }
        }
        return starts;
    }

    private static List<Path> files(String suffix) throws IOException {
        List<Path> found = new ArrayList<>();
        Path root = root();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                boolean skip = !dir.equals(root) && SKIPPED.contains(dir.getFileName().toString());
                return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (file.getFileName().toString().endsWith(suffix)) found.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return found;
    }

    private static String name(Path file) {
        return root().relativize(file).toString().replace('\\', '/');
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (!Files.exists(dir.resolve("settings.gradle"))) dir = dir.getParent();
        return dir;
    }
}
