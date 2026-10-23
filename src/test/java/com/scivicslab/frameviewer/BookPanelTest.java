package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.scivicslab.turingworkflow.plugins.rstframe.BookOutline;
import com.scivicslab.turingworkflow.plugins.rstframe.BookSections;

/** Pure tests: a book's table of contents as a tree, its sections as a list, and their panels. */
class BookPanelTest {

    private static BookPanel panel() {
        List<BookOutline.Node> outline = List.of(
                new BookOutline.Node("4", "chapter", "Objects and Classes", null, "Chap04/p001.md"),
                new BookOutline.Node("4.1", "section", "Introduction", "4", "Chap04/p001.md"),
                new BookOutline.Node("4.1.1", "subsection", "Classes", "4.1", "Chap04/p001.md"));
        List<BookSections.Section> sections = List.of(
                new BookSections.Section("4", "In this chapter.", List.of("Chap04/p001.md")),
                new BookSections.Section("4.1", "", List.of("Chap04/p001.md")),
                new BookSections.Section("4.1.1", "A class is a template.\n\nIt runs on.", List.of("Chap04/p001.md", "Chap04/p006.md")));
        return new BookPanel(List.of(new BookPanel.Book("core", "/w/books/core", outline, sections, java.util.Set.of("4.1.1"),
                java.util.Map.of("4.1.1", List.of(
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("class", true, "in the title"),
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("instance", null, null),
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("Object class", false, "only in comment"))),
                java.util.Map.of("4.1.1", List.of(
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation("is-a", "instance", "class", List.of(2)),
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation("part-of", "Object class", "class", List.of(1)),
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation("feature", "instance", "Object class", List.of(1)))))), "/w");
    }

    @Test
    void theContentsAreATreeUnderTheBookAndTheSectionsAListInTheBooksOrder() {
        List<Library.TreeNode> c = panel().contentsTree();
        assertEquals("core (3 nodes)", c.get(0).text());
        assertEquals(new Library.TreeNode("n:core|4.1.1", "n:core|4.1", "4.1.1 Classes",
                java.util.Map.of("data-book", "core", "data-node", "4.1.1", "data-level", "subsection")), c.get(3));
        List<Library.TreeNode> s = panel().sectionsTree();
        assertEquals("core (3 sections)", s.get(0).text());
        assertEquals("4.1.1 Classes  · 35 chars, 2 files · rst · 3 concepts, 1 dropped · 3 relations", s.get(3).text());
        assertEquals("sb:core", s.get(3).parent());
    }

    @Test
    void aNodeShowsWhereItStandsWhatIsUnderItAndItsText() {
        String html = panel().node("core", "4.1");
        assertTrue(html.contains("<h2>4.1 Introduction</h2>"), html);
        assertTrue(html.contains("data-node=\"4\">4 Objects and Classes</a>"), html);
        assertTrue(html.contains("<h3>Under it</h3><div class=\"child\"><a href=\"#\" class=\"node-link\" data-book=\"core\" data-node=\"4.1.1\">4.1.1 Classes</a>"), html);
        assertTrue(html.contains("class=\"section-link\" data-book=\"core\" data-section=\"4.1\">0 chars</a>"), html);
    }

    @Test
    void aSectionShowsItsFilesAndItsText() {
        String html = panel().section("core", "4.1.1", "md", null);
        assertTrue(html.contains("<h2>4.1.1 Classes</h2>"), html);
        assertTrue(html.contains("Chap04/p006.md <a href=\"#\" class=\"edit-link\" data-edit-works=\"books/core/Chap04/p006.md\""), html);
        assertTrue(html.contains("<pre class=\"raw md\">A class is a template.\n\nIt runs on.</pre>"), html);
        assertTrue(panel().section("core", "4.1", "md", null).contains("(no text of its own: its subsections hold it)"));
        assertTrue(html.contains("<tr><td class=\"concept\">class <span class=\"hint\">(in the title)</span></td><td class=\"rules\"></td></tr>"
                + "<tr><td class=\"concept\">instance</td><td class=\"rules\"></td></tr>"
                + "<tr class=\"dropped\"><td class=\"concept\">Object class <span class=\"hint\">(only in comment)</span></td><td class=\"rules\"></td></tr></table>"), html);
        assertTrue(panel().section("core", "4.1", "md", null).contains("(not listed yet)"));
    }

