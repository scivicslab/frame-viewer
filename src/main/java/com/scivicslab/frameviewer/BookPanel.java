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
     * have a rule file, the concepts each section explains, how those concepts relate (steps 6 and 7), and the
     * material of each section's concrete frame.
     */
    public record Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections,
            java.util.Set<String> withRules, Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept>> concepts,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> relations,
            Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept>> frame) {

        /** A book with no concrete-frame material yet. */
        public Book(String name, String dir, List<BookOutline.Node> outline, List<BookSections.Section> sections, java.util.Set<String> withRules,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept>> concepts,
                Map<String, List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation>> relations) {
            this(name, dir, outline, sections, withRules, concepts, relations, Map.of());
        }
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
        frame(b.frame().get(number), rules == null ? null : rules.rules(), sb);
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
            sb.append("<div class=\"relation\"><h4>").append(Html.esc(kind.getValue())).append("</h4>");
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
            sb.append("<div class=\"relation dropped-by-review\"><h4>dropped by the review</h4><ul class=\"relation-children\">");
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
            sb.append("<div class=\"relation unrelated\"><h4>related to nothing (candidates for dropping)</h4><ul class=\"relation-children\">");
            for (String c : alone) sb.append("<li>").append(conceptWithWhy(c, why)).append("</li>");
            sb.append("</ul></div>");
        }
    }

    /**
     * The material of the section's concrete frame: each concept on the list, with the features and parts folded into
     * it, and the rules that say its properties and its behaviors, by rule name; then the concepts taken off the list,
     * with how many rules they got.
     */
    private static void frame(List<com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept> list, String ruleFile, StringBuilder sb) {
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
