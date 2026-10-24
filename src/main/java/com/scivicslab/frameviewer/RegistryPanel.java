package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.scivicslab.turingworkflow.plugins.rstframe.BookStore;
import com.scivicslab.turingworkflow.plugins.rstframe.RuleFile;

/**
 * The Integrated frames tab: the registry of generalized frames. The tree is the is-a tree of the registry, each
 * generalized frame under the one it is a kind of. A generalized frame shows its id, the frame above it and the frames
 * under it, its relations to other frames besides is-a (part-of, feature, operation, other, as the sections said them),
 * the concrete frames put into it, its frame (the questions an explanation answers, each with the rst-on-lisp rules that
 * answer it) and its frame material, the rules gathered for each of its concrete frames.
 */
public final class RegistryPanel {

    private final List<BookStore.RegistryFrame> frames;
    private final Map<String, BookStore.RegistryFrame> byId = new LinkedHashMap<>();
    private final Map<String, List<BookStore.FrameItem>> items;
    private final List<BookStore.FrameRelation> relations;
    private final BiFunction<String, String, BookStore.FrameConcept> conceptOf;
    private final BiFunction<String, String, String> rulesOf;

    /**
     * @param conceptOf the material of a concrete frame, by book and "number TAB concept"
     * @param rulesOf   the rule file of a section, by book and number; {@code null} when it has none
     */
    public RegistryPanel(List<BookStore.RegistryFrame> frames, Map<String, List<BookStore.FrameItem>> items, List<BookStore.FrameRelation> relations,
            BiFunction<String, String, BookStore.FrameConcept> conceptOf, BiFunction<String, String, String> rulesOf) {
        this.frames = frames;
        for (BookStore.RegistryFrame f : frames) byId.put(f.id(), f);
        this.items = items;
        this.relations = relations;
        this.conceptOf = conceptOf;
        this.rulesOf = rulesOf;
    }

    /** @return the is-a tree of the registry, each frame under its parent; a frame whose parent is missing is at the top */
    public List<TreeNode> tree() {
        List<TreeNode> out = new ArrayList<>();
        String root = "reg";
        out.add(new TreeNode(root, "#", "generalized frames (" + frames.size() + ")", Map.of()));
        for (BookStore.RegistryFrame f : frames) {
            String parent = f.parent() != null && byId.containsKey(f.parent()) ? "r:" + f.parent() : root;
            out.add(new TreeNode("r:" + f.id(), parent, f.id() + " " + f.name() + "  · " + f.members().size() + " concrete" + itemCount(f.id()),
                    Map.of("data-frame-id", f.id())));
        }
        return out;
    }

    private String itemCount(String id) {
        List<BookStore.FrameItem> its = items.get(id);
        return its == null ? "" : " · " + its.size() + " items";
    }

    private String link(String id) {
        BookStore.RegistryFrame f = byId.get(id);
        String text = f == null ? id : id + " " + f.name();
        return "<a href=\"#\" class=\"frame-id-link\" data-frame-id=\"" + Html.esc(id) + "\">" + Html.esc(text) + "</a>";
    }

