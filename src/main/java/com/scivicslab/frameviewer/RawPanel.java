package com.scivicslab.frameviewer;

import static com.scivicslab.frameviewer.Html.editFrameLink;
import static com.scivicslab.frameviewer.Html.editWorksLink;
import static com.scivicslab.frameviewer.Html.esc;
import static com.scivicslab.frameviewer.Html.frameLink;
import static com.scivicslab.frameviewer.Html.saurusLink;
import static com.scivicslab.frameviewer.Html.tagLink;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a concrete frame and one of its source files as they are on disk, for the two panes of the concrete-frame
 * tab. A frame is made from one or more source files; the source pane opens with a drop-down list of them. In the frame, each word of a header line that names a paragraph of a source file is a link that brings that
 * paragraph into view in the source pane. In the source, every {@code defrule} line starts with an anchor, and the
 * lines of the paragraphs the frame names (with their sub-rules, {@code p-plan/n} under {@code p-plan}) are marked.
 * Pure.
 */
public final class RawPanel {

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_/*.-]*");
    private static final Pattern LISP_FILE = Pattern.compile("[A-Za-z0-9_./-]+\\.lisp");
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

    /** The frame pane: links to its tag, top frame and integrated frames, then its text. The head names the first source file. */
    public String frame(String path, String text) {
        Library.Frame frame = library.frame(path);
        if (frame == null || text == null) return "<p class=\"hint\">No frame " + esc(path) + ".</p>";
        StringBuilder sb = new StringBuilder();
        List<String> files = sources.filesOf(path);
        sb.append("<div class=\"raw-head\"");
        if (!files.isEmpty()) sb.append(" data-first-source=\"").append(esc(files.get(0))).append("\"");
        sb.append("><b>").append(esc(path)).append("</b> ").append(editFrameLink(path));
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
        sb.append("</div><pre class=\"raw\">");
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

    /**
     * The integrated-frame tab's pane: the frames that went into it and its own sub-frames are links, wherever their
     * file names appear in the text ({@code ;;; integrates:} lines, {@code :include}, {@code :frame}).
     */
    public String integrated(String path, String text) {
        Library.Frame frame = library.frame(path);
        if (frame == null || text == null) return "<p class=\"hint\">No frame " + esc(path) + ".</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"raw-head\"><b>").append(esc(path)).append("</b> ").append(editFrameLink(path));
        if (!frame.isTop() && library.frame(frame.topPath()) != null) sb.append("<br>Part of: ").append(frameLink(frame.topPath()));
        sb.append("</div><pre class=\"raw\">");
        String dir = Html.dirOf(path);
        for (String line : text.split("\n", -1)) {
            Matcher m = LISP_FILE.matcher(line);
            int at = 0;
            while (m.find()) {
                String target = library.frame(m.group()) != null ? m.group() : library.frame(dir + m.group()) != null ? dir + m.group() : null;
                if (target == null) continue;
                sb.append(esc(line.substring(at, m.start())));
                sb.append("<a href=\"#\" class=\"frame-link\" data-frame=\"").append(esc(target)).append("\">").append(esc(m.group())).append("</a>");
                at = m.end();
            }
            sb.append(esc(line.substring(at))).append("\n");
        }
        sb.append("</pre>");
        return sb.toString();
    }

    /**
     * The source pane in its markdown view: the same head, then the markdown file beside the source file as it is.
     *
     * @param mdText the markdown text, or null when there is no markdown file beside the source file
     */
    public String markdown(String file, String mdText, String framePath) {
        if (sources.file(file) == null) return "<p class=\"hint\">No source " + esc(file) + ".</p>";
        StringBuilder sb = new StringBuilder();
        head(file, framePath, true, sb);
        if (mdText == null) sb.append("<p class=\"hint\">No markdown file beside this source file.</p>");
        else sb.append("<pre class=\"raw md\">").append(esc(mdText)).append("</pre>");
        return sb.toString();
    }

    /**
     * The head of the source pane: a drop-down list of the frame's source files with this one selected, the switch
     * between the Lisp and the markdown view, the html-saurus page, and the edit link of the file shown.
     */
    private void head(String file, String framePath, boolean markdown, StringBuilder sb) {
        List<String> files = new ArrayList<>(sources.filesOf(framePath));
        if (!files.contains(file)) files.add(0, file);
        sb.append("<div class=\"raw-head source-head\"><select class=\"raw-file-select\" title=\"").append(esc(file)).append("\">");
        for (String other : files) {
            sb.append("<option value=\"").append(esc(other)).append("\"").append(other.equals(file) ? " selected" : "").append(">")
                    .append(esc(other.substring(other.lastIndexOf('/') + 1))).append("</option>");
        }
        sb.append("</select> <span class=\"hint\">").append(files.size()).append(" source file").append(files.size() == 1 ? "" : "s")
                .append(" of this frame</span>");
        sb.append(" <span class=\"raw-views\"><a href=\"#\" class=\"raw-view").append(markdown ? "" : " active")
                .append("\" data-view=\"lisp\">Lisp</a><a href=\"#\" class=\"raw-view").append(markdown ? " active" : "")
                .append("\" data-view=\"md\">Markdown</a></span>");
        String page = sources.saurusPageOf(file);
        if (page != null) sb.append(" ").append(saurusLink(page, "html-saurus"));
        String shown = markdown ? Sources.markdownBeside(file) : file;
        sb.append(" ").append(editWorksLink(shown));
        sb.append("<div class=\"raw-path\">").append(esc(shown)).append("</div></div>");
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

    /**
     * The source pane: a drop-down list of the frame's source files with this one selected, then the file's text,
     * each defrule line anchored, the frame's paragraphs marked.
     */
    public String source(String file, String text, String framePath) {
        if (text == null || sources.file(file) == null) return "<p class=\"hint\">No source " + esc(file) + ".</p>";
        Set<String> named = new HashSet<>();
        RuleFile f = sources.file(file);
        for (RuleFile.Rule r : f.rules()) {
            for (Sources.Claim c : sources.claimsOf(file, r.name())) if (c.frame().equals(framePath)) named.add(r.name());
        }
        StringBuilder sb = new StringBuilder();
        head(file, framePath, false, sb);
        sb.append("<pre class=\"raw\">");
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
