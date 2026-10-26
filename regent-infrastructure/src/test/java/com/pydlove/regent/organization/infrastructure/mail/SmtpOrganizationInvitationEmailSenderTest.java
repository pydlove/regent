package com.pydlove.regent.organization.infrastructure.mail;

import com.pydlove.regent.identity.domain.model.Email;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpOrganizationInvitationEmailSenderTest {

    @Test
    @DisplayName("邀请邮件包含组织名称和公开邀请链接")
    void sendsInvitationLink() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        String invitationUrl = "https://ichuang.top/regent/invite/test-token";

        new SmtpOrganizationInvitationEmailSender(mailSender, "noreply@example.com")
                .sendInvitation(
                        Email.of("invitee@example.com"),
                        "Regent 研发中心",
                        invitationUrl,
                        Duration.ofDays(7));

        verify(mailSender).send(message);
        assertTrue(message.getSubject().contains("Regent 研发中心"));
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        assertTrue(raw.toString(StandardCharsets.UTF_8).contains(invitationUrl));
    }
}
