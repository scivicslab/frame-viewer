package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure tests of the library: the subject trees, and how frames are found for nodes and integrations. */
class LibraryTest {

    private static Library library() {
        return new Library()
                .addSubjects("""
                        (defsubjects globus :book "/x")
                        (subject "23" "Design" :file "a.md")
                        (subject "23.4" "Deployment" :file "a.md")
                        (subject "23.4.1" "Hosts" :file "a.md")
                        (subject "24" "Implementation" :file "b.md")
                        """)
                .addFrame("subject/filebuy/design.lisp", ";;; subject: globus 23 Design\n(defrule document -> title)")
                .addFrame("subject/filebuy/design.deployment.lisp", ";;; subject: globus 23.4 Deployment\n(defrule d -> p)")
                .addFrame("subject/filebuy/design.lesson.lisp", ";;; one lesson\n(defrule lesson -> :nucleus lesson/n)")
                .addFrame("explanation/explain-program.lisp", ";;; integrates: subject/filebuy/design.lisp (Design)\n(defrule document -> title)")
                .addFrame("explanation/explain-program.flow.lisp", "(defrule flow -> a)");
    }

    @Test
    void parentsComeFromTheNumbers() {
        List<Library.Subject> s = library().books().get("globus");
        assertEquals(List.of("23", "23.4", "23.4.1", "24"), s.stream().map(Library.Subject::number).toList());
        assertEquals(java.util.Arrays.asList(null, "23", "23.4", null), s.stream().map(Library.Subject::parent).toList());
    }

    @Test
    void aNodeFindsTheFrameNamingItEvenWhenItIsAChildFrame() {
        Library lib = library();
        assertEquals("subject/filebuy/design.lisp", lib.framesOf("globus", "23").get(0).path());
        assertEquals("subject/filebuy/design.deployment.lisp", lib.framesOf("globus", "23.4").get(0).path());
        assertTrue(lib.framesOf("globus", "24").isEmpty());
    }

    @Test
    void integrationIsFoundBothWays() {
        Library lib = library();
        assertEquals(List.of("subject/filebuy/design.lisp"), lib.frame("explanation/explain-program.lisp").integrates());
        assertEquals("explanation/explain-program.lisp", lib.integratedInto("subject/filebuy/design.lisp").get(0).path());
    }

    @Test
    void theTreeMarksFramedNodesAndListsOnlyTopIntegratedFrames() {
        List<Library.TreeNode> tree = library().tree();
        Library.TreeNode design = tree.stream().filter(n -> n.id().equals("s:globus:23")).findFirst().orElseThrow();
        assertEquals(Map.of("data-frame", "subject/filebuy/design.lisp", "class", "has-frame"), design.li_attr());
        assertEquals("s:globus:23.4", tree.stream().filter(n -> n.id().equals("s:globus:23.4.1")).findFirst().orElseThrow().parent());
        List<String> integrated = tree.stream().filter(n -> "integrated".equals(n.parent())).map(Library.TreeNode::id).toList();
        assertEquals(List.of("f:explanation/explain-program.lisp"), integrated);
    }
}
