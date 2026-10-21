package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The source texts the concrete frames were made from, and which frame took which paragraph. A frame names its
 * source files in {@code ;;; made-from:} (a sub-frame adds the lines of its top frame to its own), and names paragraphs of
 * those files in the part of {@code made-from} after {@code " / "} and in {@code members}, {@code elsewhere} and
 * {@code left-out}. Pure: the files are read through {@code readWorks}, which takes a path under the works
 * directory and returns the text or null.
 */
public final class Sources {

    /** One frame naming one paragraph, and on which header line. */
    public record Claim(String frame, String kind) {
    }

    private static final List<String> KINDS = List.of("made-from", "members", "elsewhere", "left-out");

    private final Map<String, RuleFile> files = new TreeMap<>();
    private final Map<String, List<String>> filesOfFrame = new LinkedHashMap<>();
    private final Map<String, Map<String, List<Claim>>> claims = new LinkedHashMap<>();

    public Sources(Library library, Function<String, String> readWorks) {
        for (Library.Frame f : library.frames(false)) {
            // a sub-frame reads its own made-from lines first, then those of its top frame
            Library.Frame top = library.frame(f.topPath());
            List<String> madeFrom = new ArrayList<>(f.file().header("made-from"));
            if (top != null && top != f) madeFrom.addAll(top.file().header("made-from"));
            List<String> mine = new ArrayList<>();
            for (String v : madeFrom) {
                for (String file : sourceFiles(v)) {
                    if (mine.contains(file)) continue;
                    if (!files.containsKey(file)) {
                        String text = readWorks.apply(file);
                        if (text == null) continue;
                        files.put(file, RuleFile.parse(text));
                    }
                    mine.add(file);
                }
            }
            filesOfFrame.put(f.path(), mine);
            for (String kind : KINDS) {
                for (String v : f.file().header(kind)) {
                    String names = kind.equals("made-from") ? afterSlash(v) : v;
                    for (String word : names.split("[\\s,;()]+")) claim(f.path(), kind, word, mine);
                }
            }
        }
    }

    /** A section that runs on from one file into the next has its rule in both, so the claim goes to every file holding it. */
    private void claim(String frame, String kind, String word, List<String> in) {
        for (String file : in) {
            if (files.get(file).rule(word) == null) continue;
            List<Claim> list = claims.computeIfAbsent(file, k -> new LinkedHashMap<>()).computeIfAbsent(word, k -> new ArrayList<>());
            if (!list.contains(new Claim(frame, kind))) list.add(new Claim(frame, kind));
        }
    }

    /** @return the source files some frame was made from, by path under the works directory, sorted */
    public List<String> files() {
        return List.copyOf(files.keySet());
    }

    public RuleFile file(String path) {
        return files.get(path);
    }

    /** @return the source files of one frame, in the order its {@code made-from} lines name them */
    public List<String> filesOf(String framePath) {
        return filesOfFrame.getOrDefault(framePath, List.of());
    }

    /** @return the frames naming one paragraph (a rule) of a source file */
    public List<Claim> claimsOf(String file, String rule) {
        return claims.getOrDefault(file, Map.of()).getOrDefault(rule, List.of());
    }

    /** @return the source file holding a paragraph a frame names, or null */
    public String fileOf(String framePath, String rule) {
        for (String file : filesOf(framePath)) if (files.get(file).rule(rule) != null) return file;
        return null;
    }

    /**
     * The files a {@code made-from} value names: the part before {@code " / "}, which may list several entries, each
     * starting with {@code doc_}. An entry ending in {@code .lisp} is the file; any other entry is a directory holding
     * a file of its own name. Names may hold spaces; a trailing {@code ...} is dropped. A value written over several
     * {@code ;;;} lines reads as one line.
     */
    static List<String> sourceFiles(String madeFrom) {
        madeFrom = madeFrom.replace('\n', ' ');
        int slash = madeFrom.indexOf(" / ");
        String left = slash < 0 ? madeFrom : madeFrom.substring(0, slash);
        List<String> out = new ArrayList<>();
        for (String part : left.split(" (?=doc_)")) {
            String p = part.strip();
            if (p.endsWith("...")) p = p.substring(0, p.length() - 3).strip();
            if (!p.startsWith("doc_")) continue;
            out.add(p.endsWith(".lisp") ? p : p + "/" + p.substring(p.lastIndexOf('/') + 1) + ".lisp");
        }
        return out;
    }

    private static String afterSlash(String madeFrom) {
        madeFrom = madeFrom.replace('\n', ' ');
        int slash = madeFrom.indexOf(" / ");
        return slash < 0 ? "" : madeFrom.substring(slash + 3);
    }
}
