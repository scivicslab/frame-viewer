package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure tests of the panel: nesting, child frames, and the source paragraphs read through the works reader. */
class FramePanelTest {

    private static final String BOOK_DIR = "doc_X/docs/Book/Chap23/book p391-400";

    private static Library library() {
        return new Library()
                .addFrame("subject/b/design.lisp", """
                        ;;; subject: b 23 Design
                        ;;; made-from: %s / ch23
                        ;;; members: p-plan
                        ;;; left-out: p-aside (a remark)
                        (defrule document -> title s1)
                        (defrule title -> :instruction "name the system")
                        (defrule s1 -> p-plan cases :heading "## Design")
                        (defrule p-plan -> :nucleus p-plan/n :background p-plan/kind :rank secondary)
                        (defrule p-plan/n -> :instruction "say what" :sample "In this chapter <we> present FileBuy.")
                        (defrule p-plan/kind -> :instruction "say the kind")
                        (defrule cases -> :instruction "list the cases" :frame "design.case.lisp")
                        """.formatted(BOOK_DIR))
                .addFrame("subject/b/design.case.lisp", "(defrule case -> case/n :heading \"#### {item}\")\n(defrule case/n -> :instruction \"tell the case\")");
    }

    private static final Map<String, String> WORKS = Map.of(BOOK_DIR + "/book p391-400.lisp", """
            (defrule ch23 -> p-plan p-aside)
            (defrule p-plan -> :nucleus p-plan/n)
            (defrule p-plan/n -> "In this chapter we present an overview.")
            (defrule p-aside -> :nucleus p-aside/n)
            (defrule p-aside/n -> "An aside.")
            """);

    private static String render() {
        return new FramePanel(library(), new Sources(library(), WORKS::get)).render("subject/b/design.lisp");
    }

    @Test
    void blanksShowTheirInstructionSampleRelationAndRankEscaped() {
        String html = render();
        assertTrue(html.contains("<span class=\"relation\">background</span> <span class=\"name\">p-plan/kind</span>"), html);
        assertTrue(html.contains("rank-secondary"));
        assertTrue(html.contains("In this chapter &lt;we&gt; present FileBuy."));
        assertTrue(html.contains("heading: ## Design"));
    }

    @Test
    void aFrameBlankOpensItsSubFrameInPlace() {
        String html = render();
        assertTrue(html.contains("one copy per item"));
        assertTrue(html.contains("tell the case"), "the sub-frame's blank is shown inside the list blank");
    }

    @Test
    void membersAndLeftOutParagraphsComeFromTheBooksRuleFile() {
        String html = render();
        assertTrue(html.contains("data-rule=\"p-plan\">p-plan</a></dt><dd>In this chapter we present an overview.</dd>"), html);
        assertTrue(html.contains("<h4>left-out</h4><dl class=\"source\"><dt><a href=\"#\" class=\"source-link\""));
        assertTrue(html.contains("data-rule=\"p-aside\">p-aside</a></dt><dd>An aside.</dd>"));
    }

    @Test
    void theTagAndTheSourceFileAreLinks() {
        Library lib = library().addSubjects("(defsubjects b :book \"/x\")\n(subject \"23\" \"Design\")");
        String html = new FramePanel(lib, new Sources(lib, WORKS::get)).render("subject/b/design.lisp");
        assertTrue(html.contains("class=\"tag-link\" data-tag=\"b|23\">b 23 Design</a>"), html);
        assertTrue(html.contains("data-source=\"" + BOOK_DIR + "/book p391-400.lisp\" data-rule=\"\""), html);
    }

    @Test
    void aSubFrameShowsTheTopFrameItBelongsTo() {
        String html = new FramePanel(library(), new Sources(library(), WORKS::get)).render("subject/b/design.case.lisp");
        assertTrue(html.contains("<b>Part of:</b> <a href=\"#\" class=\"frame-link\" data-frame=\"subject/b/design.lisp\">"), html);
    }

    @Test
    void anUnknownFrameIsANotice() {
        assertTrue(new FramePanel(library(), new Sources(library(), WORKS::get)).render("nope.lisp").contains("No frame"));
    }
}
