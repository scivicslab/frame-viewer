package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Pure tests of the html-saurus page path, which must match the one html-saurus builds. */
class HtmlTest {

    @Test
    void aFileNamedAfterItsDirectoryIsThePageOfTheDirectory() {
        assertEquals("doc_Base011/Books-Programming/GlobusToolkit/Chap23/globus_toolkit_p391-400/",
                Html.saurusPage("doc_Base011/docs/Books-Programming/GlobusToolkit/Chap23/globus_toolkit_p391-400/globus_toolkit_p391-400.md", ""));
    }

    @Test
    void numberPrefixesAreDroppedAndTheIdReplacesTheLastSegment() {
        assertEquals("doc_Base010/Frame-Viewer/concepts/FrameViewerConcept_261021_oo01/",
                Html.saurusPage("doc_Base010/docs/Frame-Viewer/010_concepts/010_FrameViewerConcept_261021_oo01/010_FrameViewerConcept_261021_oo01.md",
                        "---\nid: FrameViewerConcept_261021_oo01\ntitle: x\n---\n# body\nid: not this\n"));
    }

    @Test
    void aFileNotNamedAfterItsDirectoryIsAPageOfItsOwn() {
        assertEquals("doc_A/x/note/", Html.saurusPage("doc_A/docs/01_x/note.md", null));
        assertEquals("doc_A/x/", Html.saurusPage("doc_A/docs/01_x/index.md", null));
    }

    @Test
    void aFileOutsideAProjectsDocsHasNoPage() {
        assertNull(Html.saurusPage("doc_A/blog/x.md", null));
    }
}
