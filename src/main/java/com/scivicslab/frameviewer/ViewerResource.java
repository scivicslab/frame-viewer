package com.scivicslab.frameviewer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The pages of the viewer, one per workflow, book, table-of-contents node and generalized frame, each with its own URL and
 * drawn by the server, and the few routes their scripts call (a closed branch of the tree, the registry's drawing). Every
 * link a page makes is relative: the AI workspace strips its own path prefix before a request reaches this tool.
 */
@jakarta.ws.rs.Path("/")
public class ViewerResource {

    /** When this process started: the version the page's links to its style sheet and scripts carry. */
    static final String STARTED = Long.toString(System.currentTimeMillis(), 36);

    /** The pages the server draws: the tabs above, the search box and the tree on the left, the page's text on the right. */
    @Inject
    Template page;

    @ConfigProperty(name = "frame-viewer.works-dir")
    String worksDir;

    @ConfigProperty(name = "frame-viewer.workflows-dir")
    String workflowsDir;

    @ConfigProperty(name = "frame-viewer.workflows")
    List<String> workflows;

    @ConfigProperty(name = "frame-viewer.books-db")
    String booksDb;

    /** What the search box of each tab looks in, by the tab's value of {@code in}. */
    static final Map<String, String> SEARCHED = Map.of(
            "workflows", "search the workflows: names, YAML text",
            "sections", "search the books: numbers, titles, text, concepts, rule files",
            "frames", "search the frames: ids, names, concepts, questions");

    /** The page at the top ({@code /}): the frame-making workflows, in the order they run. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response top() {
        String base = Html.base(1);
        WorkflowPanel w = workflowPanel();
        StringBuilder body = new StringBuilder("<h2>Workflows</h2><p class=\"hint\">The workflows that make frames from a book, in the order they run.</p><ol class=\"workflow-list\">");
        for (TreeNode n : w.tree()) {
            String d = n.li_attr().get("title");
            body.append("<li><a href=\"").append(Html.esc(base + Html.path("workflows", n.li_attr().get("data-workflow")))).append("\">")
                .append(Html.esc(n.li_attr().get("data-workflow"))).append("</a>").append(d == null || d.isEmpty() ? "" : " <span class=\"hint\">" + Html.esc(d) + "</span>")
                .append("</li>");
        }
        body.append("</ol>");
        return page(200, "workflows", "Workflows", "", workflowTree(w).html(null, base), body.toString(), base, null);
    }

    /** The page {@code workflows/<name>}: one frame-making workflow. */
    @GET
    @jakarta.ws.rs.Path("workflows/{name}")
    @Produces(MediaType.TEXT_HTML)
    public Response workflowPage(@PathParam("name") String name) {
        String base = Html.base(2);
        WorkflowPanel w = workflowPanel();
        NodeTree tree = workflowTree(w);
        if (!tree.has("w:" + name)) {
            return page(404, "workflows", "Not found", "", tree.html(null, base), "<p class=\"hint\">No workflow " + Html.esc(name) + ".</p>", base, null);
        }
        return page(200, "workflows", name, "", tree.html("w:" + name, base), w.render(name), base, null);
    }

    /** The workflows as the left tree: one item each, numbered in the order they run. */
    static NodeTree workflowTree(WorkflowPanel w) {
        return new NodeTree(w.tree(), n -> Html.path("workflows", n.li_attr().get("data-workflow")), null);
    }

    /** The page {@code frames}: every generalized frame of the registry and the relations between them, drawn. */
    @GET
    @jakarta.ws.rs.Path("frames")
    @Produces(MediaType.TEXT_HTML)
    public Response framesPage() {
        String base = Html.base(1);
        return withRegistry(r -> {
            String body = r.frames().isEmpty()
                    ? "<h2>Generalized frames</h2><p class=\"hint\">(no generalized frame yet: book-generalize.yaml has not been run)</p>"
                    : "<h2>Generalized frames</h2><p class=\"hint\">" + r.frames().size() + " generalized frames. Above, all of them and their relations; "
                            + "a frame clicked in the drawing or in the tree opens its page.</p>";
            return page(200, "frames", "Generalized frames", "", registryTree(r).html(null, base), body, base, "");
        });
    }

    /** The page {@code frames/<id>}: one generalized frame, its relations drawn above it with it in the middle. */
    @GET
    @jakarta.ws.rs.Path("frames/{id}")
    @Produces(MediaType.TEXT_HTML)
    public Response framePage(@PathParam("id") String id) {
        String base = Html.base(2);
        return withRegistry(r -> {
            NodeTree tree = registryTree(r);
            if (!r.has(id)) {
                return page(404, "frames", "Not found", "", tree.html(null, base), "<p class=\"hint\">No generalized frame " + Html.esc(id) + ".</p>", base, null);
            }
            return page(200, "frames", id + " " + tree.node("r:" + id).text(), "", tree.html("r:" + id, base), r.frame(id, base), base, id);
        });
    }

    /** The registry's is-a tree as the left tree: the frames at the top always drawn, a closed branch read from {@code tree/frames/<id>}. */
    static NodeTree registryTree(RegistryPanel r) {
        return new NodeTree(r.tree(), n -> n.li_attr().containsKey("data-frame-id") ? Html.path("frames", n.li_attr().get("data-frame-id")) : "frames",
                n -> n.li_attr().containsKey("data-frame-id") ? Html.path("tree", "frames", n.li_attr().get("data-frame-id")) : "frames");
    }

    /** The frames right under one generalized frame, for the left tree. */
    @GET
    @jakarta.ws.rs.Path("tree/frames/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response treeOfFrame(@PathParam("id") String id) {
        List<NodeTree.Entry> kids = withRegistry(r -> registryTree(r).children("r:" + id));
        if (kids == null) return Response.status(404).type(MediaType.APPLICATION_JSON_TYPE).entity(List.of()).build();
        return Response.ok(kids, MediaType.APPLICATION_JSON_TYPE).build();
    }

    /**
     * The page of a search's results in one tab ({@code search?in=workflows|sections|frames&q=...}): each item that holds
     * every word of {@code q}, a link to its page, with where it matched.
     */
    @GET
    @jakarta.ws.rs.Path("search")
    @Produces(MediaType.TEXT_HTML)
    public Response search(@QueryParam("in") String in, @QueryParam("q") String q) {
        Search s = new Search(q);
        String query = q == null ? "" : q.strip();
        String base = Html.base(1);
        String tab = SEARCHED.containsKey(in) ? in : "sections";
        String none = "<p class=\"hint\">Give the words to look for in the box above the tree.</p>";
        return switch (tab) {
            case "workflows" -> {
                WorkflowPanel w = workflowPanel();
                NodeTree tree = workflowTree(w);
                yield page(200, tab, "Search: " + query, query, tree.html(null, base),
                        s.blank() ? "<h2>Search the workflows</h2>" + none : tree.searchBody("Search the workflows", query, w.search(s), base), base, null);
            }
            case "frames" -> withRegistry(r -> {
                NodeTree tree = registryTree(r);
                return page(200, tab, "Search: " + query, query, tree.html(null, base),
                        s.blank() ? "<h2>Search the frames</h2>" + none : tree.searchBody("Search the frames", query, r.search(s), base), base, null);
            });
            default -> {
                // the books are taken once for the whole page: each take may read every book again
                BookPanel books = loaded().panel();
                yield page(200, tab, "Search: " + query, query, books.tree(null, null, base),
                        s.blank() ? "<h2>Search the books</h2>" + none : books.searchBody(query, searchSections(books, s), base), base, null);
            }
        };
    }

    /** The page {@code sections}: the books. */
    @GET
    @jakarta.ws.rs.Path("sections")
    @Produces(MediaType.TEXT_HTML)
    public Response sections() {
        String base = Html.base(1);
        BookPanel books = bookPanel();
        return page(200, "sections", "Books", "", books.tree(null, null, base), books.booksBody(base), base, null);
    }

    /** The page {@code sections/<book>}: one book and the nodes right under it. */
    @GET
    @jakarta.ws.rs.Path("sections/{book}")
    @Produces(MediaType.TEXT_HTML)
    public Response book(@PathParam("book") String book) {
        String base = Html.base(2);
        BookPanel books = bookPanel();
        if (!books.has(book, null)) return notFound(books, "No book " + book + " in the database.", base);
        return page(200, "sections", book, "", books.tree(book, null, base), books.bookBody(book, base), base, null);
    }

    /**
     * The page {@code sections/<book>/<number>}: one node of the book's table of contents, and the section of the same number
     * with what the workflows made of it; {@code view=rst} shows its rule file in place of its markdown.
     */
    @GET
    @jakarta.ws.rs.Path("sections/{book}/{number}")
    @Produces(MediaType.TEXT_HTML)
    public Response node(@PathParam("book") String book, @PathParam("number") String number, @QueryParam("view") String view) {
        String base = Html.base(3);
        Loaded l = loaded();
        BookPanel books = l.panel();
        if (!books.has(book, number)) return notFound(books, "No node " + number + " in " + book + ".", base);
        return page(200, "sections", number + " · " + book, "", books.tree(book, number, base), sectionBody(l, book, number, view, base), base, null);
    }

    /** One node of a book's left tree as the page's script reads it: its label, its page's path, and its children's path when it has any. */
    public record TreeEntry(String label, String path, boolean branch, String tree) {}

    /** The nodes right under a book, for the left tree: what its script puts in place when the book's branch is opened. */
    @GET
    @jakarta.ws.rs.Path("tree/sections/{book}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response treeOfBook(@PathParam("book") String book) {
        return treeEntries(book, null);
    }

    /** The nodes right under one node of a book, for the left tree. */
    @GET
    @jakarta.ws.rs.Path("tree/sections/{book}/{number}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response treeOfNode(@PathParam("book") String book, @PathParam("number") String number) {
        return treeEntries(book, number);
    }

    private Response treeEntries(String book, String number) {
        List<BookPanel.TocEntry> kids = bookPanel().children(book, number);
        if (kids == null) return Response.status(404).type(MediaType.APPLICATION_JSON_TYPE).entity(List.of()).build();
        return Response.ok(kids.stream().map(e -> new TreeEntry(e.label(), Html.path("sections", book, e.number()), e.branch(),
                e.branch() ? Html.path("tree", "sections", book, e.number()) : null)).toList(), MediaType.APPLICATION_JSON_TYPE).build();
    }

    private Response notFound(BookPanel books, String message, String base) {
        return page(404, "sections", "Not found", "", books.tree(null, null, base), "<p class=\"hint\">" + Html.esc(message) + "</p>", base, null);
    }

    /**
     * A page drawn with the template {@code page.html}, in the tab given.
     *
     * @param graph null for a page without the drawing of the registry; else the generalized frame drawn in its middle,
     *              or {@code ""} for every frame
     */
    private Response page(int status, String tab, String title, String q, String tree, String body, String base, String graph) {
        // the page builds code-server links from absolute paths, so it is told where the works directory is; its style sheet
        // and scripts carry the time this process started, so a restarted viewer is fetched anew
        String html = page.data("title", title).data("tab", tab).data("q", q).data("placeholder", SEARCHED.get(tab))
                .data("tree", tree).data("body", body).data("base", base).data("v", STARTED).data("graph", graph != null).data("focus", graph == null ? "" : graph)
                .data("worksDir", Path.of(worksDir).toAbsolutePath().normalize().toString()).render();
        return Response.status(status).type(MediaType.TEXT_HTML_TYPE.withCharset("UTF-8")).entity(html).build();
    }

    /**
     * The Sections tab's search: the books as {@link #loaded()} keeps them, and the sections' rule files read by their keys.
     * Building a second copy of every book for each search, beside the kept one, ran the viewer out of heap.
     */
    private List<Search.Hit> searchSections(BookPanel books, Search s) {
        if (!Files.isRegularFile(Path.of(Path.of(booksDb).toAbsolutePath().normalize() + ".mv.db"))) return List.of();
        return read(store -> books.searchSections(s, (b, n) -> {
            try {
                var r = store.sectionRules(b, n);
                return r == null ? null : r.rules();
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }));
    }

    /** The text of a node's page: the node, and its section with what the workflows made of it, read from the kept books and the database. */
    private String sectionBody(Loaded l, String book, String number, String view, String base) {
        // the rule file and the concepts' sentences: the concepts are shown with their rules beside them
        com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules = null;
        List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed = null;
        com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout pattern = null;
        Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items = Map.of();
        List<com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group> groups = null;
        List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptCauses.Link> causes = List.of();
        List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptProperties.Property> properties = List.of();
        List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptRelations.Relation> relations = List.of();
        List<BookPanel.Similar> similar = List.of();
        if (Files.isRegularFile(Path.of(Path.of(booksDb).toAbsolutePath().normalize() + ".mv.db"))) {
            Extras x = l.extras().get(book);
            rules = read(store -> store.sectionRules(book, number));
            if (x != null) {
                listed = x.listed().get(number);
                pattern = x.patterns().get(number);
                items = x.items().getOrDefault(number, Map.of());
                groups = x.groups().get(number);
                causes = x.causes().getOrDefault(number, List.of());
                properties = x.properties().getOrDefault(number, List.of());
                relations = x.relations().getOrDefault(number, List.of());
                // a pair judged from both sides is shown once
                similar = x.similar().getOrDefault(number, List.of()).stream().distinct().toList();
            }
        }
        return l.panel().section(book, number, "rst".equals(view) ? "rst" : "md", base, rules, listed, pattern, items,
                new BookPanel.FrameData(groups == null ? List.of() : groups, causes, properties, relations, similar));
    }

    /** The Relations tab's graph: the generalized frames of the registry, the is-a tree and the relations between frames. */
    @GET
    @jakarta.ws.rs.Path("registry-graph")
    @Produces(MediaType.APPLICATION_JSON)
    public RegistryPanel.Graph registryGraph() {
        return withRegistry(RegistryPanel::graph);
    }

    /**
     * Gives {@code use} the registry panel: the frames, their items and relations as {@link #loaded()} keeps them; a frame's
     * material and its sections' rule files only when a frame is shown, by their keys, over the viewer's one store.
     */
    <T> T withRegistry(java.util.function.Function<RegistryPanel, T> use) {
        Path db = Path.of(booksDb).toAbsolutePath().normalize();
        Loaded l = loaded();
        if (!Files.isRegularFile(Path.of(db + ".mv.db"))) return use.apply(new RegistryPanel(List.of(), Map.of(), List.of(), (b, k) -> null, (b, n) -> null));
        return read(store -> {
            Map<String, String> files = new java.util.HashMap<>();
            RegistryPanel panel = new RegistryPanel(l.registry().frames(), l.registry().items(), l.registry().relations(),
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
        });
    }

    /** A read of the database that may throw what JDBC throws. */
    @FunctionalInterface
    interface Read<T> {
        T from(com.scivicslab.turingworkflow.plugins.rstframe.BookStore store) throws java.sql.SQLException;
    }

    /** The viewer's one store of the database, opened for reading on the first read and kept while the viewer runs. */
    private com.scivicslab.turingworkflow.plugins.rstframe.BookStore store;

    /**
     * Reads the database through the viewer's one store, one read at a time. The store is opened for reading: it runs no
     * DDL, and refuses a database that is not at the schema version the plugin reads (BookImport migrate converts an old one).
     */
    synchronized <T> T read(Read<T> read) {
        try {
            if (store == null) store = com.scivicslab.turingworkflow.plugins.rstframe.BookStore.forReading(booksUrl());
            return read.from(store);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not read " + Path.of(booksDb).toAbsolutePath().normalize() + ": " + e.getMessage(), e);
        }
    }

    private String booksUrl() {
        // the database stays open while the viewer runs: closing the last connection made H2 close and compact the file on every
        // request, a second each time, and changed the file the cache of the books is keyed on
        return "jdbc:h2:" + Path.of(booksDb).toAbsolutePath().normalize() + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    }

    /**
     * Reads every book the H2 database of the intermediate data holds (book-toc.yaml and book-sections.yaml write it),
     * on every request, so that a run of either shows on the next click. No database yet means no book.
     */
    BookPanel bookPanel() {
        return loaded().panel();
    }

    /** What the panels of one book read of the database besides the book itself: each section's slice, by section number. */
    record Extras(Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept>> listed, Map<String, com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout> patterns,
            Map<String, Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>>> items, Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group>> groups,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptCauses.Link>> causes, Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptProperties.Property>> properties,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptRelations.Relation>> relations, Map<String, List<BookPanel.Similar>> similar) {}

    /** The registry of generalized frames: the frames with their concrete frames, their items, the relations between them. */
    record Registry(List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.RegistryFrame> frames,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items,
            List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameRelation> relations) {}

    /** The books and the registry as last read, and the database file's state they were read at. */
    record Loaded(String stamp, BookPanel panel, Map<String, Extras> extras, Registry registry) {}

    private volatile Loaded loaded;

    /**
     * Every book and the registry, read once and kept until the database's change count ({@code data_version}) moves: every
     * write of a workflow adds one, and its result shows on the next click. The file's time and size are not watched: H2
     * writes the file whenever a process opens it, so the books were read again on clicks that changed nothing. Reading
     * every book on every click filled the viewer's heap and made a page take seconds.
     */
    synchronized Loaded loaded() {
        Path db = Path.of(booksDb).toAbsolutePath().normalize();
        Path file = Path.of(db + ".mv.db");
        String stamp = Files.isRegularFile(file) ? "data version " + read(com.scivicslab.turingworkflow.plugins.rstframe.BookStore::dataVersion) : "none";
        Loaded l = loaded;
        if (l != null && l.stamp().equals(stamp)) return l;
        if (stamp.equals("none")) {
            l = new Loaded(stamp, new BookPanel(List.of(), Path.of(worksDir).toAbsolutePath().normalize().toString()), Map.of(),
                    new Registry(List.of(), Map.of(), List.of()));
        } else {
            l = read(store -> {
                Map<String, Extras> extras = new java.util.HashMap<>();
                // the similar concepts of each unit, judged from either side, by book and section
                Map<String, Map<String, List<BookPanel.Similar>>> similar = new java.util.HashMap<>();
                for (var r : store.similarities()) {
                    similar.computeIfAbsent(r.book(), k -> new java.util.HashMap<>()).computeIfAbsent(r.number(), k -> new java.util.ArrayList<>())
                            .add(new BookPanel.Similar(r.ordinal(), r.otherBook(), r.otherNumber(), r.otherConcept(), r.common()));
                    similar.computeIfAbsent(r.otherBook(), k -> new java.util.HashMap<>()).computeIfAbsent(r.otherNumber(), k -> new java.util.ArrayList<>())
                            .add(new BookPanel.Similar(r.otherOrdinal(), r.book(), r.number(), r.concept(), r.common()));
                }
                for (String b : store.books()) {
                    extras.put(b, new Extras(store.listedConcepts(b), store.patterns(b), store.frameItems(b), store.groups(b), store.causes(b),
                            store.properties(b), store.relationsOfConcepts(b),
                            similar.getOrDefault(b, Map.of())));
                }
                return new Loaded(stamp, bookPanel(store), extras, new Registry(store.registry(), store.registryItems(), store.frameRelations()));
            });
        }
        loaded = l;
        return l;
    }

    private BookPanel bookPanel(com.scivicslab.turingworkflow.plugins.rstframe.BookStore store) throws java.sql.SQLException {
        List<BookPanel.Book> books = new java.util.ArrayList<>();
        for (String b : store.books()) {
            books.add(new BookPanel.Book(b, store.dirOf(b), store.outline(b), store.sections(b), store.sectionsWithRules(b), store.judgedConcepts(b), store.relations(b), store.frameConcepts(b), store.patterns(b), store.groups(b)));
        }
        return new BookPanel(books, Path.of(worksDir).toAbsolutePath().normalize().toString());
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
