package com.scivicslab.frameviewer;

import static com.scivicslab.frameviewer.FramePanel.esc;
import static com.scivicslab.frameviewer.FramePanel.frameLink;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes the right-hand panel for one source file: its sections and paragraphs in order, each marked with the
 * frames that name it and on which header line ({@code made-from}, {@code members}, {@code elsewhere},
 * {@code left-out}). A paragraph no frame names is either covered (a frame names a section around it) or
 * unclaimed (no frame names it or any section around it). Pure.
 */
public final class SourcePanel {

    private static final int MAX_DEPTH = 40;

    private final Sources sources;

    public SourcePanel(Sources sources) {
        this.sources = sources;
    }

    public String render(String file) {
        RuleFile f = sources.file(file);
        if (f == null) return "<p class=\"hint\">No source " + esc(file) + ".</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"source-file\"><h2>").append(esc(file)).append("</h2>");
        if (!f.rules().isEmpty()) walk(file, f, f.rules().get(0).name(), false, new HashSet<>(), 0, sb);
        sb.append("</div>");
        return sb.toString();
    }

    /**
     * A rule with a direct string or a relation keyword is a paragraph and is shown whole. Any other rule is a
     * container ({@code document}, {@code sections}, a section with {@code :heading}): its heading is shown and the
     * rules it names are walked in order.
     */
    private void walk(String file, RuleFile f, String name, boolean covered, Set<String> seen, int depth, StringBuilder sb) {
        RuleFile.Rule r = f.rule(name);
        if (r == null || !seen.add(name) || depth > MAX_DEPTH) return;
        boolean named = !sources.claimsOf(file, name).isEmpty();
        if (isParagraph(r)) {
            sb.append("<div class=\"para").append(named ? "" : covered ? " covered" : " unclaimed").append("\" id=\"r-").append(esc(name)).append("\"><span class=\"name\">").append(esc(name)).append("</span>");
            marks(file, name, sb);
            sb.append("<div class=\"text\">").append(esc(String.join(" ", f.textOf(name)))).append("</div></div>");
            return;
        }
        String heading = r.attr(":heading");
        if (heading != null || named) {
            sb.append("<div class=\"section-head\" id=\"r-").append(esc(name)).append("\">");
            if (heading != null) sb.append("<b>").append(esc(heading.replaceFirst("^#+\\s*", ""))).append("</b> ");
            sb.append("<span class=\"name\">").append(esc(name)).append("</span>");
            marks(file, name, sb);
            sb.append("</div>");
        }
        List<RuleFile.Token> items = r.items();
        for (int i = 0; i < items.size(); i++) {
            RuleFile.Token t = items.get(i);
            if (t.kind() == RuleFile.Token.Kind.KEYWORD) {
                if (!RuleFile.isRelationKeyword(t.text())) i++;
                continue;
            }
            if (t.kind() == RuleFile.Token.Kind.SYMBOL) walk(file, f, t.text(), covered || named, seen, depth + 1, sb);
        }
    }

    private static boolean isParagraph(RuleFile.Rule r) {
        List<RuleFile.Token> items = r.items();
        for (int i = 0; i < items.size(); i++) {
            RuleFile.Token t = items.get(i);
            if (t.kind() == RuleFile.Token.Kind.STRING) return true;
            if (t.kind() == RuleFile.Token.Kind.KEYWORD) {
                if (RuleFile.isRelationKeyword(t.text())) return true;
                i++;
            }
        }
        return false;
    }

    private void marks(String file, String rule, StringBuilder sb) {
        for (Sources.Claim c : sources.claimsOf(file, rule)) {
            sb.append(" <span class=\"claim claim-").append(esc(c.kind())).append("\">").append(esc(c.kind())).append(": ")
                    .append(frameLink(c.frame())).append("</span>");
        }
    }
}
