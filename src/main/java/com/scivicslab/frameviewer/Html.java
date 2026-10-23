package com.scivicslab.frameviewer;

/** The pieces of HTML every panel writes: escaping, and the link that opens a file for editing in code-server. Pure. */
public final class Html {

    private Html() {
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * A link the page turns into a code-server URL that opens one file for editing, by its path under the works
     * directory, which the page knows.
     */
    static String editWorksLink(String worksPath) {
        return "<a href=\"#\" class=\"edit-link\" data-edit-works=\"" + esc(worksPath) + "\" title=\"edit in code-server\">edit</a>";
    }
}
