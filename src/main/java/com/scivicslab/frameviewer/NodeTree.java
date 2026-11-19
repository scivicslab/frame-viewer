package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The left tree of a tab whose items come as {@link TreeNode}s (the workflows, the registry's is-a tree), drawn by the
 * server as nested links, as the Sections tab's table of contents is: only the branch down to the page's item is open,
 * and a closed branch carries the path its children are read from ({@code tree/...}). Also the page of a search's results
 * in that tab. Pure: it is given the nodes and how a node's page and children are reached.
 */
public final class NodeTree {

    /** One node as the page's script reads it when a closed branch is opened: its label, its page, its children's path. */
    public record Entry(String label, String path, boolean branch, String tree) {}

    private final Map<String, TreeNode> byId = new LinkedHashMap<>();
    private final Map<String, List<TreeNode>> under = new LinkedHashMap<>();
    private final Function<TreeNode, String> pageOf;
    private final Function<TreeNode, String> childrenOf;

    /**
     * @param nodes      the nodes, a root's parent being {@code #}
     * @param pageOf     a node's page, as a path from the top without the leading {@code /}
     * @param childrenOf where a node's children are read from ({@code tree/...}), or null when they are always drawn
     */
    public NodeTree(List<TreeNode> nodes, Function<TreeNode, String> pageOf, Function<TreeNode, String> childrenOf) {
        for (TreeNode n : nodes) {
            byId.put(n.id(), n);
            under.computeIfAbsent(n.parent(), k -> new ArrayList<>()).add(n);
        }
        this.pageOf = pageOf;
        this.childrenOf = childrenOf;
    }

    public boolean has(String id) {
        return byId.containsKey(id);
    }

    public TreeNode node(String id) {
        return byId.get(id);
    }

    /** The nodes right under one, for the page's script; null when there is no such node. */
    public List<Entry> children(String id) {
        if (!byId.containsKey(id)) return null;
        List<Entry> out = new ArrayList<>();
        for (TreeNode k : under.getOrDefault(id, List.of())) {
            boolean branch = under.containsKey(k.id());
            out.add(new Entry(k.text(), pageOf.apply(k), branch, branch && childrenOf != null ? childrenOf.apply(k) : null));
        }
        return out;
    }

    /**
     * The tree as HTML: every root and the nodes right under a root are drawn; below that, only the branch down to
     * {@code current} is open, and the nodes right under {@code current}. {@code current} is marked; it may be null.
     *
     * @param base the {@code ../} leading from the page back to the top
     */
    public String html(String current, String base) {
        List<String> open = new ArrayList<>();
        for (TreeNode n = current == null ? null : byId.get(current); n != null; n = byId.get(n.parent())) open.add(0, n.id());
        StringBuilder sb = new StringBuilder();
        level("#", open, current, base, true, sb);
        return sb.toString();
    }

    private void level(String parent, List<String> open, String current, String base, boolean top, StringBuilder sb) {
        sb.append("<ul class=\"toc\">");
        for (TreeNode n : under.getOrDefault(parent, List.of())) {
            boolean branch = under.containsKey(n.id());
            // a root is always open: its nodes are the tab's first level
            boolean expanded = branch && (top || open.contains(n.id()));
            boolean lazy = branch && childrenOf != null;
            sb.append("<li class=\"").append(branch ? (expanded ? "branch open" : "branch") : "leaf").append("\">");
            // a closed branch says where its children are read from; an open one has them drawn already
            if (lazy && !expanded) sb.append("<span class=\"twisty\" data-tree=\"").append(Html.esc(childrenOf.apply(n))).append("\"></span>");
            else if (branch) sb.append("<span class=\"twisty\"></span>");
            String title = n.li_attr() == null ? null : n.li_attr().get("title");
            sb.append("<a href=\"").append(Html.esc(base + pageOf.apply(n))).append("\"")
              .append(n.id().equals(current) ? " class=\"current\"" : "")
              .append(title == null || title.isEmpty() ? "" : " title=\"" + Html.esc(title) + "\"").append(">")
              .append(Html.esc(n.text())).append("</a>");
            // a closed branch whose children are read on demand is drawn without them; one that is always drawn, with them
            if (expanded || (branch && childrenOf == null)) level(n.id(), open, current, base, false, sb);
            sb.append("</li>");
        }
        sb.append("</ul>");
    }

    /** The page of a search's results: each node that holds every word, a link to its page, with where it matched. */
    public String searchBody(String title, String q, List<Search.Hit> hits, String base) {
        StringBuilder sb = new StringBuilder("<h2>").append(Html.esc(title)).append(": ").append(Html.esc(q)).append("</h2>");
        sb.append("<p class=\"hint\">").append(hits.isEmpty() ? "none found" : hits.size() + " found").append("</p><ul class=\"search-hits\">");
        for (Search.Hit h : hits) {
            TreeNode n = byId.get(h.id());
            if (n == null) continue;
            sb.append("<li><a href=\"").append(Html.esc(base + pageOf.apply(n))).append("\">").append(Html.esc(n.text()))
              .append("</a> <span class=\"hint\">in ").append(Html.esc(h.where())).append("</span></li>");
        }
        return sb.append("</ul>").toString();
    }
}
