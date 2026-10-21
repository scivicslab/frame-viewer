package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure tests of the source index, the tag panel and the source panel: which frame took which paragraph. */
class SourcesTest {

    private static final String DIR = "doc_X/docs/Book/Chap23/book p391-400";
    private static final String FILE = DIR + "/book p391-400.lisp";
    private static final String PAPER = "doc_Y/docs/Papers/loop/loop.lisp";

    private static Library library() {
        return new Library()
                .addSubjects("(defsubjects b :book \"/x\")\n(subject \"23\" \"Design\")\n(subject \"24\" \"Code\")")
                .addFrame("subject/b/design.lisp", """
                        ;;; subject: b 23 Design
                        ;;; made-from: %s / ch23
                        ;;; members: p-plan
                        ;;; left-out: p-aside (a remark)
                        (defrule document -> s1)
                        (defrule s1 -> :instruction "x")
                        """.formatted(DIR))
                .addFrame("subject/b/design.case.lisp", ";;; members: p-case\n(defrule case -> :instruction \"y\")")
                .addFrame("subject/b/design.loop.lisp", ";;; made-from: " + PAPER + " / s-glance\n(defrule d -> :instruction \"w\")")
                .addFrame("subject/loop/loop.lisp", ";;; made-from: " + PAPER + " / s-glance\n(defrule d -> :instruction \"z\")")
                .addFrame("explanation/explain-program.lisp", ";;; integrates: subject/b/design.lisp (b, 23 Design)\n(defrule d -> e)");
    }

    private static final Map<String, String> WORKS = Map.of(FILE, """
            (defrule document -> sections)
            (defrule sections -> ch23)
            (defrule ch23 -> p-plan p-case p-aside p-free :heading "## 23 Design")
            (defrule p-plan -> :nucleus p-plan/n)
            (defrule p-plan/n -> "We present FileBuy.")
            (defrule p-case -> :nucleus p-case/n)
            (defrule p-case/n -> "A case.")
            (defrule p-aside -> :nucleus p-aside/n)
            (defrule p-aside/n -> "An aside.")
            (defrule p-free -> "Nobody took this.")
            """, PAPER, "(defrule document -> s-glance)\n(defrule s-glance -> \"The loop.\" :heading \"## Glance\")");

    private static Sources sources() {
        return new Sources(library(), WORKS::get);
    }

    @Test
    void madeFromNamesDirectoriesOrFiles() {
        assertEquals(List.of("doc_A/x y_p1/x y_p1.lisp", "doc_A/x y_p2/x y_p2.lisp"),
                Sources.sourceFiles("doc_A/x y_p1 doc_A/x y_p2 ... / s1 s2"));
        assertEquals(List.of(PAPER), Sources.sourceFiles(PAPER + " / s-glance"));
        // a value continued on the next ;;; line keeps a line break where the reader puts it back
        assertEquals(List.of("doc_A/p1/p1.lisp", "doc_A/p2/p2.lisp"), Sources.sourceFiles("doc_A/p1\ndoc_A/p2 / s1"));
    }

    @Test
    void aSubFrameUsesTheSourceFilesOfItsTopFrame() {
        Sources s = sources();
        assertEquals(List.of(FILE, PAPER), s.files());
        assertEquals(List.of(FILE), s.filesOf("subject/b/design.case.lisp"));
        assertEquals(List.of(new Sources.Claim("subject/b/design.case.lisp", "members")), s.claimsOf(FILE, "p-case"));
        assertEquals(List.of(PAPER, FILE), s.filesOf("subject/b/design.loop.lisp"), "its own made-from first");
    }

    @Test
    void everyHeaderLineClaimsItsParagraphs() {
        Sources s = sources();
        assertEquals(List.of(new Sources.Claim("subject/b/design.lisp", "made-from")), s.claimsOf(FILE, "ch23"));
        assertEquals(List.of(new Sources.Claim("subject/b/design.loop.lisp", "made-from"), new Sources.Claim("subject/loop/loop.lisp", "made-from")),
                s.claimsOf(PAPER, "s-glance"));
        assertEquals(List.of(new Sources.Claim("subject/b/design.lisp", "members")), s.claimsOf(FILE, "p-plan"));
        assertEquals(List.of(new Sources.Claim("subject/b/design.lisp", "left-out")), s.claimsOf(FILE, "p-aside"));
        assertTrue(s.claimsOf(FILE, "p-free").isEmpty());
    }

    @Test
    void aSectionRunningOnIntoTheNextFileIsClaimedInBoth() {
        String next = "doc_X/docs/Book/Chap23/p2/p2.lisp";
        Library lib = new Library().addFrame("subject/b/s.lisp", ";;; made-from: " + DIR + " doc_X/docs/Book/Chap23/p2 / ch23\n(defrule d -> :instruction \"x\")");
        Map<String, String> works = Map.of(FILE, WORKS.get(FILE), next, "(defrule ch23 -> p-more)\n(defrule p-more -> \"More.\")");
        Sources s = new Sources(lib, works::get);
        assertEquals(1, s.claimsOf(FILE, "ch23").size());
        assertEquals(1, s.claimsOf(next, "ch23").size());
    }

    @Test
    void theSourcePanelMarksEachParagraphWithTheFramesThatTookIt() {
        String html = new SourcePanel(sources()).render(FILE);
        assertTrue(html.contains("<b>23 Design</b> <span class=\"name\">ch23</span> <span class=\"claim claim-made-from\">"), html);
        assertTrue(html.contains("id=\"r-p-aside\"><span class=\"name\">p-aside</span> <span class=\"claim claim-left-out\">left-out: "
                + "<a href=\"#\" class=\"frame-link\" data-frame=\"subject/b/design.lisp\">"), html);
        assertTrue(html.contains("<div class=\"para covered\" id=\"r-p-free\"><span class=\"name\">p-free</span><div class=\"text\">Nobody took this.</div>"), html);
        assertTrue(html.indexOf("r-p-plan") < html.indexOf("r-p-case") && html.indexOf("r-p-case") < html.indexOf("r-p-aside"));
    }

    @Test
    void aParagraphOutsideEveryNamedSectionIsUnclaimed() {
        Library lib = new Library().addFrame("subject/b/s.lisp", ";;; made-from: " + DIR + " / p-plan\n(defrule d -> :instruction \"x\")");
        String html = new SourcePanel(new Sources(lib, WORKS::get)).render(FILE);
        assertTrue(html.contains("<div class=\"para\" id=\"r-p-plan\">"), html);
        assertTrue(html.contains("<div class=\"para unclaimed\" id=\"r-p-case\">"), html);
    }

    @Test
    void aHeadedSectionWithItsOwnTextIsAParagraph() {
        assertTrue(new SourcePanel(sources()).render(PAPER).contains("id=\"r-s-glance\""));
    }

    @Test
    void theTagPanelReachesTheConcreteFrameTheIntegratedFrameAndTheSource() {
        Library lib = library();
        String html = new TagPanel(lib, new Sources(lib, WORKS::get)).render("b|23");
        assertTrue(html.contains("data-frame=\"subject/b/design.lisp\""), html);
        assertTrue(html.contains("<h3>Integrated into</h3><ul><li><a href=\"#\" class=\"frame-link\" data-frame=\"explanation/explain-program.lisp\">"), html);
        assertTrue(html.contains("data-source=\"" + FILE + "\""), html);
        assertTrue(new TagPanel(lib, new Sources(lib, WORKS::get)).render("b|24").contains("No frame names this tag."));
    }
}