    @Test
    void theRstViewShowsTheRuleFileAndItsCodeFiles() {
        var rules = new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules("4.1.1",
                "(defrule title -> \"4.1.1. Classes\")", java.util.Map.of("b2.txt", "class A {}"), 12);
        String html = panel().section("core", "4.1.1", "rst", rules);
        assertTrue(html.contains("class=\"raw-view section-view active\" data-view=\"rst\""), html);
        assertTrue(html.contains("<pre class=\"raw lisp\">(defrule title -&gt; &quot;4.1.1. Classes&quot;)</pre>"), html);
        assertTrue(html.contains("<div class=\"raw-path\">b2.txt</div><pre class=\"raw\">class A {}</pre>"), html);
        assertTrue(panel().section("core", "4.1", "rst", null).contains("(no rule file yet"));
    }

    /** Step 6: each kind of relation under its heading, each parent with its children under it, with why step 5 kept them. */
    @Test
    void theRelationsAreShownByKindWithEachParentOverItsChildren() {
        String html = panel().section("core", "4.1.1", "md", null);
        assertTrue(html.contains("<h3>How they relate</h3>"), html);
        assertTrue(html.contains("<div class=\"relation\"><h4>is-a relation</h4><div class=\"relation-parent\">class <span class=\"hint\">(in the title)</span></div>"
                + "<ul class=\"relation-children\"><li>instance</li></ul></div>"), html);
        assertTrue(html.contains("<h4>part-of relation</h4><div class=\"relation-parent\">class <span class=\"hint\">(in the title)</span></div>"
                + "<ul class=\"relation-children\"><li>Object class <span class=\"hint\">(only in comment)</span></li></ul>"), html);
        assertTrue(html.contains("<h4>feature</h4><div class=\"relation-parent\">Object class"), html);
        assertTrue(html.indexOf("is-a relation") < html.indexOf("part-of relation") && html.indexOf("part-of relation") < html.indexOf("<h4>feature"),
                "in the order is-a, part-of, feature");
        assertTrue(!html.contains("related to nothing"), "every kept concept here is in a relation");
    }

    /** A kept concept no relation names is a candidate for dropping; a concept step 5 dropped is not listed again. */
    @Test
    void aKeptConceptNoRelationNamesIsListedAsACandidateForDropping() {
        var c = java.util.Map.of("4.1.1", List.of(
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("class", true, "in the title"),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("instance", true, "in a nucleus"),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("JavaScript", true, "in a nucleus (sentences 22)"),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("Object class", false, "only in comment")));
        var r = java.util.Map.of("4.1.1", List.of(
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation("is-a", "instance", "class", List.of(2))));
        BookPanel p = new BookPanel(List.of(new BookPanel.Book("core", "/w/books/core",
                List.of(new BookOutline.Node("4.1.1", "subsection", "Classes", null, "a.md")),
                List.of(new BookSections.Section("4.1.1", "A class.", List.of("a.md"))), java.util.Set.of(), c, r)), "/w");
        String html = p.section("core", "4.1.1", "md", null);
        assertTrue(html.contains("<h4>related to nothing (candidates for dropping)</h4><ul class=\"relation-children\">"
                + "<li>JavaScript <span class=\"hint\">(in a nucleus (sentences 22))</span></li></ul>"), html);
    }

    /** Step 7: a relation shows as the review left it; one it dropped is listed apart, and its concepts may become candidates. */
    @Test
    void theRelationsShowAsTheReviewLeftThem() {
        var c = java.util.Map.of("10.1.1", List.of(
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("concurrent programming", true, "in a nucleus"),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("multithreading", true, "in a nucleus"),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.JudgedConcept("Java", true, "in a nucleus")));
        var r = java.util.Map.of("10.1.1", List.of(
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation("part-of", "multithreading", "concurrent programming", List.of(1), "is-a"),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation("feature", "Java", "concurrent programming", List.of(5), "drop"),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.Relation("feature", "threads", "concurrent programming", List.of(6), "other")));
        BookPanel p = new BookPanel(List.of(new BookPanel.Book("core", "/w/books/core",
                List.of(new BookOutline.Node("10.1.1", "subsection", "Threads", null, "a.md")),
                List.of(new BookSections.Section("10.1.1", "Threads.", List.of("a.md"))), java.util.Set.of(), c, r)), "/w");
        String html = p.section("core", "10.1.1", "md", null);
        assertTrue(html.contains("<h4>is-a relation</h4><div class=\"relation-parent\">concurrent programming <span class=\"hint\">(in a nucleus)</span></div>"
                + "<ul class=\"relation-children\"><li>multithreading <span class=\"hint\">(in a nucleus)</span> <span class=\"review\">[was part-of]</span></li></ul>"), html);
        assertTrue(!html.contains("<h4>part-of relation</h4>") && !html.contains("<h4>feature</h4>"), html);
        assertTrue(html.contains("<h4>other relation</h4><div class=\"relation-parent\">concurrent programming"), html);
        assertTrue(html.contains("<li>threads <span class=\"review\">[was feature]</span></li>"), html);
        assertTrue(html.contains("<h4>dropped by the review</h4><ul class=\"relation-children\"><li class=\"dropped\">Java feature concurrent programming</li></ul>"), html);
        assertTrue(html.contains("<h4>related to nothing (candidates for dropping)</h4><ul class=\"relation-children\"><li>Java"), html);
    }

