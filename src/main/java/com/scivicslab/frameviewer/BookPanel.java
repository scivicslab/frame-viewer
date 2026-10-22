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

    /** One book as the database holds it: its name, its directory, its table of contents and its sections. */
    public record Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections) {
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
                String text = s.number() + " " + title + "  · " + s.text().length() + " chars" + (s.files().size() > 1 ? ", " + s.files().size() + " files" : "");
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

    /** The Sections tab's panel: one section's text as markdown, with the page files it was read from. */
    public String section(String book, String number) {
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
        sb.append("<h3>Its text</h3>");
        if (s.text().isEmpty()) sb.append("<p class=\"hint\">(no text of its own: its subsections hold it)</p>");
        else sb.append("<pre class=\"raw md\">").append(Html.esc(s.text())).append("</pre>");
        return sb.toString();
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
