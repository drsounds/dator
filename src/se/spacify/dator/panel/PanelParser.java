package se.spacify.dator.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ISPF Dialog Manager-flavored panel definitions:
 *
 * <pre>
 * )ATTR
 *   @ TYPE(INPUT) LEN(20) REQUIRED(YES)
 *   % TYPE(OUTPUT) LEN(20)
 * )BODY
 *   Customer ID  . . . @CUSTID
 *   Name . . . . . . . @NAME
 *   Balance  . . . . . %BALANCE
 * )INIT
 *   &amp;STATUS = 'NEW'
 * )PROC
 *   VER (&amp;CUSTID,NONBLANK)
 *   VER (&amp;NAME,NONBLANK)
 * )END
 * </pre>
 *
 * )ATTR lines declare a punctuation character as either an INPUT (editable)
 * or OUTPUT (protected) field marker, with an optional LEN, CAPS and
 * REQUIRED. In )BODY, wherever a declared attribute character is
 * immediately followed by an identifier, that identifier becomes a field
 * bound (case-insensitively) to a table column of the same name; everything
 * else on the line is literal text, positioned exactly as typed. )INIT sets
 * default text for a field on a brand-new row. )PROC supports
 * "VER (&amp;FIELD,NONBLANK)", "VER (&amp;FIELD,NUM)" and
 * "VER (&amp;FIELD,RANGE,min,max)", checked in order before a save.
 */
public final class PanelParser {

    private static final Pattern SECTION =
            Pattern.compile("^\\)(ATTR|BODY|INIT|PROC|END)\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_KEYWORD = Pattern.compile("([A-Za-z]+)\\(([^)]*)\\)");
    private static final Pattern INIT_LINE = Pattern.compile("^&([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");

    private PanelParser() {
    }

    public static PanelDefinition parse(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new PanelParseException("Panel source is empty");
        }
        PanelDefinition def = new PanelDefinition();
        String section = null;
        int bodyRow = 0;
        String[] lines = source.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String rawLine = lines[i];
            if (rawLine.endsWith("\r")) {
                rawLine = rawLine.substring(0, rawLine.length() - 1);
            }
            String trimmed = rawLine.trim();

            Matcher sm = SECTION.matcher(trimmed);
            if (sm.matches()) {
                section = sm.group(1).toUpperCase(Locale.ROOT);
                if (section.equals("BODY")) {
                    bodyRow = 0;
                }
                continue;
            }
            if (section == null) {
                if (trimmed.isEmpty() || trimmed.startsWith("*")) {
                    continue;
                }
                throw new PanelParseException(lineNumber,
                        "Content before the first ) section (expected )ATTR, )BODY, )INIT, )PROC or )END)");
            }

