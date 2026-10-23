package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.scivicslab.turingworkflow.plugins.rstframe.BookStore;
import com.scivicslab.turingworkflow.plugins.rstframe.GeneralFrames;
import com.scivicslab.turingworkflow.plugins.rstframe.RuleFile;

/**
 * The Integrated frames tab: the concepts of a book recognized across its sections, and the generalized concepts above them.
 * A concept shows the sections it appears in, its items (the questions an explanation of it answers, each with the
 * rst-on-lisp rules that answer it, from whichever section), and its frame material, the rules gathered for it in every
 * section, put together. A generalized concept shows its kinds as well, and its material is theirs and its own.
 */
public final class EntityPanel {

    private final String book;
    private final List<BookStore.Entity> entities;
    private final Map<String, List<BookStore.FrameItem>> items;
    private final Map<String, List<BookStore.FrameItem>> sectionItems;
    private final Map<String, BookStore.FrameConcept> conceptOf;
    private final Function<String, String> rulesOf;

    /**
     * @param items        the items of each concept of the book, by id
     * @param sectionItems the items of each concept of a section, by "number TAB concept"
     * @param conceptOf    the material of each concept of a section, by "number TAB concept"
     * @param rulesOf      the rule file of a section, by its number; {@code null} when it has none
     */
    public EntityPanel(String book, List<BookStore.Entity> entities, Map<String, List<BookStore.FrameItem>> items,
            Map<String, List<BookStore.FrameItem>> sectionItems, Map<String, BookStore.FrameConcept> conceptOf, Function<String, String> rulesOf) {
        this.book = book;
        this.entities = entities;
        this.items = items;
        this.sectionItems = sectionItems;
        this.conceptOf = conceptOf;
        this.rulesOf = rulesOf;
    }

    /**
     * @return the generalized concept tree: each generalized concept with its kinds under it, a kind that is itself
     *         generalized with its own kinds under it, from the top down; then the concepts of the book that the tree
     *         does not reach, of several sections and of one
     */
    public List<TreeNode> tree() {
        List<TreeNode> out = new ArrayList<>();
        Map<String, BookStore.Entity> byId = new LinkedHashMap<>();
        for (BookStore.Entity e : entities) byId.put(e.id(), e);
        // a kind is itself generalized when a generalized concept has its name
        Map<String, BookStore.Entity> generalOf = new LinkedHashMap<>();
        for (BookStore.Entity e : entities) {
            if (e.level().equals(BookStore.Entity.GENERAL)) generalOf.put(GeneralFrames.norm(e.name()), e);
        }
        java.util.Set<String> under = new java.util.HashSet<>();
        for (BookStore.Entity g : generalOf.values()) {
            for (String c : g.children()) {
                BookStore.Entity k = byId.get(c);
                BookStore.Entity kg = k == null ? null : generalOf.get(GeneralFrames.norm(k.name()));
                if (kg != null && kg != g) under.add(kg.id());
            }
        }
        String g = "eg:" + book;
        out.add(new TreeNode(g, "#", book + ": generalized concept tree (" + generalOf.size() + " generalized concepts)", Map.of()));
        java.util.Set<String> reached = new java.util.HashSet<>();
        for (BookStore.Entity e : generalOf.values()) {
            if (!under.contains(e.id())) addGeneral(e, g, byId, generalOf, new java.util.HashSet<>(), reached, out);
        }
        // a cycle leaves generalized concepts no root reaches; they are roots too
        for (BookStore.Entity e : generalOf.values()) {
            if (!reached.contains(e.id())) addGeneral(e, g, byId, generalOf, new java.util.HashSet<>(), reached, out);
        }
        List<BookStore.Entity> several = entities.stream().filter(e -> e.level().equals(BookStore.Entity.CONCEPT) && !reached.contains(e.id())
                && e.members().size() > 1).toList();
        List<BookStore.Entity> one = entities.stream().filter(e -> e.level().equals(BookStore.Entity.CONCEPT) && !reached.contains(e.id())
                && e.members().size() <= 1).toList();
        String s = "es:" + book, o = "eo:" + book;
        out.add(new TreeNode(s, "#", book + ": other concepts in several sections (" + several.size() + ")", Map.of()));
        for (BookStore.Entity e : several) out.add(node("e:" + book + "|" + e.id(), s, e, e.name() + "  · " + e.members().size() + " sections" + itemCount(e)));
        out.add(new TreeNode(o, "#", book + ": other concepts in one section (" + one.size() + ")", Map.of()));
        for (BookStore.Entity e : one) out.add(node("e:" + book + "|" + e.id(), o, e, e.name() + "  · " + e.members().get(0).number()));
        return out;
    }

