package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.scivicslab.turingworkflow.plugins.rstframe.ConceptClasses;
import com.scivicslab.turingworkflow.plugins.rstframe.ConceptTree;

/**
 * The class tab ({@code ConceptClasses_261022_oo01}): the concept classes of {@code concepts/}, and for each file of
 * assignments in {@code assignments/} (step 3's result) the same classes with the ones that file added or grew. What a
 * class inherits is computed by CLOS in ABCL, through {@link ConceptClasses}.
 *
 * <p>A set is {@code ""} for the classes of {@code concepts/} alone, or the path of one file of assignments, read on
 * top of them.</p>
 */
public final class ClassPanel {

    /** Where one concept's paragraphs went: its class, and for each slot ({@code :aside} too) the paragraphs. */
    record Assignment(String concept, String cls, Map<String, List<String>> slots) {
    }

    private static final Pattern DEFCLASS = Pattern.compile("(?m)^\\(defclass\\s+(\\S+)");
    private static final Pattern ASSIGN = Pattern.compile("^\\(assign \"((?:[^\"\\\\]|\\\\.)*)\" (\\S+?)\\)?$");
    private static final Pattern SLOT = Pattern.compile("^\\s+\\((\\S+)((?: \"(?:[^\"\\\\]|\\\\.)*\")*)\\)\\)?$");
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern BOOK_FILE = Pattern.compile("(?m)^;;; book-file: (.+)$");

    private final Map<String, String> library;
    private final Map<String, String> results;
    private final String worksDir;
    private final Function<String, String> readWorks;
    private final String rstUnderWorks;

    /**
     * @param library   the class files of {@code concepts/}, path under {@code rst-on-lisp} to text
     * @param results   the files of assignments, path under {@code rst-on-lisp} to text
     * @param worksDir  the works directory, absolute, to turn a book file's absolute path into one under it
     * @param readWorks reads a file under the works directory, or gives null
     * @param rstUnderWorks the {@code rst-on-lisp} directory under the works directory ({@code rst-on-lisp}), or null
     *                  when it is elsewhere and its files get no edit link
     */
    public ClassPanel(Map<String, String> library, Map<String, String> results, String worksDir, Function<String, String> readWorks,
            String rstUnderWorks) {
        this.rstUnderWorks = rstUnderWorks;
        this.library = library;
        this.results = results;
        this.worksDir = worksDir.endsWith("/") ? worksDir : worksDir + "/";
        this.readWorks = readWorks;
    }

    /** One root for {@code concepts/}, one for each file of assignments; under each, the classes by superclass. */
    public List<Library.TreeNode> tree() {
        List<Library.TreeNode> out = new ArrayList<>();
        addSet(out, "", "concepts/");
        for (String r : results.keySet()) addSet(out, r, r + (standalone(r) ? " (made smaller)" : " (step 3)"));
        return out;
    }

    private void addSet(List<Library.TreeNode> out, String set, String title) {
        String root = "set:" + set;
        out.add(new Library.TreeNode(root, "#", title, Map.of()));
        Set<String> added = added(set);
        Set<String> grown = grown(set);
        try (ConceptClasses c = classes(set)) {
            for (ConceptClasses.Kind k : c.all()) {
                List<String> supers = c.superclasses(k.name());
                String parent = supers.isEmpty() ? root : "c:" + set + "|" + supers.get(0);
                String mark = added.contains(k.name()) ? "+ " : grown.contains(k.name()) ? "± " : "";
                out.add(new Library.TreeNode("c:" + set + "|" + k.name(), parent, mark + k.name(),
                        Map.of("data-class", k.name(), "data-set", set, "title", k.documentation())));
            }
        }
    }

