package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The frame library as the viewer sees it: the frames, and the integrated frames that name them. Pure: built from
 * file texts handed to it, no I/O.
 */
public final class Library {

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

    private final Map<String, Frame> frames = new LinkedHashMap<>();

    /** Adds one frame file, by its path under {@code frames/}. */
    public Library addFrame(String path, String text) {
        frames.put(path, new Frame(path, RuleFile.parse(text)));
        return this;
    }

    public Frame frame(String path) {
        return frames.get(path);
    }

    public List<Frame> frames() {
        return List.copyOf(frames.values());
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

    /** One node of the jstree data: flat form, the parent named by id ({@code #} for a root). */
    public record TreeNode(String id, String parent, String text, Map<String, String> li_attr) {
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
