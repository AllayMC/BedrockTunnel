package org.allaymc.bedrocktunnel.ui;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.io.Serial;

final class ConsolePanel extends JTextPane {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int MAX_DOCUMENT_CHARS = 200_000;

    private Color currentColor = ANSIColor.RESET.color();
    private int currentLength;
    private String remaining = "";

    private void append(Color color, String text) {
        SimpleAttributeSet attribute = new SimpleAttributeSet();
        StyleConstants.setForeground(attribute, color);
        StyleConstants.setBold(attribute, ANSIColor.isBoldColor(color));

        int length = getDocument().getLength();
        try {
            if (text.contains("\r")) {
                if (text.contains("\n")) {
                    getDocument().insertString(length, text, attribute);
                    trimDocument();
                    currentLength = 0;
                    return;
                }

                int start = Math.max(0, length - currentLength);
                getDocument().remove(start, Math.min(currentLength, length));
                getDocument().insertString(start, text, attribute);
                trimDocument();
                currentLength = text.length();
                return;
            }

            currentLength += text.length();
            getDocument().insertString(length, text, attribute);
            trimDocument();
        } catch (BadLocationException ignored) {
        }
    }

    private void trimDocument() throws BadLocationException {
        int excess = getDocument().getLength() - MAX_DOCUMENT_CHARS;
        if (excess > 0) {
            getDocument().remove(0, excess);
        }
    }

    public void appendANSI(String text) {
        int position = 0;
        String input = remaining + text;
        remaining = "";

        if (input.isEmpty()) {
            return;
        }

        int escapeIndex = input.indexOf("\u001B");
        if (escapeIndex < 0) {
            append(currentColor, input);
            return;
        }

        if (escapeIndex > 0) {
            append(currentColor, input.substring(0, escapeIndex));
            position = escapeIndex;
        }

        while (position < input.length()) {
            int end = input.indexOf('m', position);
            if (end < 0) {
                remaining = input.substring(position);
                return;
            }

            currentColor = ANSIColor.fromANSI(input.substring(position, end + 1)).color();
            position = end + 1;

            int nextEscape = input.indexOf("\u001B", position);
            if (nextEscape < 0) {
                append(currentColor, input.substring(position));
                return;
            }

            append(currentColor, input.substring(position, nextEscape));
            position = nextEscape;
        }
    }
}
