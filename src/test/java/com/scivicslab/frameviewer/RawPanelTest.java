package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure tests of the two text panes of the concrete-frame tab. */
class RawPanelTest {

    private static final String DIR = "doc_X/docs/Book/Chap23/b";
    private static final String FILE = DIR + "/b.lisp";
    private static final String FRAME = """
            ;;; subject: b 23 Design: the p-aside of it
            ;;; made-from: %s / ch23
            ;;; members: p-plan (the <plan>)
            (defrule d -> :instruction "say \\"what\\"")
            """.formatted(DIR);
    private static final String SOURCE = """
            (defrule ch23 -> p-plan p-aside :heading "## 23")
            (defrule p-plan -> :nucleus p-plan/n)
            (defrule p-plan/n ->
              "We plan.")
            (defrule p-aside -> :nucleus p-aside/n)
            (defrule p-aside/n -> "Aside.")
            """;

    private static RawPanel panel() {
        Library lib = new Library().addSubjects("(defsubjects b :book \"/x\")\n(subject \"23\" \"Design\")")
                .addFrame("subject/b/design.lisp", FRAME);
        return new RawPanel(lib, new Sources(lib, Map.of(FILE, SOURCE)::get));
    }

    @Test
    void theFrameIsShownAsItIsWithItsHeaderRuleNamesLinked() {
        String html = panel().frame("subject/b/design.lisp", FRAME);
        assertTrue(html.contains(";;; members: <a href=\"#\" class=\"raw-rule\" data-source=\"" + FILE + "\" data-rule=\"p-plan\">p-plan</a> (the &lt;plan&gt;)"), html);
        assertTrue(html.contains("(defrule d -&gt; :instruction &quot;say \\&quot;what\\&quot;&quot;)"), html);
        assertTrue(html.contains("<div class=\"raw-head\" data-first-source=\"" + FILE + "\">"), html);
        assertTrue(html.contains("class=\"tag-link\" data-tag=\"b|23\""), html);
        assertTrue(html.contains(";;; subject: b 23 Design: the p-aside of it"), "the subject line is prose, not linked");
    }

    @Test
    void theSourceMarksTheNamedParagraphsWithTheirSubRulesAndContinuationLines() {
        String html = panel().source(FILE, SOURCE, "subject/b/design.lisp");
        assertTrue(html.contains("<span id=\"L-p-plan/n\"></span><span class=\"hit\">(defrule p-plan/n -&gt;</span>\n<span class=\"hit\">  &quot;We plan.&quot;)</span>"), html);
        assertTrue(html.contains("<span id=\"L-ch23\"></span><span class=\"hit\">"), "made-from names ch23");
        assertTrue(html.contains("<span id=\"L-p-aside\"></span>(defrule p-aside"), "p-aside is named by no header line");
    }

    @Test
    void theSourcePaneOpensWithADropDownOfEverySourceFileOfTheFrame() {
        String second = "doc_X/docs/Book/Chap23/c/c.lisp";
        String frame = FRAME.replace(" / ch23", " doc_X/docs/Book/Chap23/c / ch23");
        Library lib = new Library().addFrame("subject/b/design.lisp", frame);
        RawPanel panel = new RawPanel(lib, new Sources(lib, Map.of(FILE, SOURCE, second, "(defrule ch23 -> p-more)",
                "doc_X/docs/Book/Chap23/c/c.md", "---\nid: CPage_1\n---\n")::get));
        String html = panel.source(second, "(defrule ch23 -> p-more)", "subject/b/design.lisp");
        assertTrue(html.contains("<select class=\"raw-file-select\" title=\"" + second + "\"><option value=\"" + FILE + "\">b.lisp</option>"
                + "<option value=\"" + second + "\" selected>c.lisp</option></select> <span class=\"hint\">2 source files of this frame</span>"), html);
        assertTrue(html.contains("of this frame</span> <a href=\"#\" class=\"saurus-link\" data-saurus=\"doc_X/Book/Chap23/CPage_1/\">html-saurus</a>"), html);
    }

    @Test
    void theIntegratedFrameIsShownAsItIsWithTheFramesItNamesLinked() {
        String text = ";;; integrates: subject/b/design.lisp (b, 23 Design)\n(defrule d -> :include \"explain.part.lisp\" :frame \"nope.lisp\")";
        Library lib = new Library().addFrame("subject/b/design.lisp", FRAME)
                .addFrame("explanation/explain.lisp", text).addFrame("explanation/explain.part.lisp", "(defrule p -> q)");
        String html = new RawPanel(lib, new Sources(lib, Map.of(FILE, SOURCE)::get)).integrated("explanation/explain.lisp", text);
        assertTrue(html.contains(";;; integrates: <a href=\"#\" class=\"frame-link\" data-frame=\"subject/b/design.lisp\">subject/b/design.lisp</a> (b, 23 Design)"), html);
        assertTrue(html.contains(":include &quot;<a href=\"#\" class=\"frame-link\" data-frame=\"explanation/explain.part.lisp\">explain.part.lisp</a>&quot;"), html);
        assertTrue(html.contains(":frame &quot;nope.lisp&quot;"), "a file that is no frame stays text");
    }

    @Test
    void anotherFramesParagraphsAreNotMarked() {
        assertTrue(!panel().source(FILE, SOURCE, "subject/b/other.lisp").contains("class=\"hit\""));
    }
}
