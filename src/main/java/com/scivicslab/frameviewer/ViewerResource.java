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
 * The page and the two routes it calls. The library is read from disk on every request, so an edited frame shows
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

    @GET
    @jakarta.ws.rs.Path("tree")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Library.TreeNode> tree() {
        return load().tree();
    }

    @GET
    @jakarta.ws.rs.Path("frame")
    @Produces(MediaType.TEXT_HTML)
    public String frame(@QueryParam("path") String path) {
        if (path == null || path.isBlank()) return "<p class=\"hint\">No frame given.</p>";
        return new FramePanel(load(), this::readUnderWorks).render(path);
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
        Path base = Path.of(worksDir).toAbsolutePath().normalize();
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
