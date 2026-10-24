package com.scivicslab.frameviewer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
        // the page builds code-server links from absolute paths, so it is told where the works directory is
        return index.data("worksDir", Path.of(worksDir).toAbsolutePath().normalize().toString());
    }

    /** @param tab the tab whose tree is wanted: {@code workflows}, {@code contents}, {@code sections} or {@code integrated} */
    @GET
    @jakarta.ws.rs.Path("tree")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TreeNode> tree(@QueryParam("tab") String tab) {
        return switch (tab == null ? "workflows" : tab) {
            case "contents" -> bookPanel().contentsTree();
            case "sections" -> bookPanel().sectionsTree();
            case "integrated" -> withRegistry(RegistryPanel::tree);
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

    /** The Integrated frames tab's panel: one generalized frame of the registry. */
    @GET
    @jakarta.ws.rs.Path("registry-frame")
    @Produces(MediaType.TEXT_HTML)
    public String registryFrame(@QueryParam("id") String id) {
        if (id == null || id.isBlank()) return "<p class=\"hint\">No frame given.</p>";
        return withRegistry(p -> p.frame(id));
    }

    /** The Relations tab's graph: the generalized frames of the registry, the is-a tree and the relations between frames. */
    @GET
    @jakarta.ws.rs.Path("registry-graph")
    @Produces(MediaType.APPLICATION_JSON)
    public RegistryPanel.Graph registryGraph() {
        return withRegistry(RegistryPanel::graph);
    }

    /**
     * Gives {@code use} the registry panel, over one connection to the database for the whole request: the frames, their
     * items and relations are read at once; a frame's material and its sections' rule files only when a frame is shown,
     * by their keys.
     */
    <T> T withRegistry(java.util.function.Function<RegistryPanel, T> use) {
        Path db = Path.of(booksDb).toAbsolutePath().normalize();
        if (!Files.isRegularFile(Path.of(db + ".mv.db"))) return use.apply(new RegistryPanel(List.of(), Map.of(), List.of(), (b, k) -> null, (b, n) -> null));
        try (var store = new com.scivicslab.turingworkflow.plugins.rstframe.BookStore(booksUrl())) {
            Map<String, String> files = new java.util.HashMap<>();
            RegistryPanel panel = new RegistryPanel(store.registry(), store.registryItems(), store.frameRelations(),
                    (b, key) -> {
                        int tab = key.indexOf('\t');
                        try {
                            return store.frameConcept(b, key.substring(0, tab), key.substring(tab + 1));
                        } catch (java.sql.SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    },
                    (b, n) -> files.computeIfAbsent(b + "\t" + n, k -> {
                        try {
                            var r = store.sectionRules(b, n);
                            return r == null ? "" : r.rules();
                        } catch (java.sql.SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    }).transform(t -> t.isEmpty() ? null : t));
            return use.apply(panel);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not read " + db + ": " + e.getMessage(), e);
        }
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

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
