package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The edge lists of the frame library ({@code rst-on-lisp/links/**}{@code /*.tsv}, {@code FrameLinks_261022_oo01}),
 * indexed from both ends. An edge joins a rule of one file to a rule of another: {@code integrates} joins an integrated
 * frame's rule and a concrete frame's rule, {@code made-from} a concrete frame's rule and a book paragraph. A frame is
 * named by its path under {@code frames/}, a book by its path under the works directory. Pure.
 */
public final class Links {

    /** The other end of an edge, seen from one end. */
    public record Edge(String file, String rule, String kind) {
    }

    private final Map<String, Map<String, List<Edge>>> byFile = new LinkedHashMap<>();

    /** @param texts the edge lists: five tab-separated columns, {@code a_file a_rule b_file b_rule kind}; {@code #} lines are notes */
    public Links(List<String> texts) {
        for (String text : texts) {
            for (String line : text.split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] c = line.split("\t");
                if (c.length < 5) continue;
                add(c[0].strip(), c[1].strip(), new Edge(c[2].strip(), c[3].strip(), c[4].strip()));
                add(c[2].strip(), c[3].strip(), new Edge(c[0].strip(), c[1].strip(), c[4].strip()));
            }
        }
    }

    private void add(String file, String rule, Edge other) {
        List<Edge> list = byFile.computeIfAbsent(file, k -> new LinkedHashMap<>()).computeIfAbsent(rule, k -> new ArrayList<>());
        if (!list.contains(other)) list.add(other);
    }

    /** @return the edges at one rule of a file, in the order the lists give them; empty when none */
    public List<Edge> of(String file, String rule) {
        return byFile.getOrDefault(file, Map.of()).getOrDefault(rule, List.of());
    }

    /** @return whether any edge has an end in this file */
    public boolean has(String file) {
        return byFile.containsKey(file);
    }
}