    /** Adds a generalized concept under {@code parent}, and its kinds under it, a generalized kind with its own kinds. */
    private void addGeneral(BookStore.Entity e, String parent, Map<String, BookStore.Entity> byId, Map<String, BookStore.Entity> generalOf,
            java.util.Set<String> path, java.util.Set<String> reached, List<TreeNode> out) {
        String id = parent + "|" + e.id();
        out.add(node(id, parent, e, e.name() + "  · " + e.children().size() + " kinds" + itemCount(e)));
        reached.add(e.id());
        // the concept of the book the generalized concept stands for is in the tree with it
        for (BookStore.Entity c : entities) {
            if (c.level().equals(BookStore.Entity.CONCEPT) && GeneralFrames.norm(c.name()).equals(GeneralFrames.norm(e.name()))) reached.add(c.id());
        }
        path.add(e.id());
        for (String c : e.children()) {
            BookStore.Entity k = byId.get(c);
            if (k == null) continue;
            reached.add(k.id());
            BookStore.Entity kg = generalOf.get(GeneralFrames.norm(k.name()));
            if (kg != null && kg != e && !path.contains(kg.id())) {
                addGeneral(kg, id, byId, generalOf, path, reached, out);
            } else {
                out.add(node(id + "|" + k.id(), id, k, k.name() + "  · " + k.members().size() + " section(s)" + itemCount(k)));
            }
        }
        path.remove(e.id());
    }

    private TreeNode node(String id, String parent, BookStore.Entity e, String text) {
        return new TreeNode(id, parent, text, Map.of("data-book", book, "data-entity", e.id()));
    }

    private String itemCount(BookStore.Entity e) {
        List<BookStore.FrameItem> its = items.get(e.id());
        return its == null ? "" : " · " + its.size() + " items";
    }

    /** @return the panel of one concept of the book */
    public String entity(String id) {
        BookStore.Entity e = entities.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        if (e == null) return "<p class=\"hint\">No concept " + Html.esc(id) + " in " + Html.esc(book) + ".</p>";
        StringBuilder sb = new StringBuilder();
        boolean general = e.level().equals(BookStore.Entity.GENERAL);
        sb.append("<h2>").append(Html.esc(e.name())).append("</h2>");
        sb.append("<div class=\"raw-path\">").append(Html.esc(book)).append(" · ").append(general ? "a generalized concept" : "a concept of the book")
          .append(" · id ").append(Html.esc(e.id())).append("</div>");
        List<BookStore.Occurrence> all = new ArrayList<>(e.members());
        if (general) {
            sb.append("<h3>Its kinds</h3><ul class=\"relation-children\">");
            for (String c : e.children()) {
                BookStore.Entity k = entities.stream().filter(x -> x.id().equals(c)).findFirst().orElse(null);
                if (k == null) continue;
                sb.append("<li><a href=\"#\" class=\"entity-link\" data-book=\"").append(Html.esc(book)).append("\" data-entity=\"")
                  .append(Html.esc(k.id())).append("\">").append(Html.esc(k.name())).append("</a> <span class=\"hint\">(")
                  .append(k.members().size()).append(" section(s))</span></li>");
                all.addAll(k.members());
            }
            sb.append("</ul>");
        }
        sb.append("<h3>The sections it appears in</h3><div class=\"frame-folded\">");
        List<String> links = new ArrayList<>();
        for (BookStore.Occurrence o : all) {
            links.add("<a href=\"#\" class=\"section-link\" data-book=\"" + Html.esc(book) + "\" data-section=\"" + Html.esc(o.number()) + "\">"
                    + Html.esc(o.number()) + "</a> " + Html.esc(o.concept()));
        }
        sb.append(String.join(" · ", links)).append("</div>");

        // the items: of the concept of the book when it was asked over its material put together, else of its one section
        List<BookStore.FrameItem> its = items.get(e.id());
        if (its == null && e.members().size() == 1) {
            BookStore.Occurrence o = e.members().get(0);
            its = sectionItems.getOrDefault(o.number() + "\t" + o.concept(), List.of()).stream()
                    .map(it -> new BookStore.FrameItem(it.question(), it.rules().stream().map(r -> o.number() + ":" + r).toList())).toList();
        }
        sb.append("<h3>").append(general ? "The generalized frame" : "Its frame").append("</h3>");
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
        for (BookStore.Occurrence o : all) {
            BookStore.FrameConcept c = conceptOf.get(o.number() + "\t" + o.concept());
            if (c == null || c.rules().isEmpty()) continue;
            sb.append("<div class=\"frame-kind\">").append(Html.esc(o.concept())).append(" · ").append(Html.esc(o.number())).append("</div><ul class=\"frame-rules\">");
            for (BookStore.FrameRule r : c.rules()) sb.append("<li>").append(rule(o.number() + ":" + r.rule())).append("</li>");
            sb.append("</ul>");
        }
        return sb.toString();
    }

    /** @return one rule, named "number:rule", as its section's rule file writes it, with the section it comes from */
    private String rule(String ref) {
        int colon = ref.indexOf(':');
        if (colon < 0) return "<pre class=\"rule-lisp\">" + Html.esc(ref) + "</pre>";
        String number = ref.substring(0, colon), name = ref.substring(colon + 1);
        String file = rulesOf.apply(number);
        String form = file == null ? null : RuleFile.formOf(file, name);
        return "<pre class=\"rule-lisp\"><span class=\"hint\">" + Html.esc(number) + "</span> " + Html.esc(form != null ? form : name) + "</pre>";
    }
}
