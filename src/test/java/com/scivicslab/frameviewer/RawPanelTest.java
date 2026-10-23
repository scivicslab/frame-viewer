package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
        Library lib = new Library().addFrame("subject/b/design.lisp", FRAME);
        return new RawPanel(lib, new Sources(lib, Map.of(FILE, SOURCE)::get));
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
        assertTrue(html.contains("<a href=\"#\" class=\"raw-view active\" data-view=\"lisp\">Lisp</a>"), html);
        assertTrue(html.contains("</span> <a href=\"#\" class=\"saurus-link\" data-saurus=\"doc_X/Book/Chap23/CPage_1/\">html-saurus</a>"
                + " <a href=\"#\" class=\"edit-link\" data-edit-works=\"" + second + "\""), html);

        String md = panel.markdown(second, "---\nid: CPage_1\n---\n# <C>\n", "subject/b/design.lisp");
        assertTrue(md.contains("<a href=\"#\" class=\"raw-view active\" data-view=\"md\">Markdown</a>"), md);
        assertTrue(md.contains("<pre class=\"raw md\">---\nid: CPage_1\n---\n# &lt;C&gt;\n</pre>"), md);
        assertTrue(md.contains("data-edit-works=\"doc_X/docs/Book/Chap23/c/c.md\""), "in the markdown view, edit opens the markdown file");
        assertTrue(panel.markdown(second, null, "subject/b/design.lisp").contains("No markdown file beside this source file."));
    }

    @Test
    void theIntegratedFrameIsShownAsItIsWithTheFramesItNamesLinked() {
        String text = ";;; integrates: subject/b/design.lisp (b, 23 Design)\n(defrule d -> :include \"explain.part.lisp\" :frame \"nope.lisp\")";
        Library lib = new Library().addFrame("subject/b/design.lisp", FRAME)
                .addFrame("explanation/explain.lisp", text).addFrame("explanation/explain.part.lisp", "(defrule p -> q)");
        String html = new RawPanel(lib, new Sources(lib, Map.of(FILE, SOURCE)::get)).integrated("explanation/explain.lisp", text);
        assertTrue(html.contains(";;; integrates: subject/b/design.lisp (b, 23 Design)"), "a concrete frame's name is text: " + html);
        assertTrue(html.contains(":include &quot;<a href=\"#\" class=\"frame-link\" data-frame=\"explanation/explain.part.lisp\">explain.part.lisp</a>&quot;"), html);
        assertTrue(html.contains(":frame &quot;nope.lisp&quot;"), "a file that is no frame stays text");
        assertTrue(html.contains("<b>explanation/explain.lisp</b> <a href=\"#\" class=\"edit-link\" data-edit-frame=\"explanation/explain.lisp\""), html);
    }

    @Test
    void anotherFramesParagraphsAreNotMarked() {
        assertTrue(!panel().source(FILE, SOURCE, "subject/b/other.lisp").contains("class=\"hit\""));
    }

    @Test
    void eachRuleLineIsFollowedByTheRulesItsEdgesJoinItTo() {
        Library lib = new Library().addFrame("subject/b/design.lisp", FRAME)
                .addFrame("explanation/explain.lisp", "(defrule p-what -> :nucleus p-what/n)\n");
        Links links = new Links(List.of(
                "explanation/explain.lisp\tp-what\tsubject/b/design.lisp\td\tintegrates\n"
                + "subject/b/design.lisp\td\t" + FILE + "\tp-plan\tmade-from\n"));
        RawPanel panel = new RawPanel(lib, new Sources(lib, Map.of(FILE, SOURCE)::get), links);

        String integrated = panel.integrated("explanation/explain.lisp", "(defrule p-what -> :nucleus p-what/n)\n");
        assertTrue(integrated.contains("<span id=\"F-p-what\"></span>(defrule p-what"), integrated);
        assertTrue(integrated.contains("<span class=\"edge\" title=\"integrates: subject/b/design.lisp\">"), "a concrete frame's rule is text: " + integrated);
    }
}