    /** The panel of one class in one set. */
    public String render(String set, String cls) {
        if (!set.isEmpty() && !results.containsKey(set)) return "<p class=\"hint\">No such file of assignments: " + Html.esc(set) + "</p>";
        rendering = set;
        try (ConceptClasses c = classes(set)) {
            if (!c.names().contains(cls)) return "<p class=\"hint\">No class " + Html.esc(cls) + "</p>";
            StringBuilder sb = new StringBuilder();
            List<ConceptClasses.Kind> chain = c.chain(cls);
            String file = set.isEmpty() || !defines(set, cls) ? c.fileOf(cls) : set;
            sb.append("<h2 class=\"name\">").append(Html.esc(cls)).append("</h2>");
            sb.append("<div class=\"raw-path\">").append(Html.esc(file == null ? "" : file)).append(' ')
              .append((file == null || rstUnderWorks == null ? "" : Html.editWorksLink(rstUnderWorks + "/" + file))).append(set.isEmpty() ? "" : " · viewed with " + Html.esc(set)).append("</div>");
            if (added(set).contains(cls)) sb.append("<p class=\"hint\">Added while assigning: a proposal.</p>");
            else if (grown(set).contains(cls)) sb.append("<p class=\"hint\">Slots were added to it while assigning: a proposal.</p>");
            sb.append("<p>").append(Html.esc(chain.get(0).documentation())).append("</p>");

            sb.append("<h3>Kind of</h3><div class=\"child\">");
            if (chain.size() == 1) sb.append("<span class=\"hint\">(a root class)</span>");
            for (int i = 1; i < chain.size(); i++) sb.append(i > 1 ? " → " : "").append(classLink(set, chain.get(i).name()));
            sb.append("</div>");
            List<String> values = c.instances().entrySet().stream().filter(e -> e.getValue().equals(cls)).map(Map.Entry::getKey).toList();
            if (!values.isEmpty()) {
                sb.append("<h3>Values of it</h3><div class=\"child\">");
                for (int i = 0; i < values.size(); i++) sb.append(i > 0 ? ", " : "").append(Html.esc(values.get(i)));
                sb.append("</div>");
            }
            List<ConceptClasses.Kind> subs = c.subclasses(cls);
            if (!subs.isEmpty()) {
                sb.append("<h3>Kinds of it</h3><div class=\"child\">");
                for (int i = 0; i < subs.size(); i++) sb.append(i > 0 ? ", " : "").append(classLink(set, subs.get(i).name()));
                sb.append("</div>");
            }

            Map<String, List<String[]>> paragraphs = assigned(set, cls, c);
            sb.append("<h3>What can be said of it</h3>");
            for (ConceptClasses.Slot s : c.whatCanBeSaid(cls)) {
                sb.append("<div class=\"para\"><span class=\"name\">").append(Html.esc(s.name())).append("</span>");
                if (!s.from().equals(cls)) sb.append(" <span class=\"relation\">from ").append(classLink(set, s.from())).append("</span>");
                sb.append("<div class=\"instruction\">").append(Html.esc(s.question())).append("</div>");
                appendParagraphs(sb, paragraphs.remove(s.name()));
                sb.append("</div>");
            }
            for (Map.Entry<String, List<String[]>> e : paragraphs.entrySet()) {
                sb.append("<div class=\"para\"><span class=\"name\">").append(Html.esc(e.getKey())).append("</span>");
                if (!e.getKey().equals(":aside")) sb.append(" <span class=\"hint\">(not a slot of this class)</span>");
                appendParagraphs(sb, e.getValue());
                sb.append("</div>");
            }

            appendExamples(sb, set, cls, c);

            sb.append("<h3>As written</h3><pre class=\"raw\">").append(Html.esc(c.source(cls))).append("</pre>");
            return sb.toString();
        }
    }

    /** The set whose panel is being written, for the links {@link #appendParagraphs} makes. */
    private String rendering = "";

    private String classLinkIn(String cls) {
        return classLink(rendering, cls);
    }

    /** How many examples one panel shows; a class with more says how many it leaves out. */
    private static final int MAX_EXAMPLES = 12;

