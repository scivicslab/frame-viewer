package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Writes the right-hand panel for one frame as an HTML fragment: its header lines, the frames it integrates or is
 * integrated into, its rules nested as the frame nests them (with each blank's instruction, sample, relation and
 * rank, and the child frames of {@code :include} and {@code :frame} opened in place), and the paragraphs of the
 * source text its {@code members}, {@code elsewhere} and {@code left-out} lines name.
 *
 * <p>Pure: the frame library is handed in, and the books' rule files are read through {@code readWorks}, which takes
 * a path under the works directory and returns the file's text or null.
 */
public final class FramePanel {

    private static final int MAX_DEPTH = 40;

    private final Library library;
    private final Function<String, String> readWorks;

    public FramePanel(Library library, Function<String, String> readWorks) {
        this.library = library;
        this.readWorks = readWorks;
    }

    /** @return the fragment, or a short notice when there is no such frame */
    public String render(String path) {
        Library.Frame frame = library.frame(path);
        if (frame == null) return "<p class=\"hint\">No frame " + esc(path) + ".</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"frame\"><h2>").append(esc(path)).append("</h2>");
        headers(frame, sb);
        links("Integrates", frame.integrates(), sb);
        List<String> into = new ArrayList<>();
        for (Library.Frame f : library.integratedInto(path)) into.add(f.path());
        links("Integrated into", into, sb);
        sb.append("<h3>Structure</h3><ul class=\"rules\">");
        List<RuleFile.Rule> rules = frame.file().rules();
        if (!rules.isEmpty()) {
            rule(frame.file(), dirOf(path), rules.get(0).name(), null, new HashSet<>(Set.of(path)), 0, sb);
        }
        sb.append("</ul>");
        sources(frame, sb);
        sb.append("</div>");
        return sb.toString();
    }

    private void headers(Library.Frame frame, StringBuilder sb) {
        if (frame.file().headers().isEmpty()) return;
        sb.append("<table class=\"headers\">");
        for (Map.Entry<String, List<String>> e : frame.file().headers().entrySet()) {
            for (String v : e.getValue()) {
                sb.append("<tr><th>").append(esc(e.getKey())).append("</th><td>").append(esc(v)).append("</td></tr>");
            }
        }
        sb.append("</table>");
    }

    private static void links(String label, List<String> paths, StringBuilder sb) {
        if (paths.isEmpty()) return;
        sb.append("<p class=\"links\"><b>").append(label).append(":</b> ");
        for (String p : paths) {
            sb.append("<a href=\"#\" class=\"frame-link\" data-frame=\"").append(esc(p)).append("\">").append(esc(p)).append("</a> ");
        }
        sb.append("</p>");
    }

    /** Writes one rule as a list item, with the rules it names (or the child frame it points to) inside it. */
    private void rule(RuleFile file, String dir, String name, String relation, Set<String> openFrames, int depth, StringBuilder sb) {
        RuleFile.Rule r = file.rule(name);
        sb.append("<li class=\"rule");
        if (r != null && r.isBlank()) sb.append(" blank");
        if (r != null && r.attr(":rank") != null) sb.append(" rank-").append(esc(r.attr(":rank")));
        sb.append("\">");
        if (relation != null) sb.append("<span class=\"relation\">").append(esc(relation.substring(1))).append("</span> ");
        sb.append("<span class=\"name\">").append(esc(name)).append("</span>");
        if (r == null) {
            sb.append(" <span class=\"hint\">(not defined)</span></li>");
            return;
        }
        badge(":heading", r, sb);
        badge(":rank", r, sb);
        badge(":kind", r, sb);
        if (depth >= MAX_DEPTH) {
            sb.append(" <span class=\"hint\">(too deep)</span></li>");
            return;
        }
        if (r.isBlank()) {
            sb.append("<div class=\"instruction\">").append(esc(r.attr(":instruction"))).append("</div>");
            if (r.attr(":sample") != null) sb.append("<div class=\"sample\">").append(esc(r.attr(":sample"))).append("</div>");
            child(r, ":include", "included once", dir, openFrames, depth, sb);
            child(r, ":frame", "one copy per item", dir, openFrames, depth, sb);
            sb.append("</li>");
            return;
        }
        List<RuleFile.Token> items = r.items();
        StringBuilder inner = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            RuleFile.Token t = items.get(i);
            if (t.kind() == RuleFile.Token.Kind.KEYWORD) {
                if (RuleFile.isRelationKeyword(t.text()) && i + 1 < items.size()) {
                    rule(file, dir, items.get(++i).text(), t.text(), openFrames, depth + 1, inner);
                } else {
                    i++;
                }
            } else if (t.kind() == RuleFile.Token.Kind.STRING) {
                inner.append("<li class=\"text\">").append(esc(t.text())).append("</li>");
            } else {
                rule(file, dir, t.text(), null, openFrames, depth + 1, inner);
            }
        }
        if (inner.length() > 0) sb.append("<ul>").append(inner).append("</ul>");
        sb.append("</li>");
    }

    private void child(RuleFile.Rule r, String key, String label, String dir, Set<String> openFrames, int depth, StringBuilder sb) {
        String target = r.attr(key);
        if (target == null) return;
        String path = dir + target;
        sb.append("<div class=\"child\">").append(label).append(": <a href=\"#\" class=\"frame-link\" data-frame=\"")
                .append(esc(path)).append("\">").append(esc(target)).append("</a></div>");
        Library.Frame child = library.frame(path);
        if (child == null || openFrames.contains(path) || child.file().rules().isEmpty()) return;
        openFrames.add(path);
        sb.append("<ul class=\"rules child-frame\">");
        rule(child.file(), dirOf(path), child.file().rules().get(0).name(), null, openFrames, depth + 1, sb);
        sb.append("</ul>");
        openFrames.remove(path);
    }

    private static void badge(String key, RuleFile.Rule r, StringBuilder sb) {
        String v = r.attr(key);
        if (v != null) sb.append(" <span class=\"badge\">").append(esc(key.substring(1))).append(": ").append(esc(v)).append("</span>");
    }

    /** The paragraphs of the source text named by the frame's {@code members}, {@code elsewhere} and {@code left-out} lines. */
    private void sources(Library.Frame frame, StringBuilder sb) {
        List<RuleFile> books = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (String v : frame.file().header("made-from")) {
            for (String dir : sourceDirs(v)) {
                String file = dir + "/" + dir.substring(dir.lastIndexOf('/') + 1) + ".lisp";
                String text = readWorks.apply(file);
                if (text != null) {
                    books.add(RuleFile.parse(text));
                    files.add(file);
                }
            }
        }
        if (books.isEmpty()) return;
        sb.append("<h3>Source</h3><p class=\"hint\">");
        for (String f : files) sb.append(esc(f)).append("<br>");
        sb.append("</p>");
        for (String key : List.of("members", "elsewhere", "left-out")) {
            Map<String, List<String>> found = new LinkedHashMap<>();
            for (String v : frame.file().header(key)) {
                for (String word : v.split("[\\s,;()]+")) {
                    if (found.containsKey(word)) continue;
                    for (RuleFile b : books) {
                        if (b.rule(word) != null) {
                            found.put(word, b.textOf(word));
                            break;
                        }
                    }
                }
            }
            if (found.isEmpty()) continue;
            sb.append("<h4>").append(esc(key)).append("</h4><dl class=\"source\">");
            for (Map.Entry<String, List<String>> e : found.entrySet()) {
                sb.append("<dt>").append(esc(e.getKey())).append("</dt><dd>").append(esc(String.join(" ", e.getValue()))).append("</dd>");
            }
            sb.append("</dl>");
        }
    }

    /**
     * The directories a {@code made-from} value names: the part before {@code " / "}, which may list several
     * directories, each starting with {@code doc_}. Directory names may hold spaces.
     */
    static List<String> sourceDirs(String madeFrom) {
        int slash = madeFrom.indexOf(" / ");
        String left = slash < 0 ? madeFrom : madeFrom.substring(0, slash);
        List<String> out = new ArrayList<>();
        for (String part : left.split(" (?=doc_)")) {
            String p = part.strip();
            if (p.startsWith("doc_")) out.add(p);
        }
        return out;
    }

    static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i + 1);
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
