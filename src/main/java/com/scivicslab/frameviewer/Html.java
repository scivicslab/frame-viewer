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
     * A page's path from the viewer's top, each segment percent-encoded ({@code sections/世界史概観/1.2} becomes
     * {@code sections/%E4%B8%96...}). It is written without a leading slash: a page puts the {@code ../} that lead back to the
     * top in front of it, since the AI workspace strips its own prefix and the viewer never learns it.
     */
    static String path(String... segments) {
        StringBuilder sb = new StringBuilder();
        for (String s : segments) {
            if (sb.length() > 0) sb.append('/');
            sb.append(java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    /**
     * The {@code ../} that lead from a page back to the viewer's top: one for each segment of its path after the first.
     * {@code sections/core/4.1} needs {@code ../../}; {@code sections} and {@code search} need nothing.
     */
    static String base(int segments) {
        return "../".repeat(Math.max(0, segments - 1));
    }

    /** A plain link to the page of a node of a book's table of contents. */
    static String sectionLink(String base, String book, String number, String text) {
        return "<a href=\"" + esc(base + path("sections", book, number)) + "\">" + esc(text) + "</a>";
    }

    /**
     * A link the page turns into a code-server URL that opens one file for editing, by its path under the works
     * directory, which the page knows.
     */
    static String editWorksLink(String worksPath) {
        return "<a href=\"#\" class=\"edit-link\" data-edit-works=\"" + esc(worksPath) + "\" title=\"edit in code-server\">edit</a>";
    }
}
