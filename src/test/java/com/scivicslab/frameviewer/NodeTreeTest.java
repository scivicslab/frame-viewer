package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.scivicslab.turingworkflow.plugins.rstframe.BookStore;

/**
 * Pure tests: the left tree of the Workflows and Integrated frames pages, drawn by the server as links and open only down
 * to the page's item, the children a closed branch is filled with, and the page of a search's results.
 */
class NodeTreeTest {

    /** A registry of four frames: data type above integer type above int, and variable at the top beside it. */
    static RegistryPanel registry() {
        var dataType = new BookStore.RegistryFrame("F0001", "data type", null, List.of(new BookStore.RegistryMember("core", "3.3", "data types")));
        var integer = new BookStore.RegistryFrame("F0002", "integer type", "F0001", List.of());
        var intType = new BookStore.RegistryFrame("F0003", "int", "F0002", List.of());
        var variable = new BookStore.RegistryFrame("F0004", "variable", null, List.of());
        return new RegistryPanel(List.of(dataType, integer, intType, variable), Map.of(), List.of(), (b, k) -> null, (b, n) -> null);
    }

    @Test
    void theRegistryTreeIsOpenOnlyDownToTheFrameOfThePage() {
        NodeTree t = ViewerResource.registryTree(registry());
        String top = t.html(null, "");
        assertTrue(top.contains("<a href=\"frames\">generalized frames (4)</a>"), "the root leads to the page of all frames: " + top);
        assertTrue(top.contains("<li class=\"branch\"><span class=\"twisty\" data-tree=\"tree/frames/F0001\"></span><a href=\"frames/F0001\">F0001 data type"),
                "a frame at the top is drawn, closed, with where its kinds are read from: " + top);
        assertTrue(top.contains("<li class=\"leaf\"><a href=\"frames/F0004\">F0004 variable"), top);
        assertFalse(top.contains("F0002"), "nothing under a closed branch is drawn: " + top);

        String deep = t.html("r:F0002", "../");
        assertTrue(deep.contains("<li class=\"branch open\"><span class=\"twisty\"></span><a href=\"../frames/F0001\">"), "the branch down to the page is open: " + deep);
        assertTrue(deep.contains("<a href=\"../frames/F0002\" class=\"current\">F0002 integer type"), "the page's frame is marked: " + deep);
        assertTrue(deep.contains("<li class=\"leaf\"><a href=\"../frames/F0003\">F0003 int"), "the frames right under it are drawn: " + deep);
    }

    @Test
    void aClosedBranchIsFilledWithTheNodesRightUnderIt() {
        NodeTree t = ViewerResource.registryTree(registry());
        assertEquals(List.of(new NodeTree.Entry("F0002 integer type  · 0 concrete", "frames/F0002", true, "tree/frames/F0002")), t.children("r:F0001"));
        assertEquals(List.of(new NodeTree.Entry("F0003 int  · 0 concrete", "frames/F0003", false, null)), t.children("r:F0002"));
        assertEquals(null, t.children("r:F9999"));
    }

    @Test
    void theWorkflowsAreAFlatListOfLinksInTheOrderTheyRun() {
        WorkflowPanel w = new WorkflowPanel(List.of("book-toc", "book-sections"),
                Map.of("book-toc", "name: book-toc\ndescription: Reads the table of contents.\n", "book-sections", "name: book-sections\n"), null);
        String html = ViewerResource.workflowTree(w).html("w:book-sections", "../");
        assertTrue(html.contains("<li class=\"leaf\"><a href=\"../workflows/book-toc\" title=\"Reads the table of contents.\">1. book-toc</a></li>"), html);
        assertTrue(html.contains("<a href=\"../workflows/book-sections\" class=\"current\">2. book-sections</a>"), html);
    }

    @Test
    void aSearchListsEachItemFoundAsALinkToItsPage() {
        RegistryPanel r = registry();
        String html = ViewerResource.registryTree(r).searchBody("Search the frames", "integer", r.search(new Search("integer")), "");
        assertTrue(html.contains("<h2>Search the frames: integer</h2><p class=\"hint\">1 found</p>"), html);
        assertTrue(html.contains("<li><a href=\"frames/F0002\">F0002 integer type  · 0 concrete</a> <span class=\"hint\">in name</span></li>"), html);
    }
}
