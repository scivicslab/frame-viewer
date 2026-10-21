package com.scivicslab.frameviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an rst-on-lisp file: its top-level forms ({@code (defrule …)}, {@code (subject …)}, …) and its header
 * lines ({@code ;;; key: value}). Pure: no framework, no I/O.
 *
 * <p>A header line whose text does not start with {@code key:} continues the line before it, so a value written
 * over several {@code ;;;} lines reads as one value. A key that appears more than once (such as {@code made-from})
 * keeps every value.
 */
public final class RuleFile {

    /** One token of a form. */
    public record Token(Kind kind, String text) {
        public enum Kind { SYMBOL, STRING, KEYWORD }

        public boolean isKeyword(String name) {
            return kind == Kind.KEYWORD && text.equals(name);
        }
    }

    /** A top-level form: its head ({@code defrule}, {@code subject}, …) and the tokens after it. Nested lists are flattened. */
    public record Form(String head, List<Token> tokens) {
    }

    /** A {@code defrule}: its name and the tokens after {@code ->}. */
    public record Rule(String name, List<Token> items) {

        /** @return the value after the given keyword, or null */
        public String attr(String keyword) {
            for (int i = 0; i + 1 < items.size(); i++) {
                if (items.get(i).isKeyword(keyword)) return items.get(i + 1).text();
            }
            return null;
        }

        /** @return true when the rule is a blank (it carries {@code :instruction}) */
        public boolean isBlank() {
            return attr(":instruction") != null;
        }
    }

    private final List<Form> forms;
    private final Map<String, List<String>> headers;
    private final Map<String, Rule> rules = new LinkedHashMap<>();

    private RuleFile(List<Form> forms, Map<String, List<String>> headers) {
        this.forms = forms;
        this.headers = headers;
        for (Form f : forms) {
            if (!f.head().equals("defrule") || f.tokens().size() < 2) continue;
            List<Token> t = f.tokens();
            int from = t.get(1).text().equals("->") ? 2 : 1;
            rules.putIfAbsent(t.get(0).text(), new Rule(t.get(0).text(), List.copyOf(t.subList(from, t.size()))));
        }
    }

    public static RuleFile parse(String text) {
        return new RuleFile(forms(text), headers(text));
    }

    public List<Form> forms() {
        return forms;
    }

    /** @return the rules in file order */
    public List<Rule> rules() {
        return List.copyOf(rules.values());
    }

    public Rule rule(String name) {
        return rules.get(name);
    }

    /** @return every value of a header key, in file order; empty when there is none */
    public List<String> header(String key) {
        return headers.getOrDefault(key, List.of());
    }

    /** @return all header keys and values, in file order */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /** @return every string a rule holds, and the strings of the rules it names, in order */
    public List<String> textOf(String name) {
        List<String> out = new ArrayList<>();
        collect(name, out, new java.util.HashSet<>());
        return out;
    }

    private void collect(String name, List<String> out, java.util.Set<String> seen) {
        Rule r = rules.get(name);
        if (r == null || !seen.add(name)) return;
        for (int i = 0; i < r.items().size(); i++) {
            Token t = r.items().get(i);
            if (t.kind() == Token.Kind.KEYWORD) {
                // the value after an attribute such as :heading or :kind is not text of the paragraph
                if (!isRelationKeyword(t.text()) && i + 1 < r.items().size()) i++;
                continue;
            }
            if (t.kind() == Token.Kind.STRING) out.add(t.text());
            else collect(t.text(), out, seen);
        }
    }

    /** Attributes whose value is not a rule name. Every other keyword is a relation that names a rule. */
    private static final java.util.Set<String> ATTRIBUTES = java.util.Set.of(
            ":heading", ":kind", ":file", ":alt", ":doc", ":relation", ":instruction", ":sample", ":frame",
            ":include", ":source", ":rank");

    static boolean isRelationKeyword(String keyword) {
        return !ATTRIBUTES.contains(keyword);
    }

    // ---- reading ----

    /** {@code key: value}, or {@code key (note): value} as in {@code roles (role: a / b):}. */
    private static final java.util.regex.Pattern KEYED =
            java.util.regex.Pattern.compile("([A-Za-z][A-Za-z0-9-]*)( \\([^)]*\\))?:(.*)");

    private static Map<String, List<String>> headers(String text) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String lastKey = null;
        for (String line : text.split("\n", -1)) {
            if (!line.startsWith(";;;")) {
                lastKey = null;
                continue;
            }
            String body = line.substring(3).strip();
            java.util.regex.Matcher m = KEYED.matcher(body);
            if (m.matches() && !line.substring(3).startsWith("   ")) {
                String key = m.group(1);
                out.computeIfAbsent(key, k -> new ArrayList<>()).add(m.group(3).strip());
                lastKey = key;
            } else if (lastKey != null && !body.isEmpty()) {
                List<String> values = out.get(lastKey);
                String prev = values.remove(values.size() - 1);
                values.add(prev.isEmpty() ? body : prev + "\n" + body);
            }
        }
        return out;
    }

    private static List<Form> forms(String text) {
        List<Form> out = new ArrayList<>();
        int i = 0, n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == ';') { i = endOfLine(text, i); continue; }
            if (c == '(') {
                List<Token> tokens = new ArrayList<>();
                i = readList(text, i + 1, tokens);
                if (!tokens.isEmpty() && tokens.get(0).kind() == Token.Kind.SYMBOL) {
                    out.add(new Form(tokens.get(0).text(), List.copyOf(tokens.subList(1, tokens.size()))));
                }
                continue;
            }
            i++;
        }
        return out;
    }

    /** Reads up to the matching ')' and returns the index after it. Nested lists are flattened into {@code tokens}. */
    private static int readList(String s, int i, List<Token> tokens) {
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == ')') return i + 1;
            if (c == '(') { i = readList(s, i + 1, tokens); continue; }
            if (c == ';') { i = endOfLine(s, i); continue; }
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\' && i + 1 < n) i++;
                    sb.append(s.charAt(i++));
                }
                tokens.add(new Token(Token.Kind.STRING, sb.toString()));
                i++;
                continue;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(s.charAt(i)) && "()\";".indexOf(s.charAt(i)) < 0) i++;
            String word = s.substring(start, i);
            tokens.add(new Token(word.startsWith(":") ? Token.Kind.KEYWORD : Token.Kind.SYMBOL, word));
        }
        return i;
    }

    private static int endOfLine(String s, int i) {
        int nl = s.indexOf('\n', i);
        return nl < 0 ? s.length() : nl + 1;
    }
}
