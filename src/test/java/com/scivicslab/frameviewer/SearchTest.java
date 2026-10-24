package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure tests: the search box's words, matched in any case, all of them, in each tab's tree. */
class SearchTest {

    @Test
    void aTextMatchesWhenItHoldsEveryWordInAnyCase() {
        Search q = new Search("  Integer   TYPES ");
        assertTrue(q.in("the integer types"));
        assertTrue(q.in("integer", null, "floating-point types"), "the words may be in different texts");
        assertFalse(q.in("the integer"));
        assertTrue(new Search(" ").blank());
        assertFalse(new Search("").in("anything"));
    }

    @Test
    void theContentsMatchTitlesAndTheSectionsTitlesThenTextThenRules() {
        BookPanel p = BookPanelTest.panel();
        assertEquals(List.of(new Search.Hit("n:core|4", "title"), new Search.Hit("n:core|4.1.1", "title")), p.searchContents(new Search("classes")));
        assertEquals(List.of(new Search.Hit("s:core|4.1.1", "text")), p.searchSections(new Search("template"), (b, n) -> null));
        assertEquals(List.of(new Search.Hit("s:core|4.1.1", "text")), p.searchSections(new Search("instance"), (b, n) -> null), "a concept of the section");
        assertEquals(List.of(new Search.Hit("s:core|4.1.1", "rules")), p.searchSections(new Search("defrule b1"), (b, n) -> "(defrule b1 -> \"x\")"));
        assertEquals(List.of(), p.searchSections(new Search("nowhere"), (b, n) -> "(defrule b1 -> \"x\")"));
    }

    @Test
    void theRegistryMatchesNamesThenConceptsThenQuestions() {
        RegistryPanel p = RegistryPanelTest.panel();
        assertEquals(List.of(new Search.Hit("r:F0003", "name")), p.search(new Search("f0003")));
        assertEquals(List.of(new Search.Hit("r:F0001", "concept")), p.search(new Search("floating-point")));
        assertEquals(List.of(new Search.Hit("r:F0001", "question")), p.search(new Search("values hold")));
    }
}
