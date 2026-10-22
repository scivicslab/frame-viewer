package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pieces of HTML every panel writes: escaping, and the links to a frame, to a subject tag and to the html-saurus
 * page of a source file. Pure.
 */
public final class Html {

    private static final Pattern NUMBER_PREFIX = Pattern.compile("^\\d+_(.*)$");
    private static final Pattern FRONTMATTER_ID = Pattern.compile("(?m)^id:\\s*(\\S+)\\s*$");

    private Html() {
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i + 1);
    }

    static String frameLink(String path) {
        return "<a href=\"#\" class=\"frame-link\" data-frame=\"" + esc(path) + "\">" + esc(path) + "</a>";
    }

    static String tagLink(Library.Subject s) {
        return "<a href=\"#\" class=\"tag-link\" data-tag=\"" + esc(s.book() + "|" + s.number()) + "\">"
                + esc(s.book() + " " + s.number() + " " + s.title()) + "</a>";
    }

    /**
     * A link the page turns into a code-server URL that opens one file for editing: a frame, by its path under
     * {@code frames/}, or a source file, by its path under the works directory. The page knows both directories.
     */
    static String editFrameLink(String framePath) {
        return "<a href=\"#\" class=\"edit-link\" data-edit-frame=\"" + esc(framePath) + "\" title=\"edit in code-server\">edit</a>";
    }

    static String editWorksLink(String worksPath) {
        return "<a href=\"#\" class=\"edit-link\" data-edit-works=\"" + esc(worksPath) + "\" title=\"edit in code-server\">edit</a>";
    }

    /**
     * A link the page turns into {@code http://<its own host>:<port>/#/<page>}: the html-saurus portal, opened on the
     * page of one source file. html-saurus is reached on its own port, not through the AI workspace's path prefix.
     */
    static String saurusLink(String page, String text) {
        return "<a href=\"#\" class=\"saurus-link\" data-saurus=\"" + esc(page) + "\">" + esc(text) + "</a>";
    }

    /**
     * The html-saurus page of a markdown file, the way html-saurus builds it: {@code docs/} is not part of the path,
     * a file named after its directory (or {@code index.md}) is the page of that directory, every {@code NN_} prefix
     * is dropped, and an {@code id:} in the front matter replaces the last segment.
     *
     * @param md     path under the works directory, {@code doc_X/docs/A/01_b/01_b.md}
     * @param mdText the file's text, or null
     * @return {@code doc_X/A/b/}, or null when the path is not under a project's {@code docs/}
     */
    static String saurusPage(String md, String mdText) {
        String[] p = md.split("/");
        if (p.length < 3 || !p[1].equals("docs") || !md.endsWith(".md")) return null;
        List<String> segs = new ArrayList<>(List.of(p).subList(2, p.length));
        String name = segs.remove(segs.size() - 1);
        name = name.substring(0, name.length() - 3);
        boolean ofDirectory = name.equals("index") || !segs.isEmpty() && name.equals(segs.get(segs.size() - 1));
        if (!ofDirectory) segs.add(name);
        List<String> out = new ArrayList<>();
        for (String s : segs) {
            Matcher m = NUMBER_PREFIX.matcher(s);
            out.add(m.matches() ? m.group(1) : s);
        }
        if (mdText != null) {
            Matcher id = FRONTMATTER_ID.matcher(frontMatter(mdText));
            if (id.find() && !out.isEmpty()) out.set(out.size() - 1, id.group(1));
        }
        return p[0] + "/" + String.join("/", out) + (out.isEmpty() ? "" : "/");
    }

    private static String frontMatter(String text) {
        if (!text.startsWith("---")) return "";
        int end = text.indexOf("\n---", 3);
        return end < 0 ? "" : text.substring(3, end);
    }
}
