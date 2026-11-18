package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.scivicslab.turingworkflow.plugins.rstframe.BookOutline;
import com.scivicslab.turingworkflow.plugins.rstframe.BookSections;

/**
 * The Sections tab ({@code FrameOntology_261021_oo01}, getting the concept hierarchy): a book's table of
 * contents as {@code book-toc.yaml} stored it as a tree, and its text cut into those nodes as {@code book-sections.yaml} stored it.
 * Pure: it is given what the H2 database holds.
 */
public final class BookPanel {

    /**
     * One book as the database holds it: its name, its directory, its table of contents, its sections, which sections
     * have a rule file, the concepts each section explains, how those concepts relate (steps 6 and 7), and the
     * material of each section's concrete frame, and how each section explains its main concept.
     */
    public record Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections,
            java.util.Set<String> withRules, Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept>> concepts,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> relations,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept>> frame,
            Map<String, com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout> patterns,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group>> groups) {

        /** A book whose sections have not been split into groups. */
        public Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections, java.util.Set<String> withRules,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept>> concepts,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> relations,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept>> frame,
                Map<String, com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout> patterns) {
            this(name, dir, outline, sections, withRules, concepts, relations, frame, patterns, Map.of());
        }

        /** A book whose sections have not been asked how they explain their main concept. */
        public Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections, java.util.Set<String> withRules,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept>> concepts,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> relations,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept>> frame) {
            this(name, dir, outline, sections, withRules, concepts, relations, frame, Map.of());
        }

        /** A book with no concrete-frame material yet. */
        public Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections, java.util.Set<String> withRules,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept>> concepts,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> relations) {
            this(name, dir, outline, sections, withRules, concepts, relations, Map.of());
        }
    }

    /** A unit of this section judged similar to a unit elsewhere: the unit's ordinal, the other unit, and the more general concept they share. */
    public record Similar(int ordinal, String otherBook, String otherNumber, String otherConcept, String common) {}

    /**
     * What the frame data workflow read of one section: its smallest units of explanation (groups), and the causes and
     * motives, the properties, the relations to other concepts and the similar concepts elsewhere of their main concepts.
     */
    public record FrameData(List<com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group> groups, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptCauses.Link> causes, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptProperties.Property> properties, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptRelations.Relation> relations, List<Similar> similar) {
        public static final FrameData NONE = new FrameData(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** One node of a book's table of contents as the left tree shows it: its number, its label, and whether nodes stand under it. */
    public record TocEntry(String book, String number, String label, boolean branch) {}

    /** One book's table of contents as a tree: the numbers under each number ({@code ""} for the book), and each number's parent. */
    private record Toc(Map<String, List<String>> under, Map<String, String> parent, Map<String, BookOutline.Node> nodes,
            Map<String, BookSections.Section> sections) {}

    private final Map<String, Book> books = new LinkedHashMap<>();
    private final Map<String, Toc> tocs = new java.util.HashMap<>();
    private final String worksDir;

    /** @param worksDir the works directory, absolute, to give a page file a path under it for its edit link */
    public BookPanel(List<Book> books, String worksDir) {
        for (Book b : books) {
            this.books.put(b.name(), b);
            tocs.put(b.name(), toc(b));
        }
        this.worksDir = worksDir.endsWith("/") ? worksDir : worksDir + "/";
    }

    /**
     * A book's table of contents as a tree: its parts, chapters, sections and subsections under their parents, in the
     * order of the table of contents. A section the table of contents does not name (a big section cut into pieces,
     * {@code 4.2.p1}) stands under the node whose number is its own with the last piece dropped, or else under the book.
     */
    private static Toc toc(Book b) {
        Map<String, BookOutline.Node> nodes = nodes(b);
        Map<String, BookSections.Section> sections = new LinkedHashMap<>();
        for (BookSections.Section s : b.sections()) sections.put(s.number(), s);
        java.util.Set<String> known = new java.util.HashSet<>(nodes.keySet());
        known.addAll(sections.keySet());
        Map<String, List<String>> under = new java.util.HashMap<>();
        Map<String, String> parent = new java.util.HashMap<>();
        java.util.function.BiConsumer<String, String> put = (number, declared) -> {
            String p = parentOf(number, declared, known);
            parent.put(number, p);
            under.computeIfAbsent(p, k -> new ArrayList<>()).add(number);
        };
        for (BookOutline.Node n : b.outline()) put.accept(n.number(), n.parent());
        for (BookSections.Section s : b.sections()) if (!nodes.containsKey(s.number())) put.accept(s.number(), null);
        return new Toc(under, parent, nodes, sections);
    }

    /**
     * The number a number stands under: its parent in the table of contents when that parent is a node of the tree;
     * else the nearest node whose number is the number with its last parts dropped ({@code 2.2.3} under {@code 2.2}, or
     * under {@code 2} when the table of contents has no 2.2); else the book, {@code ""}.
     */
    private static String parentOf(String number, String declared, java.util.Set<String> known) {
        if (declared != null && known.contains(declared)) return declared;
        for (String up = number; up.indexOf('.') > 0; ) {
            up = up.substring(0, up.lastIndexOf('.'));
            if (known.contains(up)) return up;
        }
        return "";
    }

    /** The books in the database, in the order it lists them. */
    public List<String> books() {
        return List.copyOf(books.keySet());
    }

    /** Whether the book holds a node of its table of contents, or a section, with this number; with a null number, whether the book is there. */
    public boolean has(String book, String number) {
        Toc t = tocs.get(book);
        return t != null && (number == null || t.parent().containsKey(number));
    }

    /**
     * The nodes that stand right under a number of a book's table of contents, or under the book itself when the number is
     * {@code null}: what the left tree shows when that branch is opened. {@code null} when there is no such book or number.
     */
    public List<TocEntry> children(String book, String number) {
        Book b = books.get(book);
        if (b == null || (number != null && !has(book, number))) return null;
        Toc t = tocs.get(book);
        List<TocEntry> out = new ArrayList<>();
        for (String k : t.under().getOrDefault(number == null ? "" : number, List.of())) {
            out.add(new TocEntry(book, k, label(b, t, k), t.under().containsKey(k)));
        }
        return out;
    }

    /** The numbers above a number, from the top of the book down, without the number itself. */
    private List<String> above(String book, String number) {
        Toc t = tocs.get(book);
        List<String> up = new ArrayList<>();
        for (String p = t.parent().get(number); p != null && !p.isEmpty(); p = t.parent().get(p)) up.add(0, p);
        return up;
    }

    /** A node's label in the tree: a section's label when it has text, else its number and title. */
    private String label(Book b, Toc t, String number) {
        BookOutline.Node n = t.nodes().get(number);
        BookSections.Section s = t.sections().get(number);
        return s != null ? sectionLabel(b, s, n == null ? "" : n.title()) : number + " " + n.title();
    }

    /**
     * The left tree of a Sections page, as HTML: the books when no book is given; else the book's table of contents with
     * only the branch down to {@code number} open -- every node at each level of that branch, and the nodes right under
     * {@code number} -- and {@code number} marked. A closed branch carries the path of its children ({@code tree/...}), which
     * the page's script reads when it is opened; every node is a link to its page.
     *
     * @param base the {@code ../} leading from the page back to the top
     */
    public String tree(String book, String number, String base) {
        StringBuilder sb = new StringBuilder();
        if (book == null || !books.containsKey(book)) {
            sb.append("<ul class=\"toc\">");
            for (Book b : books.values()) {
                sb.append("<li class=\"branch\"><span class=\"twisty\" data-tree=\"").append(Html.esc(Html.path("tree", "sections", b.name())))
                  .append("\"></span><a href=\"").append(Html.esc(base + Html.path("sections", b.name()))).append("\">").append(Html.esc(b.name()))
                  .append("</a> <span class=\"hint\">").append(b.sections().size()).append(" sections</span></li>");
            }
            return sb.append("</ul>").toString();
        }
        List<String> open = number == null ? List.of() : new ArrayList<>(above(book, number));
        if (number != null) open.add(number);
        sb.append("<div class=\"toc-head\"><a href=\"").append(Html.esc(base + "sections")).append("\">all books</a> › <a href=\"")
          .append(Html.esc(base + Html.path("sections", book))).append("\"").append(number == null ? " class=\"current\"" : "").append(">")
          .append(Html.esc(book)).append("</a></div>");
        treeLevel(book, null, open, 0, number, base, sb);
        return sb.toString();
    }

    private void treeLevel(String book, String under, List<String> open, int depth, String current, String base, StringBuilder sb) {
        sb.append("<ul class=\"toc\">");
        for (TocEntry e : children(book, under)) {
            boolean isOpen = depth < open.size() && open.get(depth).equals(e.number());
            boolean expanded = isOpen && e.branch();
            sb.append("<li class=\"").append(e.branch() ? (expanded ? "branch open" : "branch") : "leaf").append("\">");
            if (e.branch()) sb.append("<span class=\"twisty\" data-tree=\"").append(Html.esc(Html.path("tree", "sections", book, e.number()))).append("\"></span>");
            sb.append("<a href=\"").append(Html.esc(base + Html.path("sections", book, e.number()))).append("\"")
              .append(e.number().equals(current) ? " class=\"current\"" : "").append(">").append(Html.esc(e.label())).append("</a>");
            if (expanded) treeLevel(book, e.number(), open, depth + 1, current, base, sb);
            sb.append("</li>");
        }
        sb.append("</ul>");
    }

    /** The page {@code sections}: every book, each a link to its page. */
    public String booksBody(String base) {
        StringBuilder sb = new StringBuilder("<h2>Books</h2>");
        if (books.isEmpty()) return sb.append("<p class=\"hint\">(no book in the database yet: book-toc.yaml has not been run)</p>").toString();
        sb.append("<ul class=\"book-list\">");
        for (Book b : books.values()) {
            sb.append("<li><a href=\"").append(Html.esc(base + Html.path("sections", b.name()))).append("\">").append(Html.esc(b.name()))
              .append("</a> <span class=\"hint\">").append(b.sections().size()).append(" sections</span></li>");
        }
        return sb.append("</ul>").toString();
    }

    /** The page {@code sections/<book>}: where the book is kept, and the nodes right under it, each a link to its page. */
    public String bookBody(String book, String base) {
        Book b = books.get(book);
        StringBuilder sb = new StringBuilder("<h2>").append(Html.esc(book)).append("</h2>");
        sb.append("<div class=\"raw-path\">").append(Html.esc(b.dir())).append(" · ").append(b.sections().size()).append(" sections</div>");
        sb.append("<h3>Under it</h3>");
        List<TocEntry> top = children(book, null);
        if (top.isEmpty()) sb.append("<p class=\"hint\">(no table of contents)</p>");
        for (TocEntry e : top) sb.append("<div class=\"child\">").append(Html.sectionLink(base, book, e.number(), e.label())).append("</div>");
        return sb.toString();
    }

    /** The section a search hit names, by its id {@code s:<book>|<number>}: the book and the number. */
    static String[] bookAndNumber(String id) {
        int bar = id.indexOf('|');
        return new String[] {id.substring(2, bar), id.substring(bar + 1)};
    }

    /** The page {@code search?in=sections}: each node that holds every word, a link to its page, with where the words were found. */
    public String searchBody(String q, List<Search.Hit> hits, String base) {
        StringBuilder sb = new StringBuilder("<h2>Search the books: ").append(Html.esc(q)).append("</h2>");
        sb.append("<p class=\"hint\">").append(hits.isEmpty() ? "none found" : hits.size() + " found").append("</p><ul class=\"search-hits\">");
        for (Search.Hit h : hits) {
            String[] bn = bookAndNumber(h.id());
            Book b = books.get(bn[0]);
            sb.append("<li><span class=\"hint\">").append(Html.esc(bn[0])).append("</span> ")
              .append(Html.sectionLink(base, bn[0], bn[1], label(b, tocs.get(bn[0]), bn[1]))).append(" <span class=\"hint\">in ")
              .append(Html.esc(h.where())).append("</span></li>");
        }
        return sb.append("</ul>").toString();
    }

    private static String sectionId(String book, String number) {
        return "s:" + book + "|" + number;
    }

    /** The label of a section in the tree: its number and title, then its units if the frame data was read, else its length and what was read of it. */
    private String sectionLabel(Book b, BookSections.Section s, String title) {
        var gs = b.groups().get(s.number());
        return gs != null && !gs.isEmpty() ? s.number() + " " + title + "  · " + gs.size() + " unit" + (gs.size() > 1 ? "s" : "") + ": "
                + String.join("; ", gs.stream().map(g -> g.type() + " of " + (g.concept() == null ? "?" : g.concept())).toList())
                : s.number() + " " + title + "  · " + s.text().length() + " chars" + (s.files().size() > 1 ? ", " + s.files().size() + " files" : "")
                + (b.withRules().contains(s.number()) ? " · rst" : "")
                + (b.concepts().containsKey(s.number()) ? " · " + conceptCount(b.concepts().get(s.number())) : "")
                + (b.relations().containsKey(s.number()) ? " · " + b.relations().get(s.number()).size() + " relations" : "")
                + (b.patterns().containsKey(s.number()) ? " · " + b.patterns().get(s.number()).pattern() + " of "
                        + b.patterns().get(s.number()).concept() : "");
    }

    /**
     * @param rulesOf the rule file of a section, by book and number; {@code null} when it has none
     * @return the sections whose number and title, or else whose text and concepts, or else whose rule file holds every
     *         word searched for, each with the first of those it matched in
     */
    public List<Search.Hit> searchSections(Search q, java.util.function.BiFunction<String, String, String> rulesOf) {
        List<Search.Hit> out = new ArrayList<>();
        for (Book b : books.values()) {
            Map<String, BookOutline.Node> nodes = nodes(b);
            java.util.Set<String> withText = new java.util.HashSet<>();
            for (BookSections.Section s : b.sections()) withText.add(s.number());
            for (BookOutline.Node n : b.outline()) {
                if (!withText.contains(n.number()) && q.in(n.number() + " " + n.title())) out.add(new Search.Hit(sectionId(b.name(), n.number()), "title"));
            }
            for (BookSections.Section s : b.sections()) {
                String id = sectionId(b.name(), s.number());
                BookOutline.Node n = nodes.get(s.number());
                StringBuilder concepts = new StringBuilder();
                for (var c : b.concepts().getOrDefault(s.number(), List.of())) concepts.append(c.concept()).append('\n');
                for (var c : b.frame().getOrDefault(s.number(), List.of())) concepts.append(c.concept()).append('\n');
                if (q.in(s.number() + " " + (n == null ? "" : n.title()))) out.add(new Search.Hit(id, "title"));
                else if (q.in(s.text(), concepts.toString())) out.add(new Search.Hit(id, "text"));
                else if (b.withRules().contains(s.number()) && q.in(rulesOf.apply(b.name(), s.number()))) out.add(new Search.Hit(id, "rules"));
            }
        }
        return out;
    }

    /**
     * The page of one node of a book's table of contents ({@code sections/<book>/<number>}): where it stands and what is under
     * it, then the section of the same number, with the page files it was read from, the concepts it explains (step 4),
     * and either its markdown text ({@code view} {@code md}) or its rst-on-lisp rule file with its code files ({@code rst}).
     *
     * @param rules the section's rule file, or {@code null} when it has none yet
     */
    public String section(String book, String number, String view, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules) {
        return section(book, number, view, rules, null);
    }

    /**
     * As {@link #section(String, String, String, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules)},
     * with the sentences step 4 said explain each concept: the concepts are a table, each with the rst-on-lisp rules of
     * those sentences beside it.
     *
     * @param listed the section's concepts with their sentence numbers, or {@code null} to show the concepts alone
     */
    public String section(String book, String number, String view, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules,
            List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed) {
        return section(book, number, view, rules, listed, null);
    }

    /**
     * As {@link #section(String, String, String, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules, List)},
     * first saying how the section explains its main concept: by a sequence of steps, by its structure, or by a
     * partition into kinds, or by one aspect after another, with the blocks that are its steps, parts, kinds or aspects.
     *
     * @param pattern how the section explains its main concept, or {@code null} when it has not been asked
     */
    public String section(String book, String number, String view, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules,
            List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed,
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout pattern) {
        return section(book, number, view, rules, listed, pattern, Map.of());
    }

    /**
     * As {@link #section(String, String, String, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules, List,
     * com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout)}, with each concrete-frame concept's items: the
     * questions its explanation answers, each with the rules that answer it.
     *
     * @param items the items of each concept of the section, by concept
     */
    public String section(String book, String number, String view, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules,
            List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed,
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout pattern,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items) {
        return section(book, number, view, rules, listed, pattern, items, FrameData.NONE);
    }

    /**
     * As {@link #section(String, String, String, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules, List,
     * com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout, Map)}, with the groups its blocks are split
     * into (step 1 of the frame data workflow), shown first.
     *
     * @param groups the section's groups in order, or {@code null} when it has not been split
     */
    public String section(String book, String number, String view, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules,
            List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed,
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout pattern,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items,
            List<com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group> groups) {
        return section(book, number, view, rules, listed, pattern, items, groups == null ? FrameData.NONE : new FrameData(groups, List.of(), List.of(), List.of(), List.of()));
    }

    /** As the method below, on the page {@code sections/<book>/<number>}, two levels below the top. */
    public String section(String book, String number, String view, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules,
            List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed,
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout pattern,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items,
            FrameData data) {
        return section(book, number, view, Html.base(3), rules, listed, pattern, items, data);
    }

    /**
     * The page of one node, with all the frame data workflow read of its section: its units, and their causes and motives
     * and properties.
     *
     * @param base the {@code ../} leading from the page back to the top, put before every link to another page
     */
    public String section(String book, String number, String view, String base, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules,
            List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept> listed,
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout pattern,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items,
            FrameData data) {
        var groups = data.groups();
        Book b = books.get(book);
        if (b == null) return "<p class=\"hint\">No book " + Html.esc(book) + " in the database.</p>";
        if (!has(book, number)) return "<p class=\"hint\">No node " + Html.esc(number) + " in " + Html.esc(book) + ".</p>";
        Toc t = tocs.get(book);
        BookSections.Section s = t.sections().get(number);
        BookOutline.Node n = t.nodes().get(number);
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Html.esc(number + " " + (n == null ? "" : n.title()))).append("</h2>");
        sb.append("<div class=\"raw-path\"><a href=\"").append(Html.esc(base + Html.path("sections", book))).append("\">").append(Html.esc(book)).append("</a>")
          .append(n == null ? "" : " · " + Html.esc(n.level())).append(s == null ? "" : " · " + s.text().length() + " chars").append("</div>");
        // where it stands in the table of contents, and what stands under it, as the left tree shows them
        sb.append("<h3>Where it stands</h3><div class=\"child\"><a href=\"").append(Html.esc(base + Html.path("sections", book))).append("\">")
          .append(Html.esc(book)).append("</a>");
        for (String up : above(book, number)) sb.append(" → ").append(Html.sectionLink(base, book, up, up + " " + titleOf(t, up)));
        sb.append("</div>");
        List<TocEntry> kids = children(book, number);
        if (!kids.isEmpty()) {
            sb.append("<h3>Under it</h3>");
            for (TocEntry k : kids) sb.append("<div class=\"child\">").append(Html.sectionLink(base, book, k.number(), k.number() + " " + titleOf(t, k.number()))).append("</div>");
        }
        if (s == null) {
            sb.append("<h3>Its text</h3><p class=\"hint\">").append(b.sections().isEmpty()
                    ? "(book-sections.yaml has not been run for this book)" : "(no text of its own: the nodes under it hold it)").append("</p>");
            if (n != null) sb.append("<h3>First found in</h3><div class=\"raw-path\">").append(Html.esc(n.firstFile())).append("</div>");
            return sb.toString();
        }
        sb.append("<h3>Read from</h3>");
        for (String f : s.files()) {
            String abs = b.dir().endsWith("/") ? b.dir() + f : b.dir() + "/" + f;
            sb.append("<div class=\"raw-path\">").append(Html.esc(f));
            if (abs.startsWith(worksDir)) sb.append(' ').append(Html.editWorksLink(abs.substring(worksDir.length())));
            sb.append("</div>");
        }
        boolean grouped = groups != null && !groups.isEmpty();
        if (grouped) {
            structure(data, rules, base, sb);
            sb.append("<details class=\"earlier\"><summary>Results of the earlier steps: the main concept and each block's relation to it, "
                    + "the concepts listed, how they relate, the concrete frame material</summary>");
        }
        if (pattern != null) pattern(pattern, rules, sb);
        sb.append("<h3>The concepts it explains</h3>");
        var concepts = b.concepts().get(number);
        if (concepts == null) sb.append("<p class=\"hint\">(not listed yet)</p>");
        else if (concepts.isEmpty()) sb.append("<p class=\"hint\">(none)</p>");
        else {
            // step 4 listed them, each with the sentences that explain it; step 5 kept or dropped each, by those sentences' roles.
            // Beside each concept, the rst-on-lisp rules of its sentences, as the section's rule file (step 3) holds them.
            Map<String, List<Integer>> at = new java.util.HashMap<>();
            if (listed != null) for (var l : listed) if (l.sentences() != null) at.put(l.concept(), l.sentences());
            List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptImportance.Unit> units = rules == null ? List.of()
                    : com.scivicslab.turingworkflow.plugins.rstframe.ConceptImportance.units(rules.rules(), rules.code());
            sb.append("<table class=\"concepts\"><tr><th>concept</th><th>its rst-on-lisp rules</th></tr>");
            for (var c : concepts) {
                boolean dropped = Boolean.FALSE.equals(c.kept());
                sb.append("<tr").append(dropped ? " class=\"dropped\"" : "").append("><td class=\"concept\">").append(Html.esc(c.concept()));
                if (c.reason() != null) sb.append(" <span class=\"hint\">(").append(Html.esc(c.reason())).append(")</span>");
                sb.append("</td><td class=\"rules\">");
                java.util.Set<String> shown = new java.util.HashSet<>();
                for (int k : at.getOrDefault(c.concept(), List.of())) {
                    if (k < 1 || k > units.size()) continue;
                    var u = units.get(k - 1);
                    if (u.rule() != null && !shown.add(u.rule())) continue;
                    sb.append(ruleForm(rules.rules(), u.rule(), u.text()));
                    // a code block's rule names its file; the code itself follows
                    if (u.role().equals("code")) sb.append("<pre class=\"rule-code\">").append(Html.esc(clip(u.text().strip()))).append("</pre>");
                }
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }
        relations(b, number, concepts, sb);
        frame(b.frame().get(number), rules == null ? null : rules.rules(), items == null ? Map.of() : items, sb);
        if (grouped) sb.append("</details>");
        boolean rst = "rst".equals(view);
        // the two views are two URLs of the same page: the markdown with no query, the rule file with ?view=rst
        String here = base + Html.path("sections", book, number);
        sb.append(grouped ? "<h3>Its source <span class=\"raw-views\">" : "<h3>Its text <span class=\"raw-views\">")
          .append("<a href=\"").append(Html.esc(here)).append("\" class=\"raw-view").append(rst ? "" : " active").append("\">markdown</a>")
          .append("<a href=\"").append(Html.esc(here + "?view=rst")).append("\" class=\"raw-view").append(rst ? " active" : "").append("\">rst-on-lisp</a></span></h3>");
        if (!rst) {
            if (s.text().isEmpty()) sb.append("<p class=\"hint\">(no text of its own: its subsections hold it)</p>");
            else sb.append("<pre class=\"raw md\">").append(Html.esc(s.text())).append("</pre>");
        } else if (rules == null) {
            sb.append("<p class=\"hint\">(no rule file yet: step 3 has not made this section into rst-on-lisp)</p>");
        } else {
            sb.append("<div class=\"raw-path\">").append(rules.questions()).append(" questions asked</div>");
            sb.append("<pre class=\"raw lisp\">").append(Html.esc(rules.rules())).append("</pre>");
            for (var e : rules.code().entrySet()) {
                sb.append("<div class=\"raw-path\">").append(Html.esc(e.getKey())).append("</div><pre class=\"raw\">")
                  .append(Html.esc(e.getValue())).append("</pre>");
            }
        }
        return sb.toString();
    }

    /** How a group explains its main concept, by its type. */
    private static final Map<String, String> GROUP_TYPES = Map.of(
            com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.SEQUENCE, "its blocks go through it in order",
            com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.STRUCTURE, "its blocks take it apart into its parts",
            com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.SET, "its blocks take up its members one by one");

    /** The relations of cause and motive, which the writing steps ask after first; their sentences are marked. */
    private static final java.util.Set<String> CAUSAL = java.util.Set.of("cause", "result", "reason", "consequence-n", "consequence-s",
            "purpose", "enablement", "means", "problem-solution-n", "problem-solution-s", "explanation-argumentative");

    /**
     * The section read through its structure ({@code FrameRethink_261024_oo01}): its groups in order, the section level;
     * under each group its blocks with what each is in the group, and each block's sentences with their relation to its
     * nucleus, the paragraph level, cause and motive marked; and what the group's main concept is tied to, the concept
     * level. A block in no group stands where it is, marked as left out.
     */
    private static void structure(FrameData data, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules, String base, StringBuilder sb) {
        var groups = data.groups();
        var causes = data.causes();
        var properties = data.properties();
        var blocks = rules == null ? List.<com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Block>of()
                : com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.blocks(rules.rules(), rules.code());
        Map<String, com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group> groupOf = new java.util.HashMap<>();
        for (var g : groups) for (var m : g.blocks()) groupOf.put(m.block(), g);
        sb.append("<h3>Its structure</h3><p class=\"hint\">The section split into the smallest units of explanation, each a run of blocks explaining one subject along one axis "
                + "(sequence, structure or set); each block's sentences with their relation to its nucleus (cause and motive marked); "
                + "what each unit's main concept is tied to.</p>");
        com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group open = null;
        for (var bl : blocks) {
            var g = groupOf.get(bl.name());
            if (open != null && g != open) {
                sb.append("</div>");
                concepts(open, causes, properties, data.relations(), data.similar(), base, sb);
                sb.append("</div>");
                open = null;
            }
            if (g != null && g != open) {
                open = g;
                sb.append("<div class=\"group\"><div class=\"group-head\"><span class=\"group-range\">")
                  .append(Html.esc(g.first() + (g.first().equals(g.last()) ? "" : "–" + g.last()))).append("</span> <b>")
                  .append(Html.esc(g.concept() == null ? "(not named)" : g.concept())).append("</b> <span class=\"group-type\">")
                  .append(Html.esc(g.type())).append("</span> <span class=\"hint\">").append(Html.esc(GROUP_TYPES.getOrDefault(g.type(), "")))
                  .append("</span></div><div class=\"group-blocks\">");
            }
            block(bl, g, sb);
        }
        if (open != null) {
            sb.append("</div>");
            concepts(open, causes, properties, data.relations(), data.similar(), base, sb);
            sb.append("</div>");
        }
    }


    /** One block: its name, what it is in its group, and its sentences, each with its relation. */
    private static void block(com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Block bl,
            com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group g, StringBuilder sb) {
        List<String> names = g == null ? List.of() : g.blocks().stream().filter(m -> m.block().equals(bl.name()) && m.name() != null && !m.name().isEmpty())
                .map(com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Member::name).toList();
        sb.append("<div class=\"block").append(g == null ? " left-out" : "").append("\"><div class=\"block-head\">").append(Html.esc(bl.name()));
        if (!names.isEmpty()) sb.append(" <i>").append(Html.esc(String.join("; ", names))).append("</i>");
        if (g == null) sb.append(" <span class=\"hint\">in no unit</span>");
        sb.append("</div><ul class=\"sentences\">");
        String text = bl.text();
        int code = text.indexOf("(code)");
        if (code == 0) {
            sb.append("<li><pre class=\"rule-code\">").append(Html.esc(clip(text.substring(6).strip()))).append("</pre></li></ul></div>");
            return;
        }
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-z][a-z-]*): (.*)$").matcher(line);
            if (m.matches()) {
                String rel = m.group(1);
                sb.append("<li class=\"").append(CAUSAL.contains(rel) ? "causal" : rel.equals("nucleus") ? "nucleus" : "satellite")
                  .append("\"><span class=\"rel\">").append(Html.esc(rel)).append("</span> ").append(Html.esc(clip(m.group(2)))).append("</li>");
            } else if (line.startsWith("- ")) {
                sb.append("<li class=\"item\"><span class=\"rel\">item</span> ").append(Html.esc(clip(line.substring(2)))).append("</li>");
            } else {
                sb.append("<li class=\"nucleus\">").append(Html.esc(clip(line))).append("</li>");
            }
        }
        sb.append("</ul></div>");
    }

    /** What a unit's main concept is tied to: its causes and motives (step 2), its relations to other concepts (step 2b), its properties (step 3), each with where it was read. */
    private static void concepts(com.scivicslab.turingworkflow.plugins.rstframe.SectionGroups.Group g, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptCauses.Link> causes, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptProperties.Property> properties, List<com.scivicslab.turingworkflow.plugins.rstframe.ConceptRelations.Relation> relations, List<Similar> similar, String base, StringBuilder sb) {
        var mine = causes.stream().filter(l -> l.group() == g.ordinal()).toList();
        sb.append("<div class=\"group-concepts\"><b>").append(Html.esc(g.concept() == null ? "its main concept" : g.concept())).append("</b><div>causes and motives ");
        if (mine.isEmpty()) sb.append("<span class=\"hint\">(none found)</span></div>");
        else {
            sb.append("</div><ul class=\"causes\">");
            for (var l : mine) {
                sb.append("<li><span class=\"cause-from\">").append(Html.esc(l.from())).append("</span> <span class=\"cause-kind\">")
                  .append(l.kind().equals("motive") ? "is the motive for" : "leads to").append("</span> <span class=\"cause-to\">")
                  .append(Html.esc(l.to())).append("</span> <span class=\"hint\">").append(Html.esc(l.relation() + ": " + l.satelliteRule() + " → " + l.nucleusRule()))
                  .append("</span></li>");
            }
            sb.append("</ul>");
        }
        var rels = relations.stream().filter(x -> x.group() == g.ordinal()).toList();
        sb.append("<div class=\"rels-head\">related concepts ");
        if (rels.isEmpty()) sb.append("<span class=\"hint\">(none found)</span></div>");
        else {
            sb.append("</div><ul class=\"rels\">");
            for (var x : rels) {
                sb.append("<li><span class=\"rel-from\">").append(Html.esc(x.from())).append("</span> <span class=\"rel-link\">").append(Html.esc(x.link()))
                  .append("</span> <span class=\"rel-to\">").append(Html.esc(x.to())).append("</span> <span class=\"hint\">")
                  .append(Html.esc(x.source().equals("unit") ? "the unit's " + (x.link().equals("part of") ? "part" : "member") + ", " + x.rule() : x.source() + ": " + x.rule()))
                  .append("</span></li>");
            }
            sb.append("</ul>");
        }
        var sims = similar.stream().filter(x -> x.ordinal() == g.ordinal()).toList();
        sb.append("<div class=\"sims-head\">similar concepts ");
        if (sims.isEmpty()) sb.append("<span class=\"hint\">(none found)</span></div>");
        else {
            sb.append("</div><ul class=\"sims\">");
            for (var x : sims) {
                sb.append("<li><span class=\"hint\">").append(Html.esc(x.otherBook())).append("</span> ")
                  .append(Html.sectionLink(base, x.otherBook(), x.otherNumber(), x.otherNumber())).append(" <span class=\"sim-concept\">")
                  .append(Html.esc(x.otherConcept())).append("</span> <span class=\"hint\">both ").append(Html.esc(x.common()))
                  .append("</span></li>");
            }
            sb.append("</ul>");
        }
        var props = properties.stream().filter(x -> x.group() == g.ordinal()).toList();
        sb.append("<div class=\"props-head\">properties ");
        if (props.isEmpty()) sb.append("<span class=\"hint\">(none found)</span></div>");
        else {
            sb.append("</div><ul class=\"props\">");
            for (var x : props) {
                sb.append("<li><span class=\"prop-kind\">").append(Html.esc(x.kind())).append("</span> ").append(Html.esc(x.property()))
                  .append(" <span class=\"hint\">").append(Html.esc(x.rule())).append("</span></li>");
            }
            sb.append("</ul>");
        }
        sb.append("</div>");
    }

    /** What each pattern says of the blocks: the relation most of them bear to the main concept. */
    private static final Map<String, String> PATTERNS = Map.of(
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.SEQUENCE, "most blocks take up a step of it",
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.STRUCTURE, "most blocks take up a part of it",
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.PARTITION, "most blocks take up a kind of it (is-a)",
            com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.ASPECTS, "most blocks take up an aspect or an operation of it");

    /** How the section explains its main concept: the pattern, the heading rule, and each block that is a step, part, kind or aspect. */
    private static void pattern(com.scivicslab.turingworkflow.plugins.rstframe.SectionPattern.Layout p,
            com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules, StringBuilder sb) {
        String says = PATTERNS.getOrDefault(p.pattern(), "");
        sb.append("<h3>How it explains ").append(Html.esc(p.concept())).append("</h3>");
        sb.append("<p class=\"section-pattern\"><b>").append(Html.esc(p.pattern())).append("</b>: ").append(Html.esc(says)).append("</p>");
        if (rules != null) {
            for (var r : com.scivicslab.turingworkflow.plugins.rstframe.RuleFile.parse(rules.rules()).rules()) {
                if (r.attr(":heading") != null) {
                    sb.append(ruleForm(rules.rules(), r.name(), ""));
                    break;
                }
            }
        }
        // the breakdown by relation: the kinds become is-a children, the parts part-of children, the aspects and
        // operations the items of the concept's frame; the pattern above is only the relation most of them bear
        Map<String, List<String>> by = new java.util.LinkedHashMap<>();
        for (String r : List.of("kind", "part", "step", "aspect", "operation")) by.put(r, new java.util.ArrayList<>());
        for (var m : p.members()) by.computeIfAbsent(m.relation(), k -> new java.util.ArrayList<>()).add(m.name());
        sb.append("<ul class=\"pattern-breakdown\">");
        for (var e : by.entrySet()) {
            sb.append("<li><b>").append(Html.esc(e.getKey())).append(' ').append(e.getValue().size()).append("</b>");
            if (!e.getValue().isEmpty()) sb.append(": ").append(Html.esc(String.join("; ", e.getValue())));
            sb.append("</li>");
        }
        sb.append("</ul>");
        if (p.members().isEmpty()) {
            sb.append("<p class=\"hint\">(no block related to ").append(Html.esc(p.concept())).append(")</p>");
            return;
        }
        sb.append("<table class=\"concepts pattern\"><tr><th>block</th><th>what it takes up is, to ").append(Html.esc(p.concept()))
          .append("</th></tr>");
        for (var m : p.members()) {
            sb.append("<tr><td class=\"concept\">").append(Html.esc(m.block())).append("</td><td><b>").append(Html.esc(m.relation()))
              .append("</b> ").append(Html.esc(m.name())).append("</td></tr>");
        }
        sb.append("</table>");
    }

    /** The headings of step 6's three kinds of relation, in the order they are shown. */
    private static final Map<String, String> RELATION_HEADINGS = new java.util.LinkedHashMap<>(Map.of());
    static {
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.IS_A, "is-a relation");
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.PART_OF, "part-of relation");
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.FEATURE, "feature");
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.OPERATION, "operation");
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.OTHER, "other relation");
    }

    /**
     * How the section's concepts relate (step 6): for each kind, each parent concept with the ones that are kinds of
     * it, parts of it, or features of it under it, each with the reason step 5 kept it, as step 7's review left them;
     * the relations the review dropped; then the kept concepts no standing relation names, the candidates for dropping.
     */
    private static void relations(Book b, String number, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept> concepts,
            StringBuilder sb) {
        var rel = b.relations().get(number);
        if (rel == null) {
            if (concepts != null && concepts.size() > 1) sb.append("<h3>How they relate</h3><p class=\"hint\">(not asked yet)</p>");
            return;
        }
        Map<String, String> why = new java.util.HashMap<>();
        if (concepts != null) for (var c : concepts) why.put(c.concept(), c.reason());
        sb.append("<h3>How they relate</h3>");
        if (rel.isEmpty()) sb.append("<p class=\"hint\">(none found)</p>");
        // as step 7's review left them: a kind changed or a direction reversed says so; a dropped one is listed last
        List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation> standing = new ArrayList<>();
        List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation> dropped = new ArrayList<>();
        Map<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation, String> was = new java.util.HashMap<>();
        for (var r : rel) {
            var after = r.reviewed();
            if (after == null) {
                dropped.add(r);
                continue;
            }
            standing.add(after);
            if (com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.REVERSE.equals(r.review())) {
                was.put(after, "reversed by the review");
            } else if (!after.kind().equals(r.kind())) {
                was.put(after, "was " + r.kind());
            }
        }
        for (var kind : RELATION_HEADINGS.entrySet()) {
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> byParent = new LinkedHashMap<>();
            for (var r : standing) if (r.kind().equals(kind.getKey())) byParent.computeIfAbsent(r.parent(), k -> new ArrayList<>()).add(r);
            if (byParent.isEmpty()) continue;
            sb.append("<div class=\"concept-relation\"><h4>").append(Html.esc(kind.getValue())).append("</h4>");
            for (var e : byParent.entrySet()) {
                sb.append("<div class=\"relation-parent\">").append(conceptWithWhy(e.getKey(), why)).append("</div><ul class=\"relation-children\">");
                for (var child : e.getValue()) {
                    sb.append("<li>").append(conceptWithWhy(child.child(), why));
                    if (was.containsKey(child)) sb.append(" <span class=\"review\">[").append(Html.esc(was.get(child))).append("]</span>");
                    sb.append("</li>");
                }
                sb.append("</ul>");
            }
            sb.append("</div>");
        }
        if (!dropped.isEmpty()) {
            sb.append("<div class=\"concept-relation dropped-by-review\"><h4>dropped by the review</h4><ul class=\"relation-children\">");
            for (var r : dropped) {
                sb.append("<li class=\"dropped\">").append(Html.esc(r.child() + " " + r.kind() + " " + r.parent())).append("</li>");
            }
            sb.append("</ul></div>");
        }
        // the concepts step 5 kept that no relation names: the candidates for dropping, which a person decides on
        java.util.Set<String> related = new java.util.HashSet<>();
        for (var r : standing) {
            related.add(r.child());
            related.add(r.parent());
        }
        List<String> alone = new ArrayList<>();
        if (concepts != null) for (var c : concepts) if (!Boolean.FALSE.equals(c.kept()) && !related.contains(c.concept())) alone.add(c.concept());
        if (!alone.isEmpty()) {
            sb.append("<div class=\"concept-relation unrelated\"><h4>related to nothing (candidates for dropping)</h4><ul class=\"relation-children\">");
            for (String c : alone) sb.append("<li>").append(conceptWithWhy(c, why)).append("</li>");
            sb.append("</ul></div>");
        }
    }

    /**
     * The material of the section's concrete frame: each concept on the list, with the features and parts folded into
     * it, and the rules that say its properties and its behaviors, by rule name; then the concepts taken off the list,
     * with how many rules they got.
     */
    private static void frame(List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept> list, String ruleFile,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameItem>> items, StringBuilder sb) {
        if (list == null) return;
        sb.append("<h3>Concrete frame material</h3>");
        List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept> off = new ArrayList<>();
        for (var c : list) {
            if (Boolean.FALSE.equals(c.kept())) {
                off.add(c);
                continue;
            }
            sb.append("<div class=\"frame-concept\"><h4>").append(Html.esc(c.concept())).append("</h4>");
            if (c.folded() != null && !c.folded().isEmpty()) {
                sb.append("<div class=\"frame-folded\">with its features, parts and operations: ").append(Html.esc(String.join(", ", c.folded()))).append("</div>");
            }
            // the items: the questions an explanation of the concept answers, each with the rules that answer it here
            var its = items.get(c.concept());
            if (its != null && !its.isEmpty()) {
                Map<String, String> textOf = new java.util.HashMap<>();
                for (var r : c.rules()) textOf.put(r.rule(), r.text());
                sb.append("<div class=\"frame-kind\">items</div><table class=\"concepts frame-items\"><tr><th>the question it answers</th>")
                  .append("<th>its rst-on-lisp rules</th></tr>");
                for (var it : its) {
                    sb.append("<tr><td class=\"concept\">").append(Html.esc(it.question())).append("</td><td class=\"rules\">");
                    for (String r : it.rules()) sb.append(ruleForm(ruleFile, r, textOf.getOrDefault(r, "")));
                    sb.append("</td></tr>");
                }
                sb.append("</table>");
            }
            for (String kind : List.of(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameRule.PROPERTY,
                    com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameRule.BEHAVIOR)) {
                var rules = c.rules().stream().filter(r -> r.kind().equals(kind)).toList();
                if (rules.isEmpty()) continue;
                sb.append("<div class=\"frame-kind\">").append(kind.equals("property") ? "properties" : "behaviors").append("</div><ul class=\"frame-rules\">");
                for (var r : rules) sb.append("<li>").append(ruleForm(ruleFile, r.rule(), r.text())).append("</li>");
                sb.append("</ul>");
            }
            if (c.rules().isEmpty()) sb.append("<p class=\"hint\">(no rule collected)</p>");
            sb.append("</div>");
        }
        if (!off.isEmpty()) {
            sb.append("<div class=\"frame-off\"><h4>taken off the list (the section says little about them)</h4><ul class=\"relation-children\">");
            for (var c : off) sb.append("<li class=\"dropped\">").append(Html.esc(c.concept())).append(" <span class=\"hint\">(")
                    .append(c.rules().size()).append(c.rules().size() == 1 ? " rule" : " rules").append(")</span></li>");
            sb.append("</ul></div>");
        }
    }

    private static String conceptWithWhy(String concept, Map<String, String> why) {
        String w = why.get(concept);
        return Html.esc(concept) + (w == null ? "" : " <span class=\"hint\">(" + Html.esc(w) + ")</span>");
    }

    /** One rule as the rule file writes it, its {@code defrule} form; its text when the file does not have it. */
    private static String ruleForm(String ruleFile, String rule, String text) {
        String form = rule == null || ruleFile == null ? null : com.scivicslab.turingworkflow.plugins.rstframe.RuleFile.formOf(ruleFile, rule);
        return "<pre class=\"rule-lisp\">" + Html.esc(form != null ? form : (rule == null ? "" : rule + " ") + clip(text.strip())) + "</pre>";
    }

    private static String clip(String text) {
        return text.length() > 1200 ? text.substring(0, 1200) + " ..." : text;
    }

    /** "8 concepts", or "8 concepts, 1 dropped" once step 5 has judged them */
    private static String conceptCount(List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept> cs) {
        long dropped = cs.stream().filter(c -> Boolean.FALSE.equals(c.kept())).count();
        return cs.size() + " concepts" + (dropped > 0 ? ", " + dropped + " dropped" : "");
    }

    private static Map<String, BookOutline.Node> nodes(Book b) {
        Map<String, BookOutline.Node> out = new LinkedHashMap<>();
        for (BookOutline.Node n : b.outline()) out.put(n.number(), n);
        return out;
    }

    /** A node's title, or nothing for a section the table of contents does not name. */
    private static String titleOf(Toc t, String number) {
        BookOutline.Node n = t.nodes().get(number);
        return n == null ? "" : n.title();
    }
}
