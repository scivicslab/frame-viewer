package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure tests of the source index: which frame took which paragraph. */
class SourcesTest {

    private static final String DIR = "doc_X/docs/Book/Chap23/book p391-400";
    private static final String FILE = DIR + "/book p391-400.lisp";
    private static final String PAPER = "doc_Y/docs/Papers/loop/loop.lisp";

    private static Library library() {
        return new Library()
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
    void aSourceWithMarkdownBesideItLinksToItsHtmlSaurusPage() {
        Map<String, String> works = new java.util.HashMap<>(WORKS);
        works.put(DIR + "/book p391-400.md", "---\ntitle: x\n---\nbody");
        Library lib = library();
        Sources s = new Sources(lib, works::get);
        assertEquals("doc_X/Book/Chap23/book p391-400/", s.saurusPageOf(FILE));
    }
}
