package me.pipi.codexmeter;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the GitHub release-note Markdown subset used by Codex Meter into HTML that
 * {@code android.text.Html} can render inside TextViews.
 */
public final class ReleaseNotesMarkdown {
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    private static final Pattern ITALIC = Pattern.compile(
            "(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)|_(.+?)_");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");
    private static final Pattern AUTOLINK = Pattern.compile(
            "(?<![\"'>])(https?://[\\w.-]+(?:/[\\w\\-./?%&=+#~:@,]*)?)");
    private static final int MAX_HEADING_LEVEL = 6;

    private ReleaseNotesMarkdown() {
    }

    public static String toHtml(String markdown) {
        String source = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        source = repairRedactedRepositoryLinks(source);
        source = HTML_COMMENT.matcher(source).replaceAll("");
        source = source.trim();
        if (source.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder();
        String[] lines = source.split("\n", -1);
        int index = 0;
        while (index < lines.length) {
            String trimmed = lines[index].trim();
            int headingLevel = headingLevel(trimmed);
            if (trimmed.isEmpty()) {
                index++;
            } else if (isHorizontalRule(trimmed)) {
                html.append("<hr>");
                index++;
            } else if (headingLevel > 0) {
                String text = trimmed.substring(headingLevel).trim();
                html.append("<p><b>").append(inline(text)).append("</b></p>");
                index++;
            } else if (isUnorderedItem(trimmed)) {
                index = appendList(html, lines, index, false);
            } else if (isOrderedItem(trimmed)) {
                index = appendList(html, lines, index, true);
            } else {
                index = appendParagraph(html, lines, index);
            }
        }
        return html.toString();
    }

    /**
     * Older published release notes accidentally contain a literal
     * {@code [REDACTED]} owner placeholder copied from agent tooling output.
     * Rewrite those URLs to the canonical public repository before rendering.
     */
    static String repairRedactedRepositoryLinks(String source) {
        if (source == null) {
            return "";
        }
        if (!source.contains("[REDACTED]")) {
            return source;
        }
        String repository = GitHubReleaseSource.REPOSITORY_URL;
        return source
                .replace("https://github.com/[REDACTED]/Codex-Meter", repository)
                .replace("http://github.com/[REDACTED]/Codex-Meter", repository);
    }

    /**
     * Appends the run of list items starting at {@code start} as one {@code <ul>} or
     * {@code <ol>} and returns the index of the first line after the list.
     */
    private static int appendList(StringBuilder html, String[] lines, int start,
            boolean ordered) {
        String tag = ordered ? "ol" : "ul";
        html.append('<').append(tag).append('>');
        int index = start;
        while (index < lines.length) {
            String item = lines[index].trim();
            if (ordered ? !isOrderedItem(item) : !isUnorderedItem(item)) {
                break;
            }
            String text = ordered ? stripOrderedMarker(item) : stripUnorderedMarker(item);
            html.append("<li>").append(inline(text)).append("</li>");
            index++;
        }
        html.append("</").append(tag).append('>');
        return index;
    }

    /**
     * Appends consecutive plain lines starting at {@code start} as one paragraph joined by line
     * breaks and returns the index of the first line after it.
     */
    private static int appendParagraph(StringBuilder html, String[] lines, int start) {
        html.append("<p>");
        int index = start;
        while (index < lines.length) {
            String line = lines[index].trim();
            if (startsBlock(line)) {
                break;
            }
            if (index > start) {
                html.append("<br>");
            }
            html.append(inline(line));
            index++;
        }
        html.append("</p>");
        return index;
    }

    private static boolean startsBlock(String trimmed) {
        return trimmed.isEmpty()
                || isHorizontalRule(trimmed)
                || headingLevel(trimmed) > 0
                || isUnorderedItem(trimmed)
                || isOrderedItem(trimmed);
    }

    private static String inline(String text) {
        String escaped = escapeHtml(text == null ? "" : text);
        escaped = replaceAll(LINK, escaped, matcher -> {
            String label = matcher.group(1);
            String url = unescapeBasicEntities(matcher.group(2));
            if (!isSafeUrl(url)) {
                return matcher.group(0);
            }
            return "<a href=\"" + escapeAttribute(url) + "\">" + label + "</a>";
        });
        escaped = replaceAll(INLINE_CODE, escaped, matcher ->
                "<code>" + matcher.group(1) + "</code>");
        escaped = replaceAll(BOLD, escaped, matcher ->
                "<b>" + firstPresentGroup(matcher) + "</b>");
        escaped = replaceAll(ITALIC, escaped, matcher ->
                "<i>" + firstPresentGroup(matcher) + "</i>");
        escaped = replaceAll(AUTOLINK, escaped, matcher -> {
            String displayed = matcher.group(1);
            String url = unescapeBasicEntities(displayed);
            if (!isSafeUrl(url) || urlContainsHtml(url)) {
                return displayed;
            }
            return "<a href=\"" + escapeAttribute(url) + "\">" + displayed + "</a>";
        });
        return escaped;
    }

    /** Returns group 1, or group 2 for the alternative spelling (e.g. {@code __bold__}). */
    private static String firstPresentGroup(Matcher matcher) {
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static String unescapeBasicEntities(String value) {
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String replaceAll(Pattern pattern, String input,
            Function<Matcher, String> replacer) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacer.apply(matcher)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static boolean isSafeUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static boolean urlContainsHtml(String url) {
        return url.indexOf('<') >= 0 || url.indexOf('>') >= 0 || url.indexOf('"') >= 0;
    }

    /** Returns the ATX heading level of {@code trimmed}, or 0 when it is not a heading. */
    private static int headingLevel(String trimmed) {
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > MAX_HEADING_LEVEL || level >= trimmed.length()
                || trimmed.charAt(level) != ' ') {
            return 0;
        }
        return level;
    }

    private static boolean isHorizontalRule(String trimmed) {
        return "---".equals(trimmed) || "***".equals(trimmed) || "___".equals(trimmed)
                || "----".equals(trimmed) || "*****".equals(trimmed);
    }

    private static boolean isUnorderedItem(String trimmed) {
        return trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ");
    }

    private static String stripUnorderedMarker(String trimmed) {
        return trimmed.substring(2).trim();
    }

    private static boolean isOrderedItem(String trimmed) {
        int digits = leadingDigitCount(trimmed);
        return digits > 0 && digits + 1 < trimmed.length()
                && trimmed.charAt(digits) == '.'
                && trimmed.charAt(digits + 1) == ' ';
    }

    private static String stripOrderedMarker(String trimmed) {
        // Skip the digits plus the ". " that follows them.
        return trimmed.substring(leadingDigitCount(trimmed) + 2).trim();
    }

    private static int leadingDigitCount(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) >= '0'
                && value.charAt(count) <= '9') {
            count++;
        }
        return count;
    }

    private static String escapeHtml(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&':
                    builder.append("&amp;");
                    break;
                case '<':
                    builder.append("&lt;");
                    break;
                case '>':
                    builder.append("&gt;");
                    break;
                case '"':
                    builder.append("&quot;");
                    break;
                default:
                    builder.append(character);
                    break;
            }
        }
        return builder.toString();
    }

    private static String escapeAttribute(String value) {
        return escapeHtml(value).replace("'", "&#39;");
    }
}
