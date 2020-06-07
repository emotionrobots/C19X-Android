package org.c19x.old.data;

/**
 * Personal message with optional link to browser.
 */
public class PersonalMessage {
    private final String text;
    private final int color;
    private final String url;

    public PersonalMessage(String text, int color, String url) {
        this.text = text;
        this.color = color;
        this.url = url;
    }

    public String getText() {
        return text;
    }

    public int getColor() {
        return color;
    }

    public String getUrl() {
        return url;
    }
}