    /** @return the panel of one generalized frame */
    public String frame(String id) {
        BookStore.RegistryFrame f = byId.get(id);
        if (f == null) return "<p class=\"hint\">No generalized frame " + Html.esc(id) + ".</p>";
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(Html.esc(f.name())).append("</h2>");
        sb.append("<div class=\"raw-path\">a generalized frame · id ").append(Html.esc(f.id())).append("</div>");

        sb.append("<h3>In the is-a tree</h3><ul class=\"relation-children\">");
        sb.append("<li>a kind of: ").append(f.parent() == null ? "<span class=\"hint\">(the top)</span>" : link(f.parent())).append("</li>");
        List<String> kids = frames.stream().filter(x -> f.id().equals(x.parent())).map(x -> link(x.id())).toList();
        sb.append("<li>its kinds: ").append(kids.isEmpty() ? "<span class=\"hint\">(none)</span>" : String.join(" · ", kids)).append("</li></ul>");

        // the network beside the tree: what the sections said of this frame's concepts and other frames' concepts
        Map<String, List<String>> out = new LinkedHashMap<>(), in = new LinkedHashMap<>();
        for (BookStore.FrameRelation r : relations) {
            if (r.from().equals(f.id())) out.computeIfAbsent(r.kind(), k -> new ArrayList<>()).add(r.to());
            if (r.to().equals(f.id())) in.computeIfAbsent(r.kind(), k -> new ArrayList<>()).add(r.from());
        }
        if (!out.isEmpty() || !in.isEmpty()) {
            sb.append("<h3>Its relations to other frames</h3><ul class=\"relation-children\">");
            for (var e : out.entrySet()) sb.append("<li>").append(Html.esc(f.name())).append(" <b>").append(Html.esc(e.getKey())).append("</b> ")
                    .append(String.join(" · ", e.getValue().stream().distinct().map(this::link).toList())).append("</li>");
            for (var e : in.entrySet()) sb.append("<li>").append(String.join(" · ", e.getValue().stream().distinct().map(this::link).toList()))
                    .append(" <b>").append(Html.esc(e.getKey())).append("</b> ").append(Html.esc(f.name())).append("</li>");
            sb.append("</ul>");
        }

        sb.append("<h3>Its concrete frames</h3><div class=\"frame-folded\">");
        List<String> links = new ArrayList<>();
        for (BookStore.RegistryMember m : f.members()) {
            links.add("<a href=\"#\" class=\"section-link\" data-book=\"" + Html.esc(m.book()) + "\" data-section=\"" + Html.esc(m.number()) + "\">"
                    + Html.esc(m.number()) + "</a> " + Html.esc(m.concept()));
        }
        sb.append(String.join(" · ", links)).append("</div>");

        sb.append("<h3>Its frame</h3>");
        List<BookStore.FrameItem> its = items.get(f.id());
        if (its == null || its.isEmpty()) {
            sb.append("<p class=\"hint\">(no items yet)</p>");
        } else {
            sb.append("<table class=\"concepts frame-items\"><tr><th>the question it answers</th><th>its rst-on-lisp rules</th></tr>");
            for (BookStore.FrameItem it : its) {
                sb.append("<tr><td class=\"concept\">").append(Html.esc(it.question())).append("</td><td class=\"rules\">");
                for (String ref : it.rules()) sb.append(rule(ref));
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }

        sb.append("<h3>Its frame material</h3>");
        for (BookStore.RegistryMember m : f.members()) {
            BookStore.FrameConcept c = conceptOf.apply(m.book(), m.number() + "\t" + m.concept());
            if (c == null || c.rules().isEmpty()) continue;
            sb.append("<div class=\"frame-kind\">").append(Html.esc(m.concept())).append(" · ").append(Html.esc(m.number())).append("</div><ul class=\"frame-rules\">");
            for (BookStore.FrameRule r : c.rules()) sb.append("<li>").append(rule(m.book() + "|" + m.number() + ":" + r.rule())).append("</li>");
            sb.append("</ul>");
        }
        return sb.toString();
    }

    /** @return one rule, named "book|number:rule", as its section's rule file writes it, with the section it comes from */
    private String rule(String ref) {
        int bar = ref.indexOf('|'), colon = ref.indexOf(':', bar + 1);
        if (bar < 0 || colon < 0) return "<pre class=\"rule-lisp\">" + Html.esc(ref) + "</pre>";
        String book = ref.substring(0, bar), number = ref.substring(bar + 1, colon), name = ref.substring(colon + 1);
        String file = rulesOf.apply(book, number);
        String form = file == null ? null : RuleFile.formOf(file, name);
        return "<pre class=\"rule-lisp\"><span class=\"hint\">" + Html.esc(number) + "</span> " + Html.esc(form != null ? form : name) + "</pre>";
    }
}
