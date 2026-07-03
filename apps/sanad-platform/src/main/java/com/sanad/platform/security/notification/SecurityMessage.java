package com.sanad.platform.security.notification;

public final class SecurityMessage {
    private final String destination;
    private final String subject;
    private final String textBody;
    private final String htmlBody;

    public SecurityMessage(String destination, String subject, String textBody, String htmlBody) {
        this.destination = destination;
        this.subject = subject;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
    }

    public String getDestination() { return destination; }
    public String getSubject() { return subject; }
    public String getTextBody() { return textBody; }
    public String getHtmlBody() { return htmlBody; }
}
