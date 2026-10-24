package com.scivicslab.frameviewer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What the search box of a tab was given: words split at spaces. A text is a match when it holds every word, whatever
 * their case. Each tab's panel asks it of the texts of its tree's nodes and answers the nodes that match.
 */
public final class Search {

    /** One node of a tab's tree that matched: its id in the tree, and where it matched (its title, its text, its rules...). */
    public record Hit(String id, String where) {}

    private final List<String> words;

    public Search(String query) {
        this.words = query == null ? List.of()
                : Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }

    /** @return whether nothing was given to search for */
    public boolean blank() {
        return words.isEmpty();
    }

    /** @return whether the texts, taken together, hold every word; null texts are skipped */
    public boolean in(String... texts) {
        if (words.isEmpty()) return false;
        StringBuilder sb = new StringBuilder();
        for (String t : texts) if (t != null) sb.append(t.toLowerCase(Locale.ROOT)).append('\n');
        String all = sb.toString();
        for (String w : words) if (!all.contains(w)) return false;
        return true;
    }
}