    /**
     * Examples written by the class: for each concept of the set's file assigned to the class or a class under it, the
     * book's own paragraphs, whole, in the order of the slots of the concept's class, each under its slot's question.
     * A slot the book does not answer for that concept shows as a gap.
     */
    private void appendExamples(StringBuilder sb, String set, String cls, ConceptClasses classes) {
        if (set.isEmpty()) return;
        String text = results.get(set);
        List<Assignment> mine = new ArrayList<>();
        for (Assignment a : assignments(text)) {
            if (classes.names().contains(a.cls()) && classes.chain(a.cls()).stream().anyMatch(k -> k.name().equals(cls))) mine.add(a);
        }
        if (mine.isEmpty()) return;
        Map<String, String[]> books = new LinkedHashMap<>();   // short file name -> {path under works, text}
        Matcher bf = BOOK_FILE.matcher(text);
        while (bf.find()) {
            String abs = bf.group(1).strip();
            String rel = abs.startsWith(worksDir) ? abs.substring(worksDir.length()) : null;
            String src = rel == null ? null : readWorks.apply(rel);
            if (src != null) books.put(rel.substring(rel.lastIndexOf('/') + 1), new String[] {rel, src});
        }
        sb.append("<h3>Examples written by it</h3><p class=\"hint\">The book's own paragraphs, put in the order of the class's "
                + "questions. A question the book does not answer for that concept shows as a gap.</p>");
        int shown = 0;
        for (Assignment a : mine) {
            if (shown++ == MAX_EXAMPLES) {
                sb.append("<p class=\"hint\">… and ").append(mine.size() - MAX_EXAMPLES).append(" more concept(s).</p>");
                break;
            }
            sb.append("<div class=\"example\"><h4>").append(Html.esc(a.concept()));
            if (!a.cls().equals(cls)) sb.append(" <span class=\"badge\">").append(classLink(set, a.cls())).append("</span>");
            sb.append("</h4>");
            for (ConceptClasses.Slot s : classes.whatCanBeSaid(a.cls())) {
                sb.append("<div class=\"ex-slot\"><div class=\"ex-question\"><span class=\"name\">").append(Html.esc(s.name()))
                  .append("</span> ").append(Html.esc(s.question())).append("</div>");
                List<String> refs = a.slots().get(s.name());
                if (refs == null) sb.append("<p class=\"hint\">(this book does not answer it for ").append(Html.esc(a.concept())).append(")</p>");
                else for (String ref : refs) appendWhole(sb, books, ref);
                sb.append("</div>");
            }
            List<String> aside = a.slots().get(":aside");
            if (aside != null) {
                sb.append("<div class=\"ex-slot\"><div class=\"ex-question\"><span class=\"name\">aside</span> said near it, not about it</div>");
                for (String ref : aside) appendWhole(sb, books, ref);
                sb.append("</div>");
            }
            sb.append("</div>");
        }
    }

    /** One paragraph of a book, whole, with a link to it; a code block as code. */
    private void appendWhole(StringBuilder sb, Map<String, String[]> books, String ref) {
        String rule = ref.contains(" / ") ? ref.substring(ref.indexOf(" / ") + 3) : ref;
        String[] book = ref.contains(" / ") ? books.get(ref.substring(0, ref.indexOf(" / "))) : null;
        if (book == null) {
            for (String[] b : books.values()) {
                if (ConceptTree.paragraphText(b[1], rule) != null) {
                    book = b;
                    break;
                }
            }
        }
        String whole = book == null ? null : ConceptTree.paragraphText(book[1], rule);
        sb.append("<div class=\"ex-para\">");
        if (book != null) {
            sb.append("<a href=\"#\" class=\"raw-rule class-source\" data-source=\"").append(Html.esc(book[0])).append("\" data-rule=\"")
              .append(Html.esc(rule)).append("\" title=\"open in the source pane\">").append(Html.esc(rule)).append("</a> ");
        }
        if (whole == null) {
            sb.append("<span class=\"hint\">").append(Html.esc(ref)).append(" (not found)</span>");
        } else if (whole.startsWith("file:")) {
            String code = readWorks.apply(book[0].substring(0, book[0].lastIndexOf('/') + 1) + whole.substring(5));
            sb.append("<pre class=\"raw\">").append(Html.esc(code == null ? whole.substring(5) : code)).append("</pre>");
        } else {
            sb.append(Html.esc(whole));
        }
        sb.append("</div>");
    }

    private void appendParagraphs(StringBuilder sb, List<String[]> ps) {
        if (ps == null) return;
        for (String[] p : ps) {
            sb.append("<div class=\"sample\">");
            if (p[3] != null) sb.append("<span class=\"badge\">").append(classLinkIn(p[3])).append("</span> ");
            if (p[1] != null) {
                sb.append("<a href=\"#\" class=\"raw-rule class-source\" data-source=\"").append(Html.esc(p[1])).append("\" data-rule=\"")
                  .append(Html.esc(p[0])).append("\">").append(Html.esc(p[0])).append("</a>");
            } else {
                sb.append(Html.esc(p[0]));
            }
            if (p[2] != null) sb.append(": ").append(Html.esc(p[2]));
            sb.append("</div>");
        }
    }

