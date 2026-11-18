package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scivicslab.turingworkflow.plugins.rstframe.BookOutline;
import com.scivicslab.turingworkflow.plugins.rstframe.BookStore;

/**
 * The viewer keeps the books it read until the database's change count moves: a click that changes nothing reads no book
 * again, and a workflow's write shows on the next click. The database is an H2 file in a temporary directory.
 */
class BooksCacheTest {

    @TempDir
    Path dir;

    static BookOutline.Outline outline(String title) {
        return new BookOutline.Outline(List.of(new BookOutline.Node("1", "chapter", title, null, "a.md")), List.of());
    }

    @Test
    void theBooksAreReadAgainOnlyWhenTheDataVersionMoves() throws Exception {
        ViewerResource viewer = new ViewerResource();
        viewer.booksDb = dir.resolve("books").toString();
        viewer.worksDir = dir.toString();
        // the writer opens the database as the viewer does, so that both share it in this one process
        String url = "jdbc:h2:" + dir.resolve("books") + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
        try (BookStore writer = BookStore.forWriting(url)) {
            writer.saveOutline("core", "/w/books/core", outline("One"));

            ViewerResource.Loaded first = viewer.loaded();
            assertEquals(List.of("core"), first.panel().books());
            assertSame(first, viewer.loaded(), "nothing was written: the books are not read again");
            viewer.bookPanel().section("core", "1", "md", null);
            assertSame(first, viewer.loaded(), "a page that only reads leaves them as they are");

            writer.saveOutline("java", "/w/books/java", outline("Two"));
            ViewerResource.Loaded second = viewer.loaded();
            assertNotSame(first, second, "a write moved the data version");
            assertEquals(List.of("core", "java"), second.panel().books());
            assertSame(second, viewer.loaded());
        }
    }
}
