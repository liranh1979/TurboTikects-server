package com.turbotikects.turbotikectsserver.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class SignatureStripperService {

    private static final Pattern SIG_DELIMITER = Pattern.compile("^--\\s*$");
    private static final Pattern SENT_FROM = Pattern.compile(
            "(?i)(sent from my (iphone|android|ipad|samsung|outlook|mail for windows))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDERSCORE_LINE = Pattern.compile("^_{5,}$");
    private static final Pattern DASH_LINE = Pattern.compile("^-{5,}$");

    public String stripFromHtml(String html) {
        if (html == null || html.isBlank()) return html;

        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);

        // Remove Gmail quote blocks
        Elements gmailQuote = doc.select("div.gmail_quote, blockquote[type=cite]");
        gmailQuote.remove();

        // Walk all text nodes; truncate at RFC 3676 "-- " delimiter
        truncateAtSignatureDelimiter(doc.body());

        return doc.body().html();
    }

    public String stripFromText(String text) {
        if (text == null || text.isBlank()) return text;

        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (SIG_DELIMITER.matcher(trimmed).matches()) break;
            if (SENT_FROM.matcher(trimmed).find()) break;
            if (UNDERSCORE_LINE.matcher(trimmed).matches()) break;
            if (DASH_LINE.matcher(trimmed).matches()) break;
            result.append(line).append("\n");
        }
        return result.toString().stripTrailing();
    }

    private void truncateAtSignatureDelimiter(Element element) {
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode tn) {
                String text = tn.getWholeText();
                int idx = findDelimiterIndex(text);
                if (idx >= 0) {
                    // Remove this node and everything after it
                    tn.text(text.substring(0, idx));
                    removeFollowingSiblings(tn);
                    return;
                }
            } else if (node instanceof Element el) {
                String text = el.text().trim();
                if (SIG_DELIMITER.matcher(text).matches()
                        || SENT_FROM.matcher(text).find()
                        || UNDERSCORE_LINE.matcher(text).matches()) {
                    removeFromNodeOnward(el);
                    return;
                }
                truncateAtSignatureDelimiter(el);
            }
        }
    }

    private int findDelimiterIndex(String text) {
        for (String line : text.split("\n")) {
            if (SIG_DELIMITER.matcher(line.trim()).matches()) {
                return text.indexOf(line);
            }
        }
        return -1;
    }

    private void removeFollowingSiblings(Node node) {
        while (node.nextSibling() != null) {
            node.nextSibling().remove();
        }
        if (node.parent() != null) {
            removeFollowingSiblings(node.parent());
        }
    }

    private void removeFromNodeOnward(Element element) {
        while (element.nextElementSibling() != null) {
            element.nextElementSibling().remove();
        }
        element.remove();
    }
}
