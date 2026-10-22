package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The frame library as the viewer sees it: the books' subject-tag trees and the frames, linked by the
 * {@code ;;; subject:} line of each frame. Pure: built from file texts handed to it, no I/O.
 */
public final class Library {

    /** One node of a book's subject-tag tree. The parent is decided by the number: {@code 1.4}'s parent is {@code 1}. */
    public record Subject(String book, String number, String title, String parent) {
    }

    /** One frame file, by its path under {@code frames/}. A sub-frame ({@code parent.part.lisp}) is not a top frame. */
    public record Frame(String path, RuleFile file) {

        /** @return true for {@code design.lisp}, false for {@code design.use-case.lisp} */
        public boolean isTop() {
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.indexOf('.') == name.lastIndexOf('.');
        }

        /** @return true for frames under {@code explanation/} */
        public boolean isIntegrated() {
            return path.startsWith("explanation/");
        }

        /** @return the book and the number of the {@code ;;; subject:} line, or null when the line does not name one */
        public String[] subject() {
            List<String> s = file.header("subject");
            if (s.isEmpty()) return null;
            String[] w = s.get(0).split("\\s+");
            if (w.length < 2) return null;
            return new String[] {w[0], w[1]};
        }

        /** @return the top frame this one belongs to: {@code design.lisp} for {@code design.use-case.lisp} */
        public String topPath() {
            int slash = path.lastIndexOf('/');
            String name = path.substring(slash + 1);
            int dot = name.indexOf('.');
            return dot == name.lastIndexOf('.') ? path : path.substring(0, slash + 1) + name.substring(0, dot) + ".lisp";
        }

        /** @return the frame paths named by {@code ;;; integrates:}, as paths under {@code frames/} */
        public List<String> integrates() {
            List<String> out = new ArrayList<>();
            for (String v : file.header("integrates")) {
                String first = v.split("\\s+")[0];
                if (first.endsWith(".lisp")) out.add(first);
            }
            return out;
        }
    }

    private final Map<String, List<Subject>> books = new LinkedHashMap<>();
    private final Map<String, Frame> frames = new LinkedHashMap<>();

    /** Adds one {@code subjects/<book>.lisp} file. */
    public Library addSubjects(String text) {
        RuleFile f = RuleFile.parse(text);
        String book = null;
        for (RuleFile.Form form : f.forms()) {
            if (form.head().equals("defsubjects") && !form.tokens().isEmpty()) book = form.tokens().get(0).text();
        }
        if (book == null) return this;
        List<Subject> nodes = new ArrayList<>();
        java.util.Set<String> numbers = new java.util.HashSet<>();
        for (RuleFile.Form form : f.forms()) {
            if (!form.head().equals("subject") || form.tokens().size() < 2) continue;
            String number = form.tokens().get(0).text();
            if (!numbers.add(number)) continue;
            nodes.add(new Subject(book, number, form.tokens().get(1).text(), null));
        }
        List<Subject> linked = new ArrayList<>();
        for (Subject s : nodes) linked.add(new Subject(book, s.number(), s.title(), parentOf(s.number(), numbers)));
        books.put(book, linked);
        return this;
    }

    /** Adds one frame file, by its path under {@code frames/}. */
    public Library addFrame(String path, String text) {
        frames.put(path, new Frame(path, RuleFile.parse(text)));
        return this;
    }

    private static String parentOf(String number, java.util.Set<String> numbers) {
        String n = number;
        // 1.4 is under 1, and 1/Sec4 (a section numbered by its chapter only) is under 1
        while (n.contains(".") || n.contains("/")) {
            n = n.substring(0, Math.max(n.lastIndexOf('.'), n.lastIndexOf('/')));
            if (numbers.contains(n)) return n;
        }
        return null;
    }

    public Map<String, List<Subject>> books() {
        return books;
    }

    public Frame frame(String path) {
        return frames.get(path);
    }

    public List<Frame> frames() {
        return List.copyOf(frames.values());
    }

    /**
     * @return the frames whose {@code ;;; subject:} line names this book and number. A child frame put in with
     *         {@code :include} names its own node (23.4 for the deployment of chapter 23), so it is found too.
     */
    public List<Frame> framesOf(String book, String number) {
        List<Frame> out = new ArrayList<>();
        for (Frame f : frames.values()) {
            String[] s = f.subject();
            if (s != null && s[0].equals(book) && s[1].equals(number)) out.add(f);
        }
        return out;
    }

    /** @return the integrated frames that name this frame in {@code ;;; integrates:} */
    public List<Frame> integratedInto(String path) {
        List<Frame> out = new ArrayList<>();
        for (Frame f : frames.values()) {
            if (f.integrates().contains(path)) out.add(f);
        }
        return out;
    }

    /** @return the frames of one kind, concrete (under {@code subject/}) or integrated (under {@code explanation/}) */
    public List<Frame> frames(boolean integrated) {
        List<Frame> out = new ArrayList<>();
        for (Frame f : frames.values()) if (f.isIntegrated() == integrated) out.add(f);
        return out;
    }

    /** @return the subject-tag node a frame names, or null */
    public Subject subjectOf(Frame f) {
        String[] s = f.subject();
        if (s == null || !books.containsKey(s[0])) return null;
        for (Subject n : books.get(s[0])) if (n.number().equals(s[1])) return n;
        return null;
    }

    /** One node of the jstree data: flat form, the parent named by id ({@code #} for a root). */
    public record TreeNode(String id, String parent, String text, Map<String, String> li_attr) {
    }

    /** The tag tab: one root per book with its subject-tag tree; the nodes that have a frame are marked. */
    public List<TreeNode> tagTree() {
        List<TreeNode> out = new ArrayList<>();
        for (Map.Entry<String, List<Subject>> e : books.entrySet()) {
            String book = e.getKey();
            String root = "book:" + book;
            out.add(new TreeNode(root, "#", book, Map.of()));
            for (Subject s : e.getValue()) {
                String id = "s:" + book + ":" + s.number();
                String parent = s.parent() == null ? root : "s:" + book + ":" + s.parent();
                String text = s.number() + " " + s.title();
                Map<String, String> attr = Map.of("data-tag", book + "|" + s.number());
                if (!framesOf(book, s.number()).isEmpty()) {
                    text = "★ " + text;
                    attr = Map.of("data-tag", book + "|" + s.number(), "class", "has-frame");
                }
                out.add(new TreeNode(id, parent, text, attr));
            }
        }
        return out;
    }

    /**
     * The concrete-frame tab or the integrated-frame tab: the frames grouped by directory, each sub-frame under the
     * top frame it belongs to.
     */
    public List<TreeNode> frameTree(boolean integrated) {
        List<TreeNode> out = new ArrayList<>();
        java.util.Set<String> dirs = new java.util.HashSet<>();
        for (Frame f : frames(integrated)) {
            String dir = Html.dirOf(f.path());
            if (dirs.add(dir)) out.add(new TreeNode("d:" + dir, "#", dir, Map.of()));
            String name = f.path().substring(f.path().lastIndexOf('/') + 1);
            String parent = f.isTop() || frame(f.topPath()) == null ? "d:" + dir : "f:" + f.topPath();
            out.add(new TreeNode("f:" + f.path(), parent, name, Map.of("data-frame", f.path(), "class", "has-frame")));
        }
        return out;
    }
}
