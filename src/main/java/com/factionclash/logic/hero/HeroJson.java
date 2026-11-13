package com.factionclash.logic.hero;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hero files' JSON reader (v1 26.1 step 1): it keeps the line and column of every key and value, forgives what
 * v1 3.1 forgives (a byte-order mark, Windows line ends, a markdown code fence or a pasted chat answer around the
 * hero, {@code //} and {@code /* *}{@code /} comments, a comma before {@code }} or {@code ]}), each reported as a
 * note, and rejects everything else with the exact position and a plain explanation: a missing comma, colon or quote,
 * single or curly quotes, unclosed brackets, text after the end, duplicate keys, a file too big, nesting too deep, a
 * string too long, YAML. Lines and columns count from 1. Pure Java, unit-tested ({@code HeroJsonTest}).
 */
public final class HeroJson {

    // ------------------------------------------------------------------ the tree

    /** A value with the position of its first character. */
    public sealed interface Node permits Obj, Arr, Str, Num, Bool, Null {
        int line();

        int col();

        /** "an object", "a list", "text", "a number", "true or false", "null": for messages. */
        String what();
    }

    /** One key of an object, with the key's own position. */
    public record Member(String key, int line, int col, Node value) {
    }

    public record Obj(int line, int col, List<Member> members) implements Node {
        public Member member(String key) {
            for (Member m : members) {
                if (m.key.equals(key)) {
                    return m;
                }
            }
            return null;
        }

        public Node get(String key) {
            Member m = member(key);
            return m == null ? null : m.value;
        }

        public String what() {
            return "an object";
        }
    }

    public record Arr(int line, int col, List<Node> items) implements Node {
        public String what() {
            return "a list";
        }
    }

    public record Str(int line, int col, String value) implements Node {
        public String what() {
            return "text";
        }
    }

    /** {@code text} is the number as written ({@code 1.0} and {@code 1} differ there). */
    public record Num(int line, int col, double value, String text) implements Node {
        public boolean integral() {
            return value == Math.rint(value) && !Double.isInfinite(value);
        }

        public String what() {
            return "a number";
        }
    }

    public record Bool(int line, int col, boolean value) implements Node {
        public String what() {
            return "true or false";
        }
    }

    public record Null(int line, int col) implements Node {
        public String what() {
            return "null";
        }
    }

    // ------------------------------------------------------------------ results

    /** Machine codes of what the reader forgave or refused (each has its own message and a unit test). */
    public enum Code {
        // forgiven (NOTE)
        BOM, CRLF, CODE_FENCE, CODE_BLOCK, COMMENTS, TRAILING_COMMA,
        // refused (NOT LOADED)
        EMPTY, TOO_BIG, YAML, NOT_JSON, MISSING_COMMA, MISSING_COLON, KEY_QUOTES, SINGLE_QUOTES, CURLY_QUOTES,
        UNCLOSED_STRING, LONG_STRING, BAD_ESCAPE, UNQUOTED_TEXT, BAD_NUMBER, UNCLOSED, WRONG_CLOSE, TRAILING_TEXT,
        DUPLICATE_KEY, TOO_DEEP, UNCLOSED_COMMENT, MISSING_VALUE
    }

    /** A forgiven point (a note) or the reason the file cannot be read. */
    public record Issue(Code code, int line, int col, String message) {
    }

    public record Result(Node root, List<Issue> notes, Issue error) {
        public boolean ok() {
            return error == null;
        }
    }

    public static Result parse(String text) {
        return parse(text, HeroCatalogue.MAX_FILE_BYTES, HeroCatalogue.MAX_DEPTH, HeroCatalogue.MAX_STRING);
    }

    /** Reads {@code text}; never throws. */
    public static Result parse(String text, int maxBytes, int maxDepth, int maxString) {
        List<Issue> notes = new ArrayList<>();
        int bytes = utf8Length(text);
        if (bytes > maxBytes) {
            return fail(notes, new Issue(Code.TOO_BIG, 0, 0, "the file is " + kb(bytes) + " KB; a hero file may be at"
                    + " most " + kb(maxBytes) + " KB"));
        }
        String s = text;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
            notes.add(new Issue(Code.BOM, 1, 1, "the byte-order mark at the start of the file was ignored"));
        }
        if (s.contains("\r\n")) {
            int at = s.indexOf("\r\n");
            notes.add(new Issue(Code.CRLF, lineOf(s, at), 0, "Windows line ends (CR LF) were read as plain line ends"));
        }
        s = codeBlock(s, notes);
        if (s.isBlank()) {
            return fail(notes, new Issue(Code.EMPTY, 1, 1, "the file is empty. A hero file is one JSON object: { ... }"));
        }
        Parser p = new Parser(s, maxDepth, maxString, notes);
        try {
            p.ws();
            char c = p.peek();
            if (c != '{' && c != '[') {
                return fail(notes, p.notJson());
            }
            Node root = p.value(0);
            p.ws();
            if (!p.end()) {
                throw p.err(Code.TRAILING_TEXT, p.line, p.col(), "there is text after the end of the hero ("
                        + p.excerpt() + "). Several heroes in one file go in a list: [ {...}, {...} ]");
            }
            p.finishNotes();
            return new Result(root, Collections.unmodifiableList(notes), null);
        } catch (Stop stop) {
            p.finishNotes();
            return fail(notes, stop.issue);
        }
    }

    private static Result fail(List<Issue> notes, Issue error) {
        return new Result(null, Collections.unmodifiableList(notes), error);
    }

    private static String kb(int bytes) {
        return String.valueOf((bytes + 1023) / 1024);
    }

    static int utf8Length(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                n++;
            } else if (c < 0x800) {
                n += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                n += 4;
                i++;
            } else {
                n += 3;
            }
        }
        return n;
    }

    private static int lineOf(String s, int index) {
        int line = 1;
        for (int i = 0; i < index && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * A markdown code fence: the first fenced block is read; everything outside it becomes spaces (line breaks kept),
     * so lines and columns stay those of the original file.
     */
    private static String codeBlock(String s, List<Issue> notes) {
        String[] lines = s.split("\n", -1);
        int open = -1, close = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("```")) {
                if (open < 0) {
                    open = i;
                } else {
                    close = i;
                    break;
                }
            }
        }
        if (open < 0) {
            return s;
        }
        boolean textAround = false;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < lines.length; i++) {
            boolean inside = i > open && (close < 0 || i < close);
            if (!inside && i != open && i != close && !lines[i].isBlank()) {
                textAround = true;
            }
            sb.append(inside ? lines[i] : " ".repeat(lines[i].length()));
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        notes.add(textAround
                ? new Issue(Code.CODE_BLOCK, open + 1, 1, "read the code block; the text around it was ignored")
                : new Issue(Code.CODE_FENCE, open + 1, 1, "the markdown code fence around the hero was removed"));
        return sb.toString();
    }

    // ------------------------------------------------------------------ the parser

    private static final class Stop extends RuntimeException {
        final Issue issue;

        Stop(Issue issue) {
            super(issue.message(), null, false, false);
            this.issue = issue;
        }
    }

    private static final class Parser {
        final String s;
        final int maxDepth, maxString;
        final List<Issue> notes;
        int i, line = 1, lineStart;
        int comments, commentLine, commentCol, trailing, trailingLine, trailingCol;

        Parser(String s, int maxDepth, int maxString, List<Issue> notes) {
            this.s = s;
            this.maxDepth = maxDepth;
            this.maxString = maxString;
            this.notes = notes;
        }

        int col() {
            return i - lineStart + 1;
        }

        boolean end() {
            return i >= s.length();
        }

        char peek() {
            return end() ? '\0' : s.charAt(i);
        }

        void advance() {
            if (s.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
            i++;
        }

        Stop err(Code code, int l, int c, String msg) {
            return new Stop(new Issue(code, l, c, msg));
        }

        void finishNotes() {
            if (comments > 0) {
                notes.add(new Issue(Code.COMMENTS, commentLine, commentCol, comments == 1 ? "a comment was ignored"
                        : comments + " comments were ignored (JSON has no comments; a key starting with _ is one)"));
            }
            if (trailing > 0) {
                notes.add(new Issue(Code.TRAILING_COMMA, trailingLine, trailingCol, (trailing == 1
                        ? "a comma before } or ] was ignored" : trailing + " commas before } or ] were ignored")));
            }
        }

        /** Skips whitespace and comments. */
        void ws() {
            while (!end()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    advance();
                } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                    noteComment();
                    while (!end() && s.charAt(i) != '\n') {
                        advance();
                    }
                } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                    int l = line, cc = col();
                    noteComment();
                    advance();
                    advance();
                    while (!end() && !(s.charAt(i) == '*' && i + 1 < s.length() && s.charAt(i + 1) == '/')) {
                        advance();
                    }
                    if (end()) {
                        throw err(Code.UNCLOSED_COMMENT, l, cc, "the comment opened with /* is never closed with */");
                    }
                    advance();
                    advance();
                } else {
                    return;
                }
            }
        }

        void noteComment() {
            if (comments++ == 0) {
                commentLine = line;
                commentCol = col();
            }
        }

        void noteTrailing(int l, int c) {
            if (trailing++ == 0) {
                trailingLine = l;
                trailingCol = c;
            }
        }

        Issue notJson() {
            String first = s.substring(i, Math.min(s.length(), s.indexOf('\n', i) < 0 ? s.length() : s.indexOf('\n', i)));
            if (first.matches("^-?\\s*[A-Za-z_][A-Za-z0-9_ -]*:(\\s.*)?$") || first.trim().equals("---")) {
                return new Issue(Code.YAML, line, col(), "this looks like YAML (\"" + first.trim()
                        + "\"); hero files are JSON: { \"name\": \"...\", ... }");
            }
            return new Issue(Code.NOT_JSON, line, col(), "a hero file starts with { (one hero) or [ (a list of"
                    + " heroes), not with \"" + excerpt() + "\"");
        }

        String excerpt() {
            int e = i;
            while (e < s.length() && e - i < 20 && s.charAt(e) != '\n') {
                e++;
            }
            return s.substring(i, e).trim();
        }

        Node value(int depth) {
            ws();
            if (end()) {
                throw err(Code.MISSING_VALUE, line, col(), "the file ends where a value was expected");
            }
            char c = peek();
            int l = line, cc = col();
            switch (c) {
                case '{':
                    return object(depth + 1);
                case '[':
                    return array(depth + 1);
                case '"':
                    return new Str(l, cc, string());
                case '\'':
                    throw err(Code.SINGLE_QUOTES, l, cc, "single quotes ' are not JSON quotes. Use double quotes:"
                            + " \"like this\"");
                case '“', '”', '„', '‘', '’':
                    throw curly(l, cc);
                default:
                    break;
            }
            if (c == '-' || c >= '0' && c <= '9' || c == '+' || c == '.') {
                return number();
            }
            if (Character.isLetter(c) || c == '_') {
                int st = i;
                while (!end() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                    advance();
                }
                String w = s.substring(st, i);
                switch (w) {
                    case "true":
                        return new Bool(l, cc, true);
                    case "false":
                        return new Bool(l, cc, false);
                    case "null":
                        return new Null(l, cc);
                    default:
                        String hint = w.equalsIgnoreCase("true") || w.equalsIgnoreCase("false")
                                || w.equalsIgnoreCase("null") ? " JSON writes " + w.toLowerCase() + " in lower case."
                                : " Text must be in double quotes: \"" + w + "\".";
                        throw err(Code.UNQUOTED_TEXT, l, cc, w + " is not a JSON value." + hint);
                }
            }
            if (c == '}' || c == ']') {
                throw err(Code.MISSING_VALUE, l, cc, "a value is missing before " + c);
            }
            throw err(Code.UNQUOTED_TEXT, l, cc, "\"" + c + "\" cannot start a value. Values are \"text\", numbers,"
                    + " true, false, null, {objects} or [lists]");
        }

        Stop curly(int l, int c) {
            return err(Code.CURLY_QUOTES, l, c, "curly quotes “ ” are used as JSON quotes. Use straight"
                    + " double quotes \" (a word processor or chat app may have changed them)");
        }

        Obj object(int depth) {
            int l = line, c = col();
            checkDepth(depth, l, c);
            advance();
            List<Member> members = new ArrayList<>();
            Map<String, Member> seen = new LinkedHashMap<>();
            ws();
            if (peek() == '}') {
                advance();
                return new Obj(l, c, members);
            }
            while (true) {
                ws();
                if (end()) {
                    throw unclosed('{', l, c);
                }
                char k = peek();
                int kl = line, kc = col();
                if (k == '}' && !members.isEmpty()) {
                    noteTrailing(kl, kc);
                    advance();
                    return new Obj(l, c, members);
                }
                String key;
                if (k == '"') {
                    key = string();
                } else if (k == '\'') {
                    throw err(Code.SINGLE_QUOTES, kl, kc, "single quotes ' are not JSON quotes. Use double quotes:"
                            + " \"name\"");
                } else if (k == '“' || k == '”' || k == '„' || k == '‘' || k == '’') {
                    throw curly(kl, kc);
                } else if (Character.isLetter(k) || k == '_') {
                    int st = i;
                    while (!end() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                        advance();
                    }
                    String w = s.substring(st, i);
                    throw err(Code.KEY_QUOTES, kl, kc, "the field name " + w + " must be in double quotes: \"" + w
                            + "\"");
                } else if (k == ']') {
                    throw wrongClose(']', '{', l, c);
                } else {
                    throw err(Code.UNQUOTED_TEXT, kl, kc, "a field name in double quotes was expected here, not \""
                            + k + "\"");
                }
                ws();
                if (peek() != ':') {
                    throw err(Code.MISSING_COLON, line, col(), "a colon is missing after \"" + key + "\". Write \""
                            + key + "\": value");
                }
                advance();
                Node v = value(depth);
                Member m = new Member(key, kl, kc, v);
                Member old = seen.get(key);
                if (old != null && !key.startsWith("_")) {
                    throw err(Code.DUPLICATE_KEY, kl, kc, "\"" + key + "\" appears twice in the same object (first"
                            + " at line " + old.line + "). Keep one");
                }
                seen.put(key, m);
                members.add(m);
                ws();
                char n = peek();
                if (n == ',') {
                    advance();
                    continue;
                }
                if (n == '}') {
                    advance();
                    return new Obj(l, c, members);
                }
                if (end()) {
                    throw unclosed('{', l, c);
                }
                if (n == '"' || Character.isLetter(n)) {
                    String next = n == '"' ? peekString() : excerpt();
                    throw err(Code.MISSING_COMMA, line, col(), "a comma is missing before \"" + next + "\". In JSON,"
                            + " fields are separated by commas");
                }
                if (n == ']') {
                    throw wrongClose(']', '{', l, c);
                }
                throw err(Code.MISSING_COMMA, line, col(), "a comma or } was expected after the value of \"" + key
                        + "\", not \"" + n + "\"");
            }
        }

        Arr array(int depth) {
            int l = line, c = col();
            checkDepth(depth, l, c);
            advance();
            List<Node> items = new ArrayList<>();
            ws();
            if (peek() == ']') {
                advance();
                return new Arr(l, c, items);
            }
            while (true) {
                ws();
                if (end()) {
                    throw unclosed('[', l, c);
                }
                if (peek() == ']' && !items.isEmpty()) {
                    noteTrailing(line, col());
                    advance();
                    return new Arr(l, c, items);
                }
                if (peek() == '}') {
                    throw wrongClose('}', '[', l, c);
                }
                items.add(value(depth));
                ws();
                char n = peek();
                if (n == ',') {
                    advance();
                    continue;
                }
                if (n == ']') {
                    advance();
                    return new Arr(l, c, items);
                }
                if (end()) {
                    throw unclosed('[', l, c);
                }
                if (n == '}') {
                    throw wrongClose('}', '[', l, c);
                }
                throw err(Code.MISSING_COMMA, line, col(), "a comma is missing between two items of the list opened"
                        + " at line " + l + ". In JSON, list items are separated by commas");
            }
        }

        void checkDepth(int depth, int l, int c) {
            if (depth > maxDepth) {
                throw err(Code.TOO_DEEP, l, c, "the file is nested deeper than " + maxDepth + " levels here");
            }
        }

        Stop unclosed(char open, int l, int c) {
            char close = open == '{' ? '}' : ']';
            return err(Code.UNCLOSED, l, c, "the " + open + " opened here is never closed: a " + close
                    + " is missing");
        }

        Stop wrongClose(char found, char open, int l, int c) {
            char want = open == '{' ? '}' : ']';
            return err(Code.WRONG_CLOSE, line, col(), found + " closes the " + open + " opened at line " + l
                    + ", column " + c + "; it needs " + want);
        }

        String peekString() {
            int e = s.indexOf('"', i + 1);
            int nl = s.indexOf('\n', i + 1);
            if (e < 0 || nl >= 0 && nl < e) {
                return excerpt();
            }
            return s.substring(i + 1, e);
        }

        String string() {
            int l = line, c = col();
            advance();
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (end() || peek() == '\n') {
                    throw err(Code.UNCLOSED_STRING, l, c, "this text is never closed: a closing \" is missing");
                }
                char ch = peek();
                if (ch == '"') {
                    advance();
                    break;
                }
                if (ch == '\\') {
                    int el = line, ec = col();
                    advance();
                    if (end()) {
                        throw err(Code.UNCLOSED_STRING, l, c, "this text is never closed: a closing \" is missing");
                    }
                    char e = peek();
                    advance();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length() || !s.substring(i, i + 4).matches("[0-9a-fA-F]{4}")) {
                                throw err(Code.BAD_ESCAPE, el, ec, "\\u must be followed by four hex digits");
                            }
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            for (int k = 0; k < 4; k++) {
                                advance();
                            }
                        }
                        default -> throw err(Code.BAD_ESCAPE, el, ec, "\\" + e + " is not a JSON escape. Write \\\\"
                                + " for a backslash");
                    }
                } else {
                    sb.append(ch);
                    advance();
                }
                if (sb.length() > maxString) {
                    throw err(Code.LONG_STRING, l, c, "this text is longer than " + maxString + " characters");
                }
            }
            return sb.toString();
        }

        Num number() {
            int l = line, c = col();
            int st = i;
            while (!end() && "+-0123456789.eE".indexOf(peek()) >= 0) {
                advance();
            }
            String t = s.substring(st, i);
            if (!t.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                String why = t.startsWith("+") ? "JSON numbers have no + sign"
                        : t.startsWith(".") || t.startsWith("-.") ? "write a 0 before the point: 0.5"
                        : t.endsWith(".") ? "write a digit after the point, or leave the point out"
                        : t.matches("-?0[0-9]+.*") ? "a number cannot start with 0" : "it is not a number";
                throw err(Code.BAD_NUMBER, l, c, t + " is not a JSON number: " + why);
            }
            double v = Double.parseDouble(t);
            if (Double.isInfinite(v)) {
                throw err(Code.BAD_NUMBER, l, c, t + " is too large");
            }
            return new Num(l, c, v, t);
        }
    }
}