    /** A section's concrete frame material: the concepts that stay with their rules by kind, and those taken off apart. */
    @Test
    void theConcreteFrameMaterialShowsTheRulesOfEachConceptThatStays() {
        var fr = java.util.Map.of("3.6", List.of(
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept("String", List.of(
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameRule("property", "p1/1", "A string is a sequence of Unicode characters."),
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameRule("behavior", "p2/1", "The substring method extracts a part.")), true,
                        List.of("String.length", "String.charAt")),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameConcept("String literal", List.of(
                        new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.FrameRule("property", "p2/2", "A literal is in quotes.")), false)));
        BookPanel p = new BookPanel(List.of(new BookPanel.Book("core", "/w/books/core",
                List.of(new BookOutline.Node("3.6", "section", "Strings", null, "a.md")),
                List.of(new BookSections.Section("3.6", "Strings.", List.of("a.md"))), java.util.Set.of(), java.util.Map.of(), java.util.Map.of(), fr)), "/w");
        String html = p.section("core", "3.6", "md", null);
        assertTrue(html.contains("<h3>Concrete frame material</h3><div class=\"frame-concept\"><h4>String</h4>"
                + "<div class=\"frame-folded\">with its features, parts and operations: String.length, String.charAt</div><div class=\"frame-kind\">properties</div>"
                + "<ul class=\"frame-rules\"><li><code>p1/1</code> A string is a sequence of Unicode characters.</li></ul>"
                + "<div class=\"frame-kind\">behaviors</div><ul class=\"frame-rules\"><li><code>p2/1</code> The substring method extracts a part.</li></ul></div>"), html);
        assertTrue(html.contains("<li class=\"dropped\">String literal <span class=\"hint\">(1 rule)</span></li>"), html);
    }

    /** Beside each concept, the rst-on-lisp rules of the sentences step 4 said explain it, by rule name. */
    @Test
    void eachConceptShowsTheRulesOfItsSentencesBesideIt() {
        var rules = new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.SectionRules("4.1.1", """
                (defrule s1 -> p1 :heading "## 4.1.1. Classes")
                (defrule p1 -> :nucleus p1/1 :example p1/2)
                (defrule p1/1 -> "A class is a template.")
                (defrule p1/2 -> "An instance is made from it.")
                """, java.util.Map.of(), 2);
        var listed = List.of(
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept("class", List.of(1, 2)),
                new com.scivicslab.turingworkflow.plugins.rstframe.BookStore.ListedConcept("instance", List.of(2, 9)));
        String html = panel().section("core", "4.1.1", "md", rules, listed);
        assertTrue(html.contains("<table class=\"concepts\"><tr><th>concept</th><th>its rst-on-lisp rules</th></tr>"), html);
        assertTrue(html.contains("<td class=\"concept\">class <span class=\"hint\">(in the title)</span></td><td class=\"rules\">"
                + "<div class=\"rule-line\"><code>p1/1</code> A class is a template.</div>"
                + "<div class=\"rule-line\"><code>p1/2</code> An instance is made from it.</div></td>"), html);
        assertTrue(html.contains("<td class=\"concept\">instance</td><td class=\"rules\"><div class=\"rule-line\"><code>p1/2</code> An instance is made from it.</div></td>"),
                "a sentence number past the rule file is left out: " + html);
    }
}
