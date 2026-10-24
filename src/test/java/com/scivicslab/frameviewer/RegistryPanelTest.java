package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.scivicslab.turingworkflow.plugins.rstframe.BookStore;

/** Pure tests: the registry of generalized frames as an is-a tree, and one frame with its relations, frame and material. */
class RegistryPanelTest {

    static RegistryPanel panel() {
        var dataType = new BookStore.RegistryFrame("F0001", "data type", null, List.of(
                new BookStore.RegistryMember("core", "3.3.1", "integer types"), new BookStore.RegistryMember("core", "3.3.2", "floating-point types")));
        var variable = new BookStore.RegistryFrame("F0002", "variable", null, List.of(new BookStore.RegistryMember("core", "3.4", "variable")));
        var constant = new BookStore.RegistryFrame("F0003", "constant", "F0002", List.of(new BookStore.RegistryMember("core", "3.4.3", "constant")));
        var r1 = new BookStore.FrameRule("property", "b1/1", "The integer types are for numbers without fractional parts.");
        Map<String, BookStore.FrameConcept> material = Map.of("3.3.1\tinteger types", new BookStore.FrameConcept("integer types", List.of(r1), true));
        return new RegistryPanel(List.of(dataType, variable, constant),
                Map.of("F0001", List.of(new BookStore.FrameItem("What values does it hold?", List.of("core|3.3.1:b1/1")))),
                List.of(new BookStore.FrameRelation("F0002", "feature", "F0001", "core", "3.4")),
                (b, k) -> material.get(k), (b, n) -> n.equals("3.3.1") ? "(defrule b1/1 -> \"The integer types are for numbers without fractional parts.\")\n" : null);
    }

    @Test
    void theTreeIsTheIsATreeOfTheRegistry() {
        List<TreeNode> t = panel().tree();
        assertEquals("generalized frames (3)", t.get(0).text());
        assertEquals("F0001 data type  · 2 concrete · 1 items", t.get(1).text());
        assertEquals("reg", t.get(1).parent());
        assertEquals("r:F0002", t.get(3).parent(), "constant is a kind of variable");
    }

    @Test
    void theGraphHasEveryFrameTheTreesLinksAndTheRelations() {
        RegistryPanel.Graph g = panel().graph();
        assertEquals(List.of("F0001", "F0002", "F0003"), g.nodes().stream().map(RegistryPanel.GraphNode::id).toList());
        assertEquals(2, g.nodes().get(0).size());
        assertEquals(List.of(new RegistryPanel.GraphEdge("F0003", "F0002", "is-a (tree)", 1), new RegistryPanel.GraphEdge("F0002", "F0001", "feature", 1)), g.edges());
    }

    @Test
    void aFrameShowsItsPlaceInTheTreeItsRelationsItsFrameAndItsMaterial() {
        String html = panel().frame("F0001");
        assertTrue(html.contains("<li>a kind of: <span class=\"hint\">(the top)</span></li>"), html);
        assertTrue(html.contains("data-frame-id=\"F0002\">F0002 variable</a> <b>feature</b> data type"), html);
        assertTrue(html.contains("<td class=\"concept\">What values does it hold?</td><td class=\"rules\"><pre class=\"rule-lisp\"><span class=\"hint\">3.3.1</span> (defrule b1/1"), html);
        assertTrue(html.contains("<div class=\"frame-kind\">integer types · 3.3.1</div>"), html);
        assertTrue(panel().frame("F0002").contains("its kinds: <a href=\"#\" class=\"frame-id-link\" data-frame-id=\"F0003\">F0003 constant</a>"));
    }
}
