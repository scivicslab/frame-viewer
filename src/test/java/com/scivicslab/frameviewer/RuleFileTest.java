package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure tests of the rule-file reader: no Quarkus, no I/O. */
class RuleFileTest {

    private static final String FRAME = """
            ;;; use: Explains one kind of data type: what it holds,
            ;;;      and the tests that go wrong.
            ;;; integrates: subject/data-types/integer-types.lisp
            ;;; integrates: subject/data-types/floating-point-types.lisp
            ;;; roles (role: integer-types / floating-point-types):
            ;;;   p-what:      p-what / p-what
            (in-package :rst)
            (defrule document -> title sections)
            ; a comment with a ) inside
            (defrule p-what -> :nucleus p-what/n :elaboration-set-member p-what/members :rank secondary)
            (defrule p-what/n -> :instruction "say \\"what\\" it is" :sample "The integer types are for numbers.")
            (defrule p-what/members -> "Java provides four.")
            """;

    @Test
    void headersKeepEveryValueAndJoinContinuationLines() {
        RuleFile f = RuleFile.parse(FRAME);
        assertEquals(List.of("Explains one kind of data type: what it holds,\nand the tests that go wrong."), f.header("use"));
        assertEquals(2, f.header("integrates").size());
        assertEquals("p-what:      p-what / p-what", f.header("roles").get(0).strip());
    }

    @Test
    void rulesKeepTheirTokensAndAttributes() {
        RuleFile f = RuleFile.parse(FRAME);
        assertEquals(List.of("document", "p-what", "p-what/n", "p-what/members"), f.rules().stream().map(RuleFile.Rule::name).toList());
        assertEquals("secondary", f.rule("p-what").attr(":rank"));
        assertTrue(f.rule("p-what/n").isBlank());
        assertEquals("say \"what\" it is", f.rule("p-what/n").attr(":instruction"));
        assertFalse(f.rule("p-what").isBlank());
    }

    @Test
    void textOfFollowsRelationsButNotAttributes() {
        RuleFile f = RuleFile.parse("""
                (defrule s1 -> p1 :heading "## Not text")
                (defrule p1 -> :nucleus p1/n :elaboration-additional p1/more)
                (defrule p1/n -> "First.")
                (defrule p1/more -> "Second.")
                """);
        assertEquals(List.of("First.", "Second."), f.textOf("s1"));
    }
}
