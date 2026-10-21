package com.scivicslab.frameviewer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The page and the routes it calls: one tree per tab and one panel per kind of item. The library is read from disk on every request, so an edited frame shows
 * on the next click. Every link the page makes is relative: the AI workspace strips its own path prefix before a
 * request reaches this tool.
 */
@jakarta.ws.rs.Path("/")
public class ViewerResource {

    @Inject
    Template index;

    @ConfigProperty(name = "frame-viewer.rst-on-lisp-dir")
    String rstOnLispDir;

    @ConfigProperty(name = "frame-viewer.works-dir")
    String worksDir;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance page() {
        return index.instance();
    }

    /** @param tab {@code tags}, {@code concrete}, {@code sources} or {@code integrated} */
    @GET
    @jakarta.ws.rs.Path("tree")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Library.TreeNode> tree(@QueryParam("tab") String tab) {
        Library lib = load();
        return switch (tab == null ? "tags" : tab) {
            case "concrete" -> lib.frameTree(false);
            case "integrated" -> lib.frameTree(true);
            case "sources" -> sourceTree(new Sources(lib, this::readUnderWorks));
            default -> lib.tagTree();
        };
    }

    @GET
    @jakarta.ws.rs.Path("frame")
    @Produces(MediaType.TEXT_HTML)
    public String frame(@QueryParam("path") String path) {
        if (path == null || path.isBlank()) return "<p class=\"hint\">No frame given.</p>";
        Library lib = load();
        return new FramePanel(lib, new Sources(lib, this::readUnderWorks)).render(path);
    }

    @GET
    @jakarta.ws.rs.Path("tag")
    @Produces(MediaType.TEXT_HTML)
    public String tag(@QueryParam("tag") String tag) {
        if (tag == null || tag.isBlank()) return "<p class=\"hint\">No tag given.</p>";
        Library lib = load();
        return new TagPanel(lib, new Sources(lib, this::readUnderWorks)).render(tag);
    }

    @GET
    @jakarta.ws.rs.Path("source")
    @Produces(MediaType.TEXT_HTML)
    public String source(@QueryParam("file") String file) {
        if (file == null || file.isBlank()) return "<p class=\"hint\">No source given.</p>";
        Library lib = load();
        return new SourcePanel(new Sources(lib, this::readUnderWorks)).render(file);
    }

    /** The left pane of the concrete-frame tab: the frame file as it is, with its header words linked to paragraphs. */
    @GET
    @jakarta.ws.rs.Path("raw-frame")
    @Produces(MediaType.TEXT_HTML)
    public String rawFrame(@QueryParam("path") String path) {
        if (path == null || path.isBlank()) return "<p class=\"hint\">No frame given.</p>";
        Library lib = load();
        return new RawPanel(lib, new Sources(lib, this::readUnderWorks)).frame(path, readUnder(Path.of(rstOnLispDir, "frames"), path));
    }

    /** The right pane of the concrete-frame tab: one source file as it is, the paragraphs the frame names marked. */
    @GET
    @jakarta.ws.rs.Path("raw-source")
    @Produces(MediaType.TEXT_HTML)
    public String rawSource(@QueryParam("file") String file, @QueryParam("frame") String frame) {
        if (file == null || file.isBlank()) return "<p class=\"hint\">No source given.</p>";
        Library lib = load();
        return new RawPanel(lib, new Sources(lib, this::readUnderWorks)).source(file, readUnderWorks(file), frame == null ? "" : frame);
    }

    /** The source files grouped by the directory of the book or paper they belong to. */
    static List<Library.TreeNode> sourceTree(Sources sources) {
        List<Library.TreeNode> out = new java.util.ArrayList<>();
        java.util.Set<String> dirs = new java.util.HashSet<>();
        for (String f : sources.files()) {
            String dir = bookDir(f);
            if (dirs.add(dir)) out.add(new Library.TreeNode("d:" + dir, "#", dir, java.util.Map.of()));
            String name = f.substring(f.lastIndexOf('/') + 1);
            out.add(new Library.TreeNode("f:" + f, "d:" + dir, name, java.util.Map.of("data-source", f, "class", "has-frame")));
        }
        return out;
    }

    /** {@code doc_X/docs/Shelf/Book/Chap03/x/x.lisp} → {@code doc_X/docs/Shelf/Book} */
    static String bookDir(String file) {
        String[] p = file.split("/");
        return String.join("/", java.util.Arrays.copyOf(p, Math.min(4, p.length - 1)));
    }

    /** Reads {@code subjects/*.lisp} and every {@code .lisp} under {@code frames/}. */
    Library load() {
        Library lib = new Library();
        Path root = Path.of(rstOnLispDir);
        for (Path p : lisp(root.resolve("subjects"))) lib.addSubjects(read(p));
        Path frames = root.resolve("frames");
        for (Path p : lisp(frames)) lib.addFrame(frames.relativize(p).toString().replace('\\', '/'), read(p));
        return lib;
    }

    /** @return the text of a file under the works directory, or null when it is missing or outside the directory */
    String readUnderWorks(String relative) {
        return readUnder(Path.of(worksDir), relative);
    }

    /** @return the text of a file under {@code dir}, or null when it is missing or outside {@code dir} */
    private static String readUnder(Path dir, String relative) {
        Path base = dir.toAbsolutePath().normalize();
        Path p = base.resolve(relative).normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p)) return null;
        return read(p);
    }

    private static List<Path> lisp(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".lisp") && Files.isRegularFile(p)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