            switch (section) {
                case "ATTR":
                    parseAttrLine(def, trimmed, lineNumber);
                    break;
                case "BODY":
                    parseBodyLine(def, rawLine, bodyRow, lineNumber);
                    bodyRow++;
                    break;
                case "INIT":
                    parseInitLine(def, trimmed, lineNumber);
                    break;
                case "PROC":
                    parseProcLine(def, trimmed, lineNumber);
                    break;
                case "END":
                default:
                    break;
            }
        }

        if (def.getFields().isEmpty()) {
            throw new PanelParseException("Panel has no input/output fields in )BODY");
        }
        for (VerifyRule rule : def.getVerifyRules()) {
            if (def.findField(rule.getField()) == null) {
                throw new PanelParseException(
                        ")PROC refers to unknown field \"" + rule.getField() + "\" (not declared in )BODY)");
            }
        }
        for (InitAssignment init : def.getInitAssignments()) {
            if (def.findField(init.getField()) == null) {
                throw new PanelParseException(
                        ")INIT refers to unknown field \"" + init.getField() + "\" (not declared in )BODY)");
            }
        }
        return def;
    }

    private static void parseAttrLine(PanelDefinition def, String trimmed, int lineNumber) {
        if (trimmed.isEmpty() || trimmed.startsWith("*")) {
            return;
        }
        char code = trimmed.charAt(0);
        if (Character.isLetterOrDigit(code)) {
            throw new PanelParseException(lineNumber,
                    "Attribute character '" + code + "' must be punctuation, not a letter or digit");
        }
        PanelAttr attr = new PanelAttr(code);
        String rest = trimmed.substring(1).trim();
        Matcher m = ATTR_KEYWORD.matcher(rest);
        boolean any = false;
        while (m.find()) {
            any = true;
            String key = m.group(1).toUpperCase(Locale.ROOT);
            String value = m.group(2).trim();
            switch (key) {
                case "TYPE":
                    String type = value.toUpperCase(Locale.ROOT);
                    if (!type.equals(PanelAttr.INPUT) && !type.equals(PanelAttr.OUTPUT)) {
                        throw new PanelParseException(lineNumber,
                                "TYPE must be INPUT or OUTPUT, got \"" + value + "\"");
                    }
                    attr.setType(type);
                    break;
                case "LEN":
                    try {
                        attr.setLen(Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        throw new PanelParseException(lineNumber, "LEN must be a number, got \"" + value + "\"");
                    }
                    break;
                case "CAPS":
                    attr.setCaps("ON".equalsIgnoreCase(value));
                    break;
                case "REQUIRED":
                    attr.setRequired("YES".equalsIgnoreCase(value));
                    break;
                default:
                    throw new PanelParseException(lineNumber, "Unknown )ATTR keyword \"" + key + "\"");
            }
        }
        if (!any) {
            throw new PanelParseException(lineNumber,
                    "Attribute \"" + code + "\" needs at least one keyword, e.g. TYPE(INPUT)");
        }
        def.putAttr(attr);
    }

    private static void parseBodyLine(PanelDefinition def, String rawLine, int row, int lineNumber) {
        String line = rawLine;
        if (line.endsWith("+")) {
            line = line.substring(0, line.length() - 1);
        }
        int n = line.length();
        int col = 0;
        StringBuilder literal = new StringBuilder();
        int literalStart = 0;

        while (col < n) {
            char c = line.charAt(col);
            PanelAttr attr = def.getAttrs().get(c);
            boolean startsField = attr != null && col + 1 < n && isIdentStart(line.charAt(col + 1));
            if (startsField) {
                if (literal.length() > 0) {
                    def.addLiteral(new PanelLiteral(literal.toString(), row, literalStart));
                    literal.setLength(0);
                }
                int start = col + 1;
                int end = start;
                while (end < n && isIdentPart(line.charAt(end))) {
                    end++;
                }
                String name = line.substring(start, end).toUpperCase(Locale.ROOT);
                if (def.findField(name) != null) {
                    throw new PanelParseException(lineNumber, "Field \"" + name + "\" is declared more than once");
                }
                def.addField(new PanelField(name, row, col, attr));
                col = end;
                literalStart = col;
            } else {
                if (literal.length() == 0) {
                    literalStart = col;
                }
                literal.append(c);
                col++;
            }
        }
        if (literal.length() > 0) {
            def.addLiteral(new PanelLiteral(literal.toString(), row, literalStart));
        }
        def.growBounds(row, n);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static void parseInitLine(PanelDefinition def, String trimmed, int lineNumber) {
        if (trimmed.isEmpty() || trimmed.startsWith("*")) {
            return;
        }
        Matcher m = INIT_LINE.matcher(trimmed);
        if (!m.matches()) {
            throw new PanelParseException(lineNumber, "Expected &FIELD = value in )INIT, got \"" + trimmed + "\"");
        }
        String field = m.group(1).toUpperCase(Locale.ROOT);
        String value = unquote(m.group(2).trim());
        def.addInit(new InitAssignment(field, value));
    }

    private static void parseProcLine(PanelDefinition def, String trimmed, int lineNumber) {
        if (trimmed.isEmpty() || trimmed.startsWith("*")) {
            return;
        }
        if (!trimmed.toUpperCase(Locale.ROOT).startsWith("VER")) {
            throw new PanelParseException(lineNumber,
                    "Only VER(...) statements are supported in )PROC, got \"" + trimmed + "\"");
        }
        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');
        if (open < 0 || close < 0 || close < open) {
            throw new PanelParseException(lineNumber, "Malformed VER statement: \"" + trimmed + "\"");
        }
        String[] parts = trimmed.substring(open + 1, close).split(",");
        if (parts.length < 2 || !parts[0].trim().startsWith("&")) {
            throw new PanelParseException(lineNumber, "VER needs (&FIELD,RULE[,args...]), got \"" + trimmed + "\"");
        }
        String field = parts[0].trim().substring(1).toUpperCase(Locale.ROOT);
        String kind = parts[1].trim().toUpperCase(Locale.ROOT);
        List<String> args = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            args.add(parts[i].trim());
        }
        if (!kind.equals(VerifyRule.NONBLANK) && !kind.equals(VerifyRule.NUM) && !kind.equals(VerifyRule.RANGE)) {
            throw new PanelParseException(lineNumber,
                    "Unknown VER rule \"" + kind + "\" (expected NONBLANK, NUM or RANGE)");
        }
        if (kind.equals(VerifyRule.RANGE) && args.size() != 2) {
            throw new PanelParseException(lineNumber, "VER(...,RANGE,min,max) needs two arguments");
        }
        def.addVerify(new VerifyRule(field, kind, args));
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
