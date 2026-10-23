package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.scivicslab.turingworkflow.plugins.rstframe.BookStore;

/** Pure tests: the concepts of a book across its sections, and a generalized concept above them. */
class EntityPanelTest {

    private static EntityPanel panel() {
        var intType = new BookStore.Entity("integer type", "integer types", BookStore.Entity.CONCEPT,
                List.of(new BookStore.Occurrence("3.3.1", "integer types"), new BookStore.Occurrence("3.5.1", "integer type")), List.of());
        var floatType = new BookStore.Entity("floating-point type", "floating-point types", BookStore.Entity.CONCEPT,
                List.of(new BookStore.Occurrence("3.3.2", "floating-point types")), List.of());
        var primitive = new BookStore.Entity("general:primitive type", "primitive type", BookStore.Entity.GENERAL, List.of(),
                List.of("integer type", "floating-point type"));
        var r1 = new BookStore.FrameRule("property", "b1/1", "The integer types are for numbers without fractional parts.");
        var r2 = new BookStore.FrameRule("property", "b1/1", "Floating-point types denote numbers with fractional parts.");
        Map<String, BookStore.FrameConcept> conceptOf = Map.of(
                "3.3.1\tinteger types", new BookStore.FrameConcept("integer types", List.of(r1), true),
                "3.3.2\tfloating-point types", new BookStore.FrameConcept("floating-point types", List.of(r2), true));
        Map<String, String> files = Map.of(
                "3.3.1", "(defrule b1/1 -> \"The integer types are for numbers without fractional parts.\")\n",
                "3.3.2", "(defrule b1/1 -> \"Floating-point types denote numbers with fractional parts.\")\n");
        return new EntityPanel("core", List.of(intType, floatType, primitive),
                Map.of("general:primitive type", List.of(new BookStore.FrameItem("What numbers does it hold?", List.of("3.3.1:b1/1", "3.3.2:b1/1")))),
                Map.of("3.3.2\tfloating-point types", List.of(new BookStore.FrameItem("What do they denote?", List.of("b1/1")))),
                conceptOf, files::get);
    }

    @Test
    void theTreeHasTheGeneralizedConceptsWithTheirKindsThenTheConceptsOfSeveralSectionsThenOfOne() {
        List<Library.TreeNode> t = panel().tree();
        assertEquals("core: generalized concepts (1)", t.get(0).text());
        assertEquals("primitive type  · 2 kinds · 1 items", t.get(1).text());
        assertEquals("integer types  · 2 section(s)", t.get(2).text());
        assertEquals(t.get(1).id(), t.get(2).parent());
        assertEquals("core: concepts in several sections (1)", t.get(4).text());
        assertEquals("core: concepts in one section (1)", t.get(6).text());
    }

    @Test
    void aGeneralizedConceptShowsItsKindsItsFrameAndTheMaterialOfItsKinds() {
        String html = panel().entity("general:primitive type");
        assertTrue(html.contains("data-entity=\"integer type\">integer types</a>"), html);
        assertTrue(html.contains("<td class=\"concept\">What numbers does it hold?</td><td class=\"rules\"><pre class=\"rule-lisp\"><span class=\"hint\">3.3.1</span> (defrule b1/1"), html);
        assertTrue(html.contains("<div class=\"frame-kind\">floating-point types · 3.3.2</div>"), html);
    }

    @Test
    void aConceptOfOneSectionShowsThatSectionsItems() {
        String html = panel().entity("floating-point type");
        assertTrue(html.contains("<td class=\"concept\">What do they denote?</td><td class=\"rules\"><pre class=\"rule-lisp\"><span class=\"hint\">3.3.2</span>"), html);
    }
}
