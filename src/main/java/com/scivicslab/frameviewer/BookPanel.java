package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.scivicslab.turingworkflow.plugins.rstframe.BookOutline;
import com.scivicslab.turingworkflow.plugins.rstframe.BookSections;

/**
 * The Contents and Sections tabs ({@code FrameOntology_261021_oo01}, getting the concept hierarchy): a book's table of
 * contents as {@code book-toc.yaml} stored it, and its text cut into those nodes as {@code book-sections.yaml} stored it.
 * Pure: it is given what the H2 database holds.
 */
public final class BookPanel {

    /**
     * One book as the database holds it: its name, its directory, its table of contents, its sections, which sections
     * have a rule file, the concepts each section explains, and how those concepts relate (step 6).
     */
    public record Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections,
            java.util.Set<String> withRules, Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept>> concepts,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> relations) {
    }

    private final Map<String, Book> books = new LinkedHashMap<>();
    private final String worksDir;

    /** @param worksDir the works directory, absolute, to give a page file a path under it for its edit link */
    public BookPanel(List<Book> books, String worksDir) {
        for (Book b : books) this.books.put(b.name(), b);
        this.worksDir = worksDir.endsWith("/") ? worksDir : worksDir + "/";
    }

    /** The Contents tab: one root per book, its parts, chapters, sections and subsections under their parents. */
    public List<Library.TreeNode> contentsTree() {
        List<Library.TreeNode> out = new ArrayList<>();
        for (Book b : books.values()) {
            String root = "b:" + b.name();
            out.add(new Library.TreeNode(root, "#", b.name() + " (" + b.outline().size() + " nodes)", Map.of()));
            for (BookOutline.Node n : b.outline()) {
                String parent = n.parent() == null ? root : "n:" + b.name() + "|" + n.parent();
                out.add(new Library.TreeNode("n:" + b.name() + "|" + n.number(), parent, n.number() + " " + n.title(),
                        Map.of("data-book", b.name(), "data-node", n.number(), "data-level", n.level())));
            }
        }
        return out;
    }

    /** The Sections tab: one root per book, its sections in the book's order, each with its length and how many files it spans. */
    public List<Library.TreeNode> sectionsTree() {
        List<Library.TreeNode> out = new ArrayList<>();
        for (Book b : books.values()) {
            String root = "sb:" + b.name();
            out.add(new Library.TreeNode(root, "#", b.name() + " (" + b.sections().size() + " sections)", Map.of()));
            Map<String, BookOutline.Node> nodes = nodes(b);
            for (BookSections.Section s : b.sections()) {
                BookOutline.Node n = nodes.get(s.number());
                String title = n == null ? "" : n.title();
                String text = s.number() + " " + title + "  · " + s.text().length() + " chars" + (s.files().size() > 1 ? ", " + s.files().size() + " files" : "")
                        + (b.withRules().contains(s.number()) ? " · rst" : "")
                        + (b.concepts().containsKey(s.number()) ? " · " + conceptCount(b.concepts().get(s.number())) : "")
                        + (b.relations().containsKey(s.number()) ? " · " + b.relations().get(s.number()).size() + " relations" : "");
                out.add(new Library.TreeNode("s:" + b.name() + "|" + s.number(), root, text, Map.of("data-book", b.name(), "data-section", s.number())));
            }
        }
        return out;
    }

    /** The Contents tab's panel: one node, where it stands, what is under it, and the link to its text. */
    public String node(String book, String number) {
        Book b = books.get(book);
        if (b == null) return "<p class=\"hint\">No book " + Html.esc(book) + " in the database.</p>";
        Map<String, BookOutline.Node> nodes = nodes(b);
        BookOutline.Node n = nodes.get(number);
        if (n == null) return "<p class=\"hint\">No node " + Html.esc(number) + " in " + Html.esc(book) + ".</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Html.esc(n.number() + " " + n.title())).append("</h2>");
        sb.append("<div class=\"raw-path\">").append(Html.esc(book)).append(" · ").append(Html.esc(n.level())).append("</div>");
        sb.append("<h3>Where it stands</h3><div class=\"child\">");
        List<BookOutline.Node> up = new ArrayList<>();
        for (BookOutline.Node p = n.parent() == null ? null : nodes.get(n.parent()); p != null; p = p.parent() == null ? null : nodes.get(p.parent())) up.add(0, p);
        if (up.isEmpty()) sb.append("<span class=\"hint\">(the top of the book)</span>");
        for (int i = 0; i < up.size(); i++) sb.append(i > 0 ? " → " : "").append(nodeLink(book, up.get(i)));
        sb.append("</div>");
        List<BookOutline.Node> kids = b.outline().stream().filter(x -> number.equals(x.parent())).toList();
        if (!kids.isEmpty()) {
            sb.append("<h3>Under it</h3>");
            for (BookOutline.Node k : kids) sb.append("<div class=\"child\">").append(nodeLink(book, k)).append("</div>");
        }
        sb.append("<h3>Its text</h3><div class=\"child\">");
        BookSections.Section s = b.sections().stream().filter(x -> x.number().equals(number)).findFirst().orElse(null);
        if (s == null) sb.append("<span class=\"hint\">(book-sections.yaml has not been run for this book)</span>");
        else sb.append(sectionLink(book, s.number(), s.text().length() + " chars" + (s.files().size() > 1 ? ", " + s.files().size() + " files" : "")));
        sb.append("</div><h3>First found in</h3><div class=\"raw-path\">").append(Html.esc(n.firstFile())).append("</div>");
        return sb.toString();
    }

    /**
     * The Sections tab's panel: one section, with the page files it was read from, the concepts it explains (step 4),
     * and either its markdown text ({@code view} {@code md}) or its rst-on-lisp rule file with its code files ({@code rst}).
     *
     * @param rules the section's rule file, or {@code null} when it has none yet
     */
    public String section(String book, String number, String view, com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules rules) {
        Book b = books.get(book);
        if (b == null) return "<p class=\"hint\">No book " + Html.esc(book) + " in the database.</p>";
        BookSections.Section s = b.sections().stream().filter(x -> x.number().equals(number)).findFirst().orElse(null);
        if (s == null) return "<p class=\"hint\">No section " + Html.esc(number) + " in " + Html.esc(book) + ".</p>";
        BookOutline.Node n = nodes(b).get(number);
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Html.esc(number + " " + (n == null ? "" : n.title()))).append("</h2>");
        sb.append("<div class=\"raw-path\">").append(Html.esc(book)).append(" · ").append(s.text().length()).append(" chars · ")
          .append(n == null ? "" : nodeLink(book, n, "in the table of contents")).append("</div>");
        sb.append("<h3>Read from</h3>");
        for (String f : s.files()) {
            String abs = b.dir().endsWith("/") ? b.dir() + f : b.dir() + "/" + f;
            sb.append("<div class=\"raw-path\">").append(Html.esc(f));
            if (abs.startsWith(worksDir)) sb.append(' ').append(Html.editWorksLink(abs.substring(worksDir.length())));
            sb.append("</div>");
        }
        sb.append("<h3>The concepts it explains</h3>");
        var concepts = b.concepts().get(number);
        if (concepts == null) sb.append("<p class=\"hint\">(not listed yet)</p>");
        else if (concepts.isEmpty()) sb.append("<p class=\"hint\">(none)</p>");
        else {
            // step 4 listed them; step 5 kept or dropped each, by the role of the sentences that name it
            sb.append("<ul class=\"concepts\">");
            for (var c : concepts) {
                boolean dropped = Boolean.FALSE.equals(c.kept());
                sb.append("<li").append(dropped ? " class=\"dropped\"" : "").append(">").append(Html.esc(c.concept()));
                if (c.reason() != null) sb.append(" <span class=\"hint\">(").append(Html.esc(c.reason())).append(")</span>");
                sb.append("</li>");
            }
            sb.append("</ul>");
        }
        relations(b, number, concepts, sb);
        boolean rst = "rst".equals(view);
        sb.append("<h3>Its text <span class=\"raw-views\">")
          .append("<a href=\"#\" class=\"raw-view section-view").append(rst ? "" : " active").append("\" data-view=\"md\" data-book=\"")
          .append(Html.esc(book)).append("\" data-section=\"").append(Html.esc(number)).append("\">markdown</a>")
          .append("<a href=\"#\" class=\"raw-view section-view").append(rst ? " active" : "").append("\" data-view=\"rst\" data-book=\"")
          .append(Html.esc(book)).append("\" data-section=\"").append(Html.esc(number)).append("\">rst-on-lisp</a></span></h3>");
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

    /** The headings of step 6's three kinds of relation, in the order they are shown. */
    private static final Map<String, String> RELATION_HEADINGS = new java.util.LinkedHashMap<>(Map.of());
    static {
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.IS_A, "is-a relation");
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.PART_OF, "part-of relation");
        RELATION_HEADINGS.put(com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation.FEATURE, "feature");
    }

    /**
     * How the section's concepts relate (step 6): for each kind, each parent concept with the ones that are kinds of
     * it, parts of it, or features of it under it, each with the reason step 5 kept it.
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
        if (rel.isEmpty()) {
            sb.append("<p class=\"hint\">(none found)</p>");
            return;
        }
        for (var kind : RELATION_HEADINGS.entrySet()) {
            Map<String, List<String>> byParent = new LinkedHashMap<>();
            for (var r : rel) if (r.kind().equals(kind.getKey())) byParent.computeIfAbsent(r.parent(), k -> new ArrayList<>()).add(r.child());
            if (byParent.isEmpty()) continue;
            sb.append("<div class=\"relation\"><h4>").append(Html.esc(kind.getValue())).append("</h4>");
            for (var e : byParent.entrySet()) {
                sb.append("<div class=\"relation-parent\">").append(conceptWithWhy(e.getKey(), why)).append("</div><ul class=\"relation-children\">");
                for (String child : e.getValue()) sb.append("<li>").append(conceptWithWhy(child, why)).append("</li>");
                sb.append("</ul>");
            }
            sb.append("</div>");
        }
    }

    private static String conceptWithWhy(String concept, Map<String, String> why) {
        String w = why.get(concept);
        return Html.esc(concept) + (w == null ? "" : " <span class=\"hint\">(" + Html.esc(w) + ")</span>");
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

    private static String nodeLink(String book, BookOutline.Node n) {
        return nodeLink(book, n, n.number() + " " + n.title());
    }

    private static String nodeLink(String book, BookOutline.Node n, String text) {
        return "<a href=\"#\" class=\"node-link\" data-book=\"" + Html.esc(book) + "\" data-node=\"" + Html.esc(n.number()) + "\">" + Html.esc(text) + "</a>";
    }

    private static String sectionLink(String book, String number, String text) {
        return "<a href=\"#\" class=\"section-link\" data-book=\"" + Html.esc(book) + "\" data-section=\"" + Html.esc(number) + "\">" + Html.esc(text) + "</a>";
    }
}
