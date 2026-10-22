package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The workflows tab ({@code FrameOntology_261021_oo01}, Solution step 0): the Turing Workflow YAML files that make
 * frames from books, in the order they run, each read as what it is for, what it takes, and its state transitions,
 * with the file as it is below. Pure: it is given the files' texts.
 */
public final class WorkflowPanel {

    private final List<String> names;
    private final Map<String, String> texts;
    private final String dirUnderWorks;

    /**
     * @param names         the workflows' names, in the order they run
     * @param texts         each workflow's YAML text by name; a missing one is shown as missing
     * @param dirUnderWorks the workflows' directory under the works directory, for the edit links, or null
     */
    public WorkflowPanel(List<String> names, Map<String, String> texts, String dirUnderWorks) {
        this.names = names;
        this.texts = texts;
        this.dirUnderWorks = dirUnderWorks;
    }

    /** One node per workflow, numbered in the order they run. */
    public List<Library.TreeNode> tree() {
        List<Library.TreeNode> out = new ArrayList<>();
        int n = 0;
        for (String name : names) {
            n++;
            String text = texts.get(name);
            String title = n + ". " + name + (text == null ? " (missing)" : "");
            out.add(new Library.TreeNode("w:" + name, "#", title, Map.of("data-workflow", name, "title", firstLine(description(text)))));
        }
        return out;
    }

    /** The panel of one workflow. */
    public String render(String name) {
        String text = texts.get(name);
        if (text == null) return "<p class=\"hint\">No workflow " + Html.esc(name) + "</p>";
        Map<String, Object> y = parse(text);
        StringBuilder sb = new StringBuilder();
        sb.append("<h2 class=\"name\">").append(Html.esc(String.valueOf(y.getOrDefault("name", name)))).append("</h2>");
        if (dirUnderWorks != null) {
            sb.append("<div class=\"raw-path\">").append(Html.esc(dirUnderWorks + "/" + name + ".yaml")).append(' ')
              .append(Html.editWorksLink(dirUnderWorks + "/" + name + ".yaml")).append("</div>");
        }
        sb.append("<p class=\"wf-description\">").append(Html.esc(description(text))).append("</p>");

        if (y.get("params") instanceof Map<?, ?> params && !params.isEmpty()) {
            sb.append("<h3>What it takes</h3><table class=\"wf-table\"><tr><th>param</th><th>what it is</th><th>default</th></tr>");
            for (Map.Entry<?, ?> e : params.entrySet()) {
                Map<?, ?> p = e.getValue() instanceof Map<?, ?> m ? m : Map.of();
                Object d = p.get("description") != null ? p.get("description") : p.get("label");
                sb.append("<tr><td class=\"name\">").append(Html.esc(String.valueOf(e.getKey()))).append("</td><td>")
                  .append(Html.esc(d == null ? "" : String.valueOf(d))).append("</td><td class=\"name\">")
                  .append(p.containsKey("default") ? Html.esc(String.valueOf(p.get("default"))) : (Boolean.TRUE.equals(p.get("required")) ? "(required)" : ""))
                  .append("</td></tr>");
            }
            sb.append("</table>");
        }

        if (y.get("steps") instanceof List<?> steps) {
            sb.append("<h3>Its state transitions</h3><p class=\"hint\">From each state, the first transition whose actions all "
                    + "succeed is taken; the others from the same state are tried in this order.</p>");
            sb.append("<table class=\"wf-table\"><tr><th>from</th><th>to</th><th>transition</th><th>actions</th></tr>");
            for (Object o : steps) {
                if (!(o instanceof Map<?, ?> step)) continue;
                List<?> states = step.get("states") instanceof List<?> l ? l : List.of();
                sb.append("<tr><td class=\"name\">").append(Html.esc(states.isEmpty() ? "" : String.valueOf(states.get(0))))
                  .append("</td><td class=\"name\">").append(Html.esc(states.size() < 2 ? "" : String.valueOf(states.get(1))))
                  .append("</td><td><span class=\"name\">").append(Html.esc(step.get("label") == null ? "" : String.valueOf(step.get("label")))).append("</span>");
                if (step.get("note") != null) sb.append("<div class=\"hint\">").append(Html.esc(String.valueOf(step.get("note")))).append("</div>");
                sb.append("</td><td>");
                if (step.get("actions") instanceof List<?> actions) {
                    for (Object ao : actions) {
                        if (!(ao instanceof Map<?, ?> a)) continue;
                        sb.append("<div class=\"name\">").append(Html.esc(String.valueOf(a.get("actor")))).append('.')
                          .append(Html.esc(String.valueOf(a.get("method")))).append("(").append(Html.esc(arguments(a.get("arguments")))).append(")</div>");
                    }
                }
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }

        sb.append("<h3>As written</h3><pre class=\"raw\">").append(Html.esc(text)).append("</pre>");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String text) {
        Object o = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    private static String description(String text) {
        if (text == null) return "";
        Object d = parse(text).get("description");
        return d == null ? "" : String.valueOf(d).strip();
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    /** An action's arguments in one line: a list as its items, a map as key: value, a string as it is. */
    static String arguments(Object a) {
        if (a == null) return "";
        if (a instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object x : l) out.add(x instanceof String s ? "\"" + s + "\"" : String.valueOf(x));
            return String.join(", ", out);
        }
        if (a instanceof Map<?, ?> m) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.add(e.getKey() + ": " + e.getValue());
            return String.join(", ", out);
        }
        return "\"" + a + "\"";
    }
}
