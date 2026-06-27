package com.sanad.platform.security.notification;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpSecurityNotificationGatewayTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsMultipartMessageUsingConfiguredSender() throws Exception {
        SecurityNotificationProperties properties = new SecurityNotificationProperties();
        properties.setFromAddress("snad.ai.app@gmail.com");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpSecurityNotificationGateway gateway =
                new SmtpSecurityNotificationGateway(mailSender, properties);

        gateway.deliver(new SecurityMessage(
                "pilot-user@example.invalid",
                "SNAD recovery",
                "Open the secure one-time link.",
                "<p>Open the secure one-time link.</p>"));

        verify(mailSender).send(mimeMessage);
        assertEquals("SNAD recovery", mimeMessage.getSubject());
        assertEquals("snad.ai.app@gmail.com", mimeMessage.getFrom()[0].toString());
        assertEquals(
                "pilot-user@example.invalid",
                mimeMessage.getRecipients(Message.RecipientType.TO)[0].toString());
    }

    @Test
    void failsClosedWhenSenderIsMissing() {
        SecurityNotificationProperties properties = new SecurityNotificationProperties();
        properties.setFromAddress(" ");
        SmtpSecurityNotificationGateway gateway =
                new SmtpSecurityNotificationGateway(mailSender, properties);

        assertThrows(IllegalStateException.class, () -> gateway.deliver(
                new SecurityMessage(
                        "pilot-user@example.invalid",
                        "SNAD recovery",
                        "text",
                        "<p>html</p>")));

        verify(mailSender, never()).createMimeMessage();
    }
}
