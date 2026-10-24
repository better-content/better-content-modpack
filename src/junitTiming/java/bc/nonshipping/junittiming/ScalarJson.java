package bc.nonshipping.junittiming;

import java.math.BigDecimal;
import java.util.*;

/** Small bounded JSON codec for prepared scalar-only observer metadata; no object reflection. */
final class ScalarJson {
    static String encode(Object value) {
        if (value == null || value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        if (value instanceof String text) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 32 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int)c));
                else out.append(c);
            }
            return out.append('"').toString();
        }
        if (value instanceof Map<?, ?> map) {
            var items = new ArrayList<String>();
            for (var item : map.entrySet()) {
                if (!(item.getKey() instanceof String)) throw new IllegalArgumentException("Non-string JSON key");
                items.add(encode(item.getKey()) + ":" + encode(item.getValue()));
            }
            return "{" + String.join(",", items) + "}";
        }
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(ScalarJson::encode).toList()) + "]";
        throw new IllegalArgumentException("Non-scalar metadata");
    }
    static Map<String, Object> object(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.at != text.length() || !(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected JSON object");
        @SuppressWarnings("unchecked") Map<String,Object> result = (Map<String,Object>) value;
        return result;
    }
    private static final class Parser {
        final String text;
        int at;
        Parser(String text) { this.text = text; }
        void space() { while (at < text.length() && " \n\r\t".indexOf(text.charAt(at)) >= 0) at++; }
        char take() { if (at == text.length()) throw new IllegalArgumentException("Truncated JSON"); return text.charAt(at++); }
        Object value(int depth) {
            if (depth > 64) throw new IllegalArgumentException("JSON too deep");
            space(); char c = take();
            if (c == '"') {
                StringBuilder out = new StringBuilder();
                while ((c = take()) != '"') {
                    if (c == '\\') {
                        c = take();
                        switch (c) {
                            case '"', '\\', '/' -> out.append(c);
                            case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                            case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                            case 'u' -> { if (at + 4 > text.length()) throw new IllegalArgumentException("Unicode escape"); out.append((char)Integer.parseInt(text.substring(at, at + 4), 16)); at += 4; }
                            default -> throw new IllegalArgumentException("JSON escape");
                        }
                    } else { if (c < 32) throw new IllegalArgumentException("JSON control character"); out.append(c); }
                }
                return out.toString();
            }
            if (c == '{' || c == '[') {
                boolean object = c == '{'; char end = object ? '}' : ']';
                Map<String,Object> map = new LinkedHashMap<>(); List<Object> list = new ArrayList<>();
                space(); if (at < text.length() && text.charAt(at) == end) { at++; return object ? map : list; }
                while (true) {
                    if (object) {
                        Object key = value(depth + 1); space();
                        if (!(key instanceof String) || take() != ':' || map.containsKey(key)) throw new IllegalArgumentException("JSON key");
                        map.put((String)key, value(depth + 1));
                    } else list.add(value(depth + 1));
                    space(); c = take(); if (c == end) return object ? map : list;
                    if (c != ',') throw new IllegalArgumentException("JSON separator");
                }
            }
            at--;
            for (String literal : List.of("true", "false", "null")) if (text.startsWith(literal, at)) {
                at += literal.length(); return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
            int start = at;
            while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String number = text.substring(start, at);
            if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("JSON number");
            return new BigDecimal(number);
        }
    }
}
