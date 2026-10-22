package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure tests: the class tree per set, and one class's panel with what it inherits and the paragraphs assigned to it. */
class ClassPanelTest {

    private static final String MESSAGES = """
            (defpackage :concepts (:use :cl))
            (in-package :concepts)
            (defclass message ()
              ((carried-values :documentation "What values it carries"))
              (:documentation "Something the agent loop hands to the program"))
            (defclass result-message (message)
              ((how-it-ended :documentation "How the loop ended"))
              (:documentation "The message that marks the end of the loop"))
            """;
    private static final String RESULT = """
            ;;; book-file: /w/doc_Y/loop/loop.lisp
            (in-package :concepts)

            ;; slots added to a class that was there before
            (defclass message ()
              ((carried-values :documentation "What values it carries")
               (cautions :documentation "What to watch for"))
              (:documentation "Something the agent loop hands to the program"))

            (defclass loop-option () ()
              (:documentation "A value that changes how the loop runs"))

            (assign "ResultMessage" result-message
              (how-it-ended "p-result")
              (cautions "p-guard")
              (:aside "p-other"))
            (assign "effort level" loop-option)
            """;
    private static final String BOOK = """
            (defrule document -> s)
            (defrule s -> p-result p-guard p-other :heading "## Result")
            (defrule p-result -> :nucleus p-result/n)
            (defrule p-result/n -> "The ResultMessage marks the end of the loop.")
            (defrule p-guard -> :nucleus p-guard/n)
            (defrule p-guard/n -> "After a crash the cost may be zero.")
            (defrule p-other -> :nucleus p-other/n)
            (defrule p-other/n -> "See also the hooks page.")
            """;

    private static ClassPanel panel() {
        return new ClassPanel(Map.of("concepts/messages.lisp", MESSAGES), Map.of("assignments/loop.lisp", RESULT), "/w",
                rel -> rel.equals("doc_Y/loop/loop.lisp") ? BOOK : null, "rst-on-lisp");
    }

    @Test
    void eachSetIsARootWithItsClassesUnderTheirSuperclassAndProposalsMarked() {
        List<Library.TreeNode> t = panel().tree();
        assertEquals("set:", t.get(0).id());
        assertTrue(t.contains(new Library.TreeNode("c:|result-message", "c:|message", "result-message",
                Map.of("data-class", "result-message", "data-set", "", "title", "The message that marks the end of the loop"))), t.toString());
        assertTrue(t.stream().anyMatch(n -> n.id().equals("c:assignments/loop.lisp|loop-option") && n.text().equals("+ loop-option")), t.toString());
        assertTrue(t.stream().anyMatch(n -> n.id().equals("c:assignments/loop.lisp|message") && n.text().equals("± message")), t.toString());
    }

    @Test
    void aClassShowsWhatItInheritsAndTheParagraphsAssignedToEachSlot() {
        String html = panel().render("assignments/loop.lisp", "result-message");
        assertTrue(html.contains("<span class=\"name\">cautions</span> <span class=\"relation\">from <a href=\"#\" class=\"class-link\" data-set=\"assignments/loop.lisp\" data-class=\"message\">message</a>"), html);
        assertTrue(html.contains("data-source=\"doc_Y/loop/loop.lisp\" data-rule=\"p-guard\">p-guard</a>: After a crash the cost may be zero."), html);
        assertTrue(html.contains("<span class=\"name\">:aside</span>"), html);
        assertTrue(html.contains("data-edit-works=\"rst-on-lisp/concepts/messages.lisp\""), html);
    }

    @Test
    void theClassesAloneShowNoProposalsAndNoParagraphs() {
        String html = panel().render("", "message");
        assertTrue(!html.contains("cautions"), html);
        assertTrue(html.contains("Kinds of it</h3><div class=\"child\"><a href=\"#\" class=\"class-link\" data-set=\"\" data-class=\"result-message\">"), html);
    }

    @Test
    void assignFormsAreReadWithTheirSlotsAndParagraphs() {
        List<ClassPanel.Assignment> a = ClassPanel.assignments(RESULT);
        assertEquals(2, a.size());
        assertEquals(List.of("p-result"), a.get(0).slots().get("how-it-ended"));
        assertEquals(List.of("p-other"), a.get(0).slots().get(":aside"));
        assertEquals("loop-option", a.get(1).cls());
    }

    @Test
    void aSuperclassShowsTheParagraphsItsKindsGotForItsSlots() {
        String html = panel().render("assignments/loop.lisp", "message");
        assertTrue(html.contains("<span class=\"badge\"><a href=\"#\" class=\"class-link\" data-set=\"assignments/loop.lisp\" data-class=\"result-message\">result-message</a></span> <a href=\"#\" class=\"raw-rule class-source\" data-source=\"doc_Y/loop/loop.lisp\" data-rule=\"p-guard\">"), html);
    }

    @Test
    void aFileThatStandsAloneIsReadWithoutTheClassesAndListsItsValues() {
        String smaller = """
                ;;; The concept classes of x, made smaller.
                ;;; standalone
                (in-package :concepts)
                (defclass message ()
                  ((carried-values :documentation "What values it carries"))
                  (:documentation "Something the agent loop hands to the program"))
                (instance "result message" message)
                (assign "ResultMessage" message
                  (carried-values "p-result"))
                """;
        ClassPanel p = new ClassPanel(Map.of("concepts/messages.lisp", MESSAGES), Map.of("assignments/loop.smaller.lisp", smaller), "/w",
                rel -> null, "rst-on-lisp");
        List<Library.TreeNode> t = p.tree();
        assertTrue(t.stream().anyMatch(n -> n.id().equals("set:assignments/loop.smaller.lisp") && n.text().endsWith("(made smaller)")), t.toString());
        assertTrue(t.stream().noneMatch(n -> n.id().equals("c:assignments/loop.smaller.lisp|result-message")), "the classes of concepts/ are not read: " + t);
        String html = p.render("assignments/loop.smaller.lisp", "message");
        assertTrue(html.contains("<h3>Values of it</h3><div class=\"child\">result message</div>"), html);
    }

    @Test
    void anExampleIsTheBooksParagraphsWholeInTheOrderOfTheClassesQuestionsWithGaps() {
        String html = panel().render("assignments/loop.lisp", "result-message");
        int ex = html.indexOf("<h3>Examples written by it</h3>");
        assertTrue(ex > 0, html);
        String example = html.substring(ex);
        assertTrue(example.contains("<h4>ResultMessage</h4>"), example);
        // carried-values (from message) comes first and the book does not answer it; then cautions, then how-it-ended
        int carried = example.indexOf("<span class=\"name\">carried-values</span>");
        int cautions = example.indexOf("<span class=\"name\">cautions</span>");
        int ended = example.indexOf("<span class=\"name\">how-it-ended</span>");
        assertTrue(carried > 0 && carried < cautions && cautions < ended, example);
        assertTrue(example.contains("(this book does not answer it for ResultMessage)"), example);
        assertTrue(example.contains("data-rule=\"p-guard\" title=\"open in the source pane\">p-guard</a> After a crash the cost may be zero."), example);
        assertTrue(example.contains("<span class=\"name\">aside</span>"), example);
    }
}
