package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.scivicslab.turingworkflow.plugins.rstframe.BookOutline;
import com.scivicslab.turingworkflow.plugins.rstframe.BookSections;

/** Pure tests: a book's table of contents as a tree, its sections as a list, and their panels. */
class BookPanelTest {

    private static BookPanel panel() {
        List<BookOutline.Node> outline = List.of(
                new BookOutline.Node("4", "chapter", "Objects and Classes", null, "Chap04/p001.md"),
                new BookOutline.Node("4.1", "section", "Introduction", "4", "Chap04/p001.md"),
                new BookOutline.Node("4.1.1", "subsection", "Classes", "4.1", "Chap04/p001.md"));
        List<BookSections.Section> sections = List.of(
                new BookSections.Section("4", "In this chapter.", List.of("Chap04/p001.md")),
                new BookSections.Section("4.1", "", List.of("Chap04/p001.md")),
                new BookSections.Section("4.1.1", "A class is a template.\n\nIt runs on.", List.of("Chap04/p001.md", "Chap04/p006.md")));
        return new BookPanel(List.of(new BookPanel.Book("core", "/w/books/core", outline, sections)), "/w");
    }

    @Test
    void theContentsAreATreeUnderTheBookAndTheSectionsAListInTheBooksOrder() {
        List<Library.TreeNode> c = panel().contentsTree();
        assertEquals("core (3 nodes)", c.get(0).text());
        assertEquals(new Library.TreeNode("n:core|4.1.1", "n:core|4.1", "4.1.1 Classes",
                java.util.Map.of("data-book", "core", "data-node", "4.1.1", "data-level", "subsection")), c.get(3));
        List<Library.TreeNode> s = panel().sectionsTree();
        assertEquals("core (3 sections)", s.get(0).text());
        assertEquals("4.1.1 Classes  · 35 chars, 2 files", s.get(3).text());
        assertEquals("sb:core", s.get(3).parent());
    }

    @Test
    void aNodeShowsWhereItStandsWhatIsUnderItAndItsText() {
        String html = panel().node("core", "4.1");
        assertTrue(html.contains("<h2>4.1 Introduction</h2>"), html);
        assertTrue(html.contains("data-node=\"4\">4 Objects and Classes</a>"), html);
        assertTrue(html.contains("<h3>Under it</h3><div class=\"child\"><a href=\"#\" class=\"node-link\" data-book=\"core\" data-node=\"4.1.1\">4.1.1 Classes</a>"), html);
        assertTrue(html.contains("class=\"section-link\" data-book=\"core\" data-section=\"4.1\">0 chars</a>"), html);
    }

    @Test
    void aSectionShowsItsFilesAndItsText() {
        String html = panel().section("core", "4.1.1");
        assertTrue(html.contains("<h2>4.1.1 Classes</h2>"), html);
        assertTrue(html.contains("Chap04/p006.md <a href=\"#\" class=\"edit-link\" data-edit-works=\"books/core/Chap04/p006.md\""), html);
        assertTrue(html.contains("<pre class=\"raw md\">A class is a template.\n\nIt runs on.</pre>"), html);
        assertTrue(panel().section("core", "4.1").contains("(no text of its own: its subsections hold it)"));
    }
}
