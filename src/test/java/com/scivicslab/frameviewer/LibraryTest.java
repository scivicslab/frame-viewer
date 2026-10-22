package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure tests of the library: how frames are grouped, and found for integrations. */
class LibraryTest {

    private static Library library() {
        return new Library()
                .addFrame("subject/filebuy/design.lisp", ";;; subject: globus 23 Design\n(defrule document -> title)")
                .addFrame("subject/filebuy/design.deployment.lisp", ";;; subject: globus 23.4 Deployment\n(defrule d -> p)")
                .addFrame("subject/filebuy/design.lesson.lisp", ";;; one lesson\n(defrule lesson -> :nucleus lesson/n)")
                .addFrame("explanation/explain-program.lisp", ";;; integrates: subject/filebuy/design.lisp (Design)\n(defrule document -> title)")
                .addFrame("explanation/explain-program.flow.lisp", "(defrule flow -> a)");
    }

    @Test
    void integrationIsFoundBothWays() {
        Library lib = library();
        assertEquals(List.of("subject/filebuy/design.lisp"), lib.frame("explanation/explain-program.lisp").integrates());
        assertEquals("explanation/explain-program.lisp", lib.integratedInto("subject/filebuy/design.lisp").get(0).path());
    }

    @Test
    void theFrameTreesKeepTheKindsApartAndPutSubFramesUnderTheirTop() {
        Library lib = library();
        List<Library.TreeNode> concrete = lib.frameTree(false);
        assertEquals("f:subject/filebuy/design.lisp",
                concrete.stream().filter(n -> n.id().equals("f:subject/filebuy/design.deployment.lisp")).findFirst().orElseThrow().parent());
        assertTrue(concrete.stream().noneMatch(n -> n.id().startsWith("f:explanation/")));
        List<String> integrated = lib.frameTree(true).stream().map(Library.TreeNode::id).toList();
        assertEquals(List.of("d:explanation/", "f:explanation/explain-program.lisp", "f:explanation/explain-program.flow.lisp"), integrated);
    }

}