    /**
     * The paragraphs the set's file assigned to the class and to the classes under it, by slot: each as {rule, book
     * file under the works directory or null, its nucleus or null, the class it was assigned to when it is not this one}.
     */
    private Map<String, List<String[]>> assigned(String set, String cls, ConceptClasses classes) {
        Map<String, List<String[]>> out = new LinkedHashMap<>();
        if (set.isEmpty()) return out;
        String text = results.get(set);
        Map<String, ConceptTree.Unit> units = new LinkedHashMap<>();
        Map<String, String> fileOfRule = new LinkedHashMap<>();
        Matcher bf = BOOK_FILE.matcher(text);
        while (bf.find()) {
            String abs = bf.group(1).strip();
            String rel = abs.startsWith(worksDir) ? abs.substring(worksDir.length()) : null;
            String src = rel == null ? null : readWorks.apply(rel);
            if (src == null) continue;
            String shortName = rel.substring(rel.lastIndexOf('/') + 1);
            for (ConceptTree.Unit u : ConceptTree.paragraphs(rel, src)) {
                units.putIfAbsent(shortName + " / " + u.rule(), u);
                fileOfRule.putIfAbsent(shortName + " / " + u.rule(), rel);
                units.putIfAbsent(u.rule(), u);
                fileOfRule.putIfAbsent(u.rule(), rel);
            }
        }
        for (Assignment a : assignments(text)) {
            if (!classes.names().contains(a.cls()) || classes.chain(a.cls()).stream().noneMatch(k -> k.name().equals(cls))) continue;
            String under = a.cls().equals(cls) ? null : a.cls();
            for (Map.Entry<String, List<String>> e : a.slots().entrySet()) {
                for (String ref : e.getValue()) {
                    String rule = ref.contains(" / ") ? ref.substring(ref.indexOf(" / ") + 3) : ref;
                    ConceptTree.Unit u = units.get(ref);
                    out.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                       .add(new String[] {rule, fileOfRule.get(ref), u == null ? null : cut(u.nucleus(), 240), under});
                }
            }
        }
        return out;
    }

    /** @return the {@code (assign ...)} forms of a file of assignments, one concept each */
    static List<Assignment> assignments(String text) {
        List<Assignment> out = new ArrayList<>();
        Assignment cur = null;
        for (String line : text.split("\\R")) {
            Matcher a = ASSIGN.matcher(line);
            if (a.matches()) {
                cur = new Assignment(a.group(1).replace("\\\"", "\""), a.group(2), new LinkedHashMap<>());
                out.add(cur);
                continue;
            }
            Matcher s = SLOT.matcher(line);
            if (cur != null && s.matches()) {
                List<String> refs = cur.slots().computeIfAbsent(s.group(1), k -> new ArrayList<>());
                Matcher str = STRING.matcher(s.group(2));
                while (str.find()) refs.add(str.group(1).replace("\\\"", "\""));
                continue;
            }
            cur = null;
        }
        return out;
    }

    /** A file that stands alone ({@code concept-consolidate.yaml}'s result) is read without the classes of {@code concepts/}. */
    private ConceptClasses classes(String set) {
        Map<String, String> files = new LinkedHashMap<>();
        if (!standalone(set)) files.putAll(library);
        if (!set.isEmpty() && results.containsKey(set)) files.put(set, results.get(set));
        return new ConceptClasses(files);
    }

    private boolean standalone(String set) {
        return !set.isEmpty() && results.containsKey(set) && results.get(set).contains("\n;;; standalone\n");
    }

    private boolean defines(String set, String cls) {
        return !set.isEmpty() && defined(results.get(set)).contains(cls);
    }

    /** @return the classes the set's file defines that {@code concepts/} does not */
    private Set<String> added(String set) {
        if (set.isEmpty() || !results.containsKey(set) || standalone(set)) return Set.of();
        Set<String> out = defined(results.get(set));
        out.removeAll(libraryClasses());
        return out;
    }

    /** @return the classes of {@code concepts/} the set's file defines again, with slots added */
    private Set<String> grown(String set) {
        if (set.isEmpty() || !results.containsKey(set) || standalone(set)) return Set.of();
        Set<String> out = defined(results.get(set));
        out.retainAll(libraryClasses());
        return out;
    }

    private Set<String> libraryClasses() {
        Set<String> out = new LinkedHashSet<>();
        for (String t : library.values()) out.addAll(defined(t));
        return out;
    }

    private static Set<String> defined(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = DEFCLASS.matcher(text);
        while (m.find()) out.add(m.group(1).toLowerCase(java.util.Locale.ROOT));
        return out;
    }

    private static String classLink(String set, String cls) {
        return "<a href=\"#\" class=\"class-link\" data-set=\"" + Html.esc(set) + "\" data-class=\"" + Html.esc(cls) + "\">" + Html.esc(cls) + "</a>";
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) + " ..." : s;
    }
}
