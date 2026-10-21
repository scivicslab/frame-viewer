package com.scivicslab.frameviewer;

import static com.scivicslab.frameviewer.FramePanel.esc;
import static com.scivicslab.frameviewer.FramePanel.frameLink;
import static com.scivicslab.frameviewer.FramePanel.tagLink;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a concrete frame and one of its source files as they are on disk, for the two panes of the concrete-frame
 * tab. In the frame, each word of a header line that names a paragraph of a source file is a link that brings that
 * paragraph into view in the source pane. In the source, every {@code defrule} line starts with an anchor, and the
 * lines of the paragraphs the frame names (with their sub-rules, {@code p-plan/n} under {@code p-plan}) are marked.
 * Pure.
 */
public final class RawPanel {

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_/*.-]*");
    private static final Pattern KEY = Pattern.compile(";;; ([A-Za-z][A-Za-z0-9-]*)( \\([^)]*\\))?:");
    /** The header lines whose words name paragraphs of the source; other lines (subject, children) are prose. */
    private static final Set<String> NAMING = Set.of("made-from", "members", "elsewhere", "left-out");
    private static final Pattern DEFRULE = Pattern.compile("^\\(defrule\\s+(\\S+)");

    private final Library library;
    private final Sources sources;

    public RawPanel(Library library, Sources sources) {
        this.library = library;
        this.sources = sources;
    }

    /** The frame pane: links to its tag, top frame and integrated frames, its source files, then its text. */
    public String frame(String path, String text) {
        Library.Frame frame = library.frame(path);
        if (frame == null || text == null) return "<p class=\"hint\">No frame " + esc(path) + ".</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"raw-head\"><b>").append(esc(path)).append("</b>");
        Library.Subject tag = library.subjectOf(frame);
        if (tag != null) sb.append("<br>Tag: ").append(tagLink(tag));
        if (!frame.isTop() && library.frame(frame.topPath()) != null) sb.append("<br>Part of: ").append(frameLink(frame.topPath()));
        List<String> into = new ArrayList<>();
        for (String p : List.of(path, frame.topPath())) {
            for (Library.Frame f : library.integratedInto(p)) if (!into.contains(f.path())) into.add(f.path());
        }
        if (!into.isEmpty()) {
            sb.append("<br>Integrated into:");
            for (String p : into) sb.append(" ").append(frameLink(p));
        }
        sb.append("<div class=\"raw-files\">");
        for (String file : sources.filesOf(path)) {
            sb.append("<a href=\"#\" class=\"raw-file\" data-source=\"").append(esc(file)).append("\">")
                    .append(esc(file.substring(file.lastIndexOf('/') + 1))).append("</a> ");
        }
        sb.append("</div></div><pre class=\"raw\">");
        String key = null;
        for (String line : text.split("\n", -1)) {
            Matcher k = KEY.matcher(line);
            if (k.lookingAt()) key = k.group(1);
            else if (!line.startsWith(";;;   ")) key = null;
            if (key != null && NAMING.contains(key)) linkRules(path, line, sb);
            else sb.append(esc(line));
            sb.append("\n");
        }
        sb.append("</pre>");
        return sb.toString();
    }

    private void linkRules(String path, String line, StringBuilder sb) {
        Matcher m = WORD.matcher(line);
        int at = 0;
        while (m.find()) {
            String file = sources.fileOf(path, m.group());
            if (file == null) continue;
            sb.append(esc(line.substring(at, m.start())));
            sb.append("<a href=\"#\" class=\"raw-rule\" data-source=\"").append(esc(file)).append("\" data-rule=\"")
                    .append(esc(m.group())).append("\">").append(esc(m.group())).append("</a>");
            at = m.end();
        }
        sb.append(esc(line.substring(at)));
    }

    /** The source pane: the file's text, each defrule line anchored, the frame's paragraphs marked. */
    public String source(String file, String text, String framePath) {
        if (text == null || sources.file(file) == null) return "<p class=\"hint\">No source " + esc(file) + ".</p>";
        Set<String> named = new HashSet<>();
        RuleFile f = sources.file(file);
        for (RuleFile.Rule r : f.rules()) {
            for (Sources.Claim c : sources.claimsOf(file, r.name())) if (c.frame().equals(framePath)) named.add(r.name());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"raw-head\"><b>").append(esc(file)).append("</b></div><pre class=\"raw\">");
        boolean hit = false;
        for (String line : text.split("\n", -1)) {
            Matcher m = DEFRULE.matcher(line);
            if (m.find()) {
                String rule = m.group(1);
                int slash = rule.indexOf('/');
                hit = named.contains(rule) || slash > 0 && named.contains(rule.substring(0, slash));
                sb.append("<span id=\"L-").append(esc(rule)).append("\"></span>");
            } else if (line.startsWith("(") || line.isBlank()) {
                hit = false;
            }
            if (hit) sb.append("<span class=\"hit\">").append(esc(line)).append("</span>");
            else sb.append(esc(line));
            sb.append("\n");
        }
        sb.append("</pre>");
        return sb.toString();
    }
}
