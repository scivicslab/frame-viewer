package com.scivicslab.frameviewer;

import static com.scivicslab.frameviewer.FramePanel.esc;
import static com.scivicslab.frameviewer.FramePanel.frameLink;
import static com.scivicslab.frameviewer.FramePanel.sourceLink;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes the right-hand panel for one subject tag: the concrete frames that name it, the integrated frames those
 * frames went into, and the source files they were made from. Pure.
 */
public final class TagPanel {

    private final Library library;
    private final Sources sources;

    public TagPanel(Library library, Sources sources) {
        this.library = library;
        this.sources = sources;
    }

    /** @param tag {@code book|number}, as the tree's {@code data-tag} carries it */
    public String render(String tag) {
        int bar = tag.indexOf('|');
        String book = bar < 0 ? tag : tag.substring(0, bar);
        String number = bar < 0 ? "" : tag.substring(bar + 1);
        Library.Subject node = null;
        for (Library.Subject s : library.books().getOrDefault(book, List.of())) if (s.number().equals(number)) node = s;
        if (node == null) return "<p class=\"hint\">No tag " + esc(tag) + ".</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"tag\"><h2>").append(esc(book + " " + number + " " + node.title())).append("</h2>");
        List<Library.Frame> frames = library.framesOf(book, number);
        if (frames.isEmpty()) {
            sb.append("<p class=\"hint\">No frame names this tag.</p></div>");
            return sb.toString();
        }
        List<String> concrete = new ArrayList<>();
        List<String> integrated = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (Library.Frame f : frames) {
            concrete.add(f.path());
            for (String p : List.of(f.path(), f.topPath())) {
                for (Library.Frame i : library.integratedInto(p)) if (!integrated.contains(i.path())) integrated.add(i.path());
            }
            for (String file : sources.filesOf(f.path())) if (!files.contains(file)) files.add(file);
        }
        list("Concrete frames", concrete, sb);
        list("Integrated into", integrated, sb);
        if (!files.isEmpty()) {
            sb.append("<h3>Source</h3><ul>");
            for (String file : files) sb.append("<li>").append(sourceLink(file, null, file)).append("</li>");
            sb.append("</ul>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static void list(String label, List<String> paths, StringBuilder sb) {
        if (paths.isEmpty()) return;
        sb.append("<h3>").append(label).append("</h3><ul>");
        for (String p : paths) sb.append("<li>").append(frameLink(p)).append("</li>");
        sb.append("</ul>");
    }
}
