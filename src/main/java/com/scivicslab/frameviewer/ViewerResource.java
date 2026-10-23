package com.scivicslab.frameviewer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    @ConfigProperty(name = "frame-viewer.workflows-dir")
    String workflowsDir;

    @ConfigProperty(name = "frame-viewer.workflows")
    List<String> workflows;

    @ConfigProperty(name = "frame-viewer.books-db")
    String booksDb;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance page() {
        // the page builds code-server links from absolute paths, so it is told where frames and sources live
        return index.data("framesDir", Path.of(rstOnLispDir, "frames").toAbsolutePath().normalize().toString())
                .data("worksDir", Path.of(worksDir).toAbsolutePath().normalize().toString());
    }

    /** @param tab the tab whose tree is wanted, e.g. {@code workflows}, {@code contents}, {@code concrete} */
    @GET
    @jakarta.ws.rs.Path("tree")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Library.TreeNode> tree(@QueryParam("tab") String tab) {
        Library lib = load();
        return switch (tab == null ? "workflows" : tab) {
            case "integrated" -> lib.frameTree(true);
            case "classes" -> classPanel().tree();
            case "contents" -> bookPanel().contentsTree();
            case "sections" -> bookPanel().sectionsTree();
            case "bookframes" -> {
                List<Library.TreeNode> out = new java.util.ArrayList<>();
                for (EntityPanel p : entityPanels(null).values()) out.addAll(p.tree());
                yield out;
            }
            default -> workflowPanel().tree();
        };
    }

    /** The Contents tab's panel: one node of a book's table of contents. */
    @GET
    @jakarta.ws.rs.Path("book-node")
    @Produces(MediaType.TEXT_HTML)
    public String bookNode(@QueryParam("book") String book, @QueryParam("number") String number) {
        if (book == null || number == null) return "<p class=\"hint\">No node given.</p>";
        return bookPanel().node(book, number);
    }

    /** The Sections tab's panel: one section's text. */
    @GET
    @jakarta.ws.rs.Path("book-section")
    @Produces(MediaType.TEXT_HTML)
    public String bookSection(@QueryParam("book") String book, @QueryParam("number") String number, @QueryParam("view") String view) {
        if (book == null || number == null) return "<p class=\"hint\">No section given.</p>";
        // the rule file and the concepts' sentences: the concepts are shown with their rules beside them
        com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules = null;
        List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed = null;
        com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout pattern = null;
        Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items = Map.of();
        if (Files.isRegularFile(Path.of(Path.of(booksDb).toAbsolutePath().normalize() + ".mv.db"))) {
            try (var store = new com.scivicslab.turingworkflow.plugins.rstframe.BookStore(booksUrl())) {
                rules = store.sectionRules(book, number);
                listed = store.listedConcepts(book).get(number);
                pattern = store.patterns(book).get(number);
                items = store.frameItems(book).getOrDefault(number, Map.of());
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("could not read " + booksDb + ": " + e.getMessage(), e);
            }
        }
        return bookPanel().section(book, number, view == null ? "md" : view, rules, listed, pattern, items);
    }

    /** The Book frames tab's panel: one concept of a book, recognized across its sections, or a generalized concept. */
    @GET
    @jakarta.ws.rs.Path("book-entity")
    @Produces(MediaType.TEXT_HTML)
    public String bookEntity(@QueryParam("book") String book, @QueryParam("id") String id) {
        if (book == null || id == null) return "<p class=\"hint\">No concept given.</p>";
        EntityPanel p = entityPanels(book).get(book);
        return p == null ? "<p class=\"hint\">No book " + Html.esc(book) + ".</p>" : p.entity(id);
    }

    /** @return the Book frames panel of each book, or of the one named, read from the database on every request */
    Map<String, EntityPanel> entityPanels(String only) {
        Map<String, EntityPanel> out = new java.util.LinkedHashMap<>();
        Path db = Path.of(booksDb).toAbsolutePath().normalize();
        if (!Files.isRegularFile(Path.of(db + ".mv.db"))) return out;
        try (var store = new com.scivicslab.turingworkflow.plugins.rstframe.BookStore(booksUrl())) {
            for (String b : store.books()) {
                if (only != null && !only.equals(b)) continue;
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> sectionItems = new java.util.HashMap<>();
                for (var e : store.frameItems(b).entrySet()) for (var c : e.getValue().entrySet()) sectionItems.put(e.getKey() + "\t" + c.getKey(), c.getValue());
                Map<String, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept> conceptOf = new java.util.HashMap<>();
                for (var e : store.frameConcepts(b).entrySet()) for (var c : e.getValue()) conceptOf.put(e.getKey() + "\t" + c.concept(), c);
                Map<String, String> files = new java.util.HashMap<>();
                java.util.function.Function<String, String> rulesOf = n -> files.computeIfAbsent(n, k -> {
                    try (var s2 = new com.scivicslab.turingworkflow.plugins.rstframe.BookStore(booksUrl())) {
                        var r = s2.sectionRules(b, k);
                        return r == null ? null : r.rules();
                    } catch (java.sql.SQLException ex) {
                        return null;
                    }
                });
                out.put(b, new EntityPanel(b, store.entities(b), store.entityItems(b), sectionItems, conceptOf, rulesOf));
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not read " + db + ": " + e.getMessage(), e);
        }
        return out;
    }

    private String booksUrl() {
        return "jdbc:h2:" + Path.of(booksDb).toAbsolutePath().normalize() + ";AUTO_SERVER=TRUE";
    }

    /**
     * Reads every book the H2 database of the intermediate data holds (book-toc.yaml and book-sections.yaml write it),
     * on every request, so that a run of either shows on the next click. No database yet means no book.
     */
    BookPanel bookPanel() {
        List<BookPanel.Book> books = new java.util.ArrayList<>();
        Path db = Path.of(booksDb).toAbsolutePath().normalize();
        if (Files.isRegularFile(Path.of(db + ".mv.db"))) {
            try (var store = new com.scivicslab.turingworkflow.plugins.rstframe.BookStore(booksUrl())) {
                for (String b : store.books()) {
                    books.add(new BookPanel.Book(b, store.dirOf(b), store.outline(b), store.sections(b), store.sectionsWithRules(b), store.judgedConcepts(b), store.relations(b), store.frameConcepts(b), store.patterns(b)));
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("could not read " + db + ": " + e.getMessage(), e);
            }
        }
        return new BookPanel(books, Path.of(worksDir).toAbsolutePath().normalize().toString());
    }

    /** The workflows tab's panel: one frame-making workflow. */
    @GET
    @jakarta.ws.rs.Path("workflow")
    @Produces(MediaType.TEXT_HTML)
    public String workflow(@QueryParam("name") String name) {
        if (name == null || name.isBlank()) return "<p class=\"hint\">No workflow given.</p>";
        return workflowPanel().render(name);
    }

    /** Reads the frame-making workflows named in the configuration, from their directory, on every request. */
    WorkflowPanel workflowPanel() {
        Path dir = Path.of(workflowsDir).toAbsolutePath().normalize();
        java.util.Map<String, String> texts = new java.util.LinkedHashMap<>();
        for (String w : workflows) {
            Path f = dir.resolve(w.strip() + ".yaml");
            if (Files.isRegularFile(f)) texts.put(w.strip(), read(f));
        }
        Path works = Path.of(worksDir).toAbsolutePath().normalize();
        String under = dir.startsWith(works) ? works.relativize(dir).toString().replace('\\', '/') : null;
        return new WorkflowPanel(workflows.stream().map(String::strip).toList(), texts, under);
    }

    /** The class tab's panel: one concept class, seen alone ({@code set} empty) or with one file of assignments. */
    @GET
    @jakarta.ws.rs.Path("concept-class")
    @Produces(MediaType.TEXT_HTML)
    public String conceptClass(@QueryParam("set") String set, @QueryParam("name") String name) {
        if (name == null || name.isBlank()) return "<p class=\"hint\">No class given.</p>";
        return classPanel().render(set == null ? "" : set, name);
    }

    /** Reads the class files, {@code concepts/**}, and the files of assignments, {@code assignments/**}, beside {@code frames/}. */
    ClassPanel classPanel() {
        Path root = Path.of(rstOnLispDir).toAbsolutePath().normalize();
        java.util.Map<String, String> library = new java.util.LinkedHashMap<>();
        for (Path p : lisp(root.resolve("concepts"))) library.put(root.relativize(p).toString().replace('\\', '/'), read(p));
        java.util.Map<String, String> results = new java.util.LinkedHashMap<>();
        for (Path p : lisp(root.resolve("assignments"))) results.put(root.relativize(p).toString().replace('\\', '/'), read(p));
        Path works = Path.of(worksDir).toAbsolutePath().normalize();
        String rstUnderWorks = root.startsWith(works) ? works.relativize(root).toString().replace('\\', '/') : null;
        return new ClassPanel(library, results, works.toString(), this::readUnderWorks, rstUnderWorks);
    }

    /** The left pane of the integrated-frame tab: the frame file as it is, with the integrated frames it names linked. */
    @GET
    @jakarta.ws.rs.Path("raw-integrated")
    @Produces(MediaType.TEXT_HTML)
    public String rawIntegrated(@QueryParam("path") String path) {
        if (path == null || path.isBlank()) return "<p class=\"hint\">No frame given.</p>";
        Library lib = load();
        return new RawPanel(lib, new Sources(lib, this::readUnderWorks), links()).integrated(path, readUnder(Path.of(rstOnLispDir, "frames"), path));
    }

    /**
     * The source pane of the integrated-frame and class tabs: one source file as it is; or, with {@code view=md}, the
     * markdown file beside it as it is.
     */
    @GET
    @jakarta.ws.rs.Path("raw-source")
    @Produces(MediaType.TEXT_HTML)
    public String rawSource(@QueryParam("file") String file, @QueryParam("frame") String frame, @QueryParam("view") String view) {
        if (file == null || file.isBlank()) return "<p class=\"hint\">No source given.</p>";
        Library lib = load();
        RawPanel panel = new RawPanel(lib, new Sources(lib, this::readUnderWorks), links());
        String f = frame == null ? "" : frame;
        if ("md".equals(view)) return panel.markdown(file, readUnderWorks(Sources.markdownBeside(file)), f);
        return panel.source(file, readUnderWorks(file), f);
    }

    /** Reads the edge lists, {@code links/**}{@code /*.tsv} beside {@code frames/} ({@code FrameLinks_261022_oo01}). */
    Links links() {
        List<String> texts = new java.util.ArrayList<>();
        Path dir = Path.of(rstOnLispDir).resolve("links");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.filter(p -> p.toString().endsWith(".tsv") && Files.isRegularFile(p)).sorted().toList()) texts.add(read(p));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new Links(texts);
    }

    /** Reads every {@code .lisp} under {@code frames/}. */
    Library load() {
        Library lib = new Library();
        Path root = Path.of(rstOnLispDir);
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
