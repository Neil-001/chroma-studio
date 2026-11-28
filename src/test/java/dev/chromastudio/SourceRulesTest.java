package dev.chromastudio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceRulesTest {
    private static final Set<String> SKIPPED = Set.of(".git", ".gradle", ".idea");
    private static final Set<String> OUTPUTS = Set.of("bin", "build", "logs", "out", "run");
    private static final String API = "src/client/java/dev/chromastudio/api/";
    private static final Map<String, Integer> ALLOWED = Map.of(
            "src/client/java/dev/chromastudio/api/ChromaStudioApi.java", 2);
    private static final String CORE = "src/main/java/";
    private static final String CLIENT = "src/client/java/";
    private static final String AXIOM = CLIENT + "dev/chromastudio/client/axiom/";
    private static final List<Boundary> BOUNDARIES = List.of(
            new Boundary(in(CORE), names("net.minecraft", "com.mojang"),
                    "dev.chromastudio.core stays independent of Minecraft"),
            new Boundary(in(CORE, CLIENT), names("net.fabricmc", "net.neoforged"),
                    "loader classes belong in that loader's source tree, reached through ClientPlatform"),
            new Boundary(in(CORE, CLIENT, "fabric/src/", "neoforge/src/").and(name -> !name.startsWith(AXIOM)),
                    names("com.moulberry", "imgui.moulberry92"),
                    "Axiom classes belong in dev.chromastudio.client.axiom, entered through AxiomBridge"));

    private record Boundary(Predicate<String> covers, Pattern names, String rule) {
    }

    @Test
    void everyClaudeMdHasAnAgentsMdLinkBesideIt() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path file : files(name -> name.equals("CLAUDE.md"))) {
            Path agents = file.resolveSibling("AGENTS.md");
            if (!Files.isSymbolicLink(agents) || !Files.readSymbolicLink(agents).toString().equals("CLAUDE.md")) {
                missing.add(name(agents) + " is not a symlink to CLAUDE.md");
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void sourceTreesNameOnlyThePackagesTheyMayUse() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : files(name -> name.endsWith(".java"))) {
            problems.addAll(breaches(name(file), Files.readString(file)));
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyBoundaryCatchesTheNamesItForbids() {
        String block = "import net.minecraft.world.level.block.Block;";
        String loader = "    boolean loaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);";
        String axiom = "        Class.forName(\"com.moulberry.axiomclientapi.AxiomClientAPI\");";
        String core = CORE + "dev/chromastudio/core/A.java";
        String tab = CLIENT + "dev/chromastudio/client/ui/A.java";
        assertEquals(List.of(core + ":2 names net.minecraft, but " + BOUNDARIES.get(0).rule()),
                breaches(core, "package dev.chromastudio.core;\n" + block));
        assertEquals(List.of(), breaches(tab, block));
        assertEquals(List.of(tab + ":1 names net.fabricmc, but " + BOUNDARIES.get(1).rule()), breaches(tab, loader));
        assertEquals(List.of(), breaches("fabric/src/client/java/dev/chromastudio/client/fabric/A.java", loader));
        assertEquals(List.of(tab + ":1 names com.moulberry, but " + BOUNDARIES.get(2).rule()), breaches(tab, axiom));
        assertEquals(List.of(), breaches(AXIOM + "A.java", axiom + "\nimport imgui.moulberry92.ImGui;"));
        assertEquals(1, breaches("neoforge/src/main/java/dev/chromastudio/client/neoforge/A.java", "import imgui.moulberry92.ImGui;").size());
        assertEquals(List.of(), breaches("src/test/java/dev/chromastudio/A.java", String.join("\n", block, loader, axiom)));
        assertEquals(List.of(core + ":1 names net.minecraft, but " + BOUNDARIES.get(0).rule()),
                breaches(core, "package dev.chromastudio.core; \\u000a " + block));
        assertEquals(1, breaches(core, "boolean vanilla = type.getPackageName().equals(\"net.minecraft\");").size());
        assertEquals(List.of(), breaches(core, "String internet = planet.minecrafty.name + magnet.minecraft.name;"));
        assertEquals(List.of(), breaches(core, String.join("\n", "String help = \"\"\"",
                "    Official information is available at minecraft.net.", "    minecraft.wiki lists block IDs.",
                "    Download it from example.net. minecraft:stone is the default.", "    \"\"\";")));
    }

    private static List<String> breaches(String name, String raw) {
        String source = raw.replace("\r\n", "\n").replace('\r', '\n');
        List<String> problems = new ArrayList<>();
        for (Boundary boundary : BOUNDARIES) {
            if (!boundary.covers().test(name)) continue;
            Matcher found = boundary.names().matcher(source);
            while (found.find()) {
                long line = 1 + source.substring(0, found.start()).chars().filter(c -> c == '\n').count();
                problems.add(name + ":" + line + " names " + found.group() + ", but " + boundary.rule());
            }
        }
        return problems;
    }

    private static Predicate<String> in(String... trees) {
        return name -> Arrays.stream(trees).anyMatch(name::startsWith);
    }

    private static Pattern names(String... packages) {
        return Pattern.compile("\\b(" + Arrays.stream(packages).map(Pattern::quote).collect(Collectors.joining("|")) + ")\\b");
    }

    @Test
    void javaSourcesHaveNoCommentsOutsideTheAllowlist() throws IOException {
        Map<String, List<Integer>> found = new TreeMap<>();
        for (Path file : files(name -> name.endsWith(".java"))) {
            String name = name(file);
            List<Integer> lines = commentLines(Files.readString(file), name.startsWith(API));
            if (!lines.isEmpty()) found.put(name, lines);
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
        String slash = "/", star = "*", backslash = "\\";
        String source = String.join("\n",
                "class A {",
                "    String a = \"" + slash + slash + "replace stone\";",
                "    char b = '\\'';",
                "    String c = \"\"\"",
                "        \\\"\"\" " + slash + star + " not a comment " + backslash,
                "        \"\"\";",
                "    int d; " + slash + slash + " one",
                "    " + slash + slash + " the same run",
                "    int e; " + slash + slash + " two",
                "    " + slash + star + star + " three",
                "     " + star + slash,
                "    " + slash + slash + " four",
                "    " + backslash + "u002f" + backslash + "u002a five " + backslash + "u002a" + backslash + "u002f",
                "}");
        assertEquals(List.of(7, 9, 10, 12, 13), commentLines(source, false));
        assertEquals(List.of(7, 9, 12, 13), commentLines(source, true));
        assertEquals(List.of(1, 2), commentLines(slash + slash + " a" + backslash + "u000d" + slash + star + " b " + star + slash, false));
        assertEquals(List.of(1, 3), commentLines(slash + slash + " a\rint x;\r" + slash + slash + " b\r", false));
    }

    private static List<Integer> commentLines(String raw, boolean javadocAllowed) {
        String source = unescape(raw).replace("\r\n", "\n").replace('\r', '\n');
        List<Integer> starts = new ArrayList<>();
        int line = 1, lastLineComment = -1, lastLineCommentEnd = -1;
        int i = 0, n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("\"\"\"", i)) {
                i += 3;
                while (!source.startsWith("\"\"\"", i)) {
                    int step = source.charAt(i) == '\\' ? 2 : 1;
                    for (int j = i; j < i + step; j++) if (source.charAt(j) == '\n') line++;
                    i += step;
                }
                i += 3;
            } else if (c == '"' || c == '\'') {
                i++;
                while (source.charAt(i) != c) i += source.charAt(i) == '\\' ? 2 : 1;
                i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                boolean ownLine = source.substring(source.lastIndexOf('\n', i - 1) + 1, i).isBlank();
                boolean joinsRun = ownLine && lastLineComment == line - 1
                        && source.substring(lastLineCommentEnd, i).isBlank();
                if (!joinsRun) starts.add(line);
                lastLineComment = line;
                while (i < n && source.charAt(i) != '\n') i++;
                lastLineCommentEnd = i;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                boolean javadoc = source.startsWith("/**", i) && !source.startsWith("/**/", i);
                if (!(javadoc && javadocAllowed)) starts.add(line);
                int end = source.indexOf("*/", i + 2);
                for (int j = i; j < end; j++) if (source.charAt(j) == '\n') line++;
                i = end + 2;
                lastLineComment = -1;
            } else {
                if (c == '\n') line++;
                i++;
            }
        }
        return starts;
    }

    private static String unescape(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c != '\\') {
                out.append(c);
                i++;
                continue;
            }
            int slashes = 0;
            while (i + slashes < source.length() && source.charAt(i + slashes) == '\\') slashes++;
            int at = i + slashes;
            if (slashes % 2 == 1 && at < source.length() && source.charAt(at) == 'u') {
                out.append("\\".repeat(slashes - 1));
                int hex = at;
                while (source.charAt(hex) == 'u') hex++;
                out.append((char) Integer.parseInt(source.substring(hex, hex + 4), 16));
                i = hex + 4;
            } else {
                out.append("\\".repeat(slashes));
                i = at;
            }
        }
        return out.toString();
    }

    private static List<Path> files(Predicate<String> wanted) throws IOException {
        List<Path> found = new ArrayList<>();
        Path root = root();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                String name = dir.getFileName().toString();
                boolean output = OUTPUTS.contains(name) && Files.exists(dir.resolveSibling("build.gradle"));
                boolean skip = !dir.equals(root) && (SKIPPED.contains(name) || output);
                return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (wanted.test(file.getFileName().toString())) found.add(file);
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
