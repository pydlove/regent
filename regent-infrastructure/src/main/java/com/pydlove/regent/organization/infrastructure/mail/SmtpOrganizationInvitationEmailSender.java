package com.pydlove.regent.organization.infrastructure.mail;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.organization.domain.port.OrganizationInvitationEmailSender;
import com.pydlove.regent.shared.domain.DomainException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** SMTP 组织邀请邮件。 */
@Component
public class SmtpOrganizationInvitationEmailSender implements OrganizationInvitationEmailSender {

    private static final Logger log =
            LoggerFactory.getLogger(SmtpOrganizationInvitationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpOrganizationInvitationEmailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendInvitation(Email recipient,
                               String organizationName,
                               String invitationUrl,
                               Duration ttl) {
        if (from == null || from.isBlank()) {
            throw new DomainException("邮件服务未配置，请设置 SPRING_MAIL_USERNAME 和 SPRING_MAIL_PASSWORD");
        }
        String name = organizationName == null || organizationName.isBlank()
                ? "Regent 组织"
                : organizationName.trim();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient.value());
            helper.setSubject("Regent · 邀请你加入「" + name + "」");
            helper.setText(plainText(name, invitationUrl, ttl), html(name, invitationUrl, ttl));
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            log.warn("组织邀请邮件发送失败 email={}：{}", recipient.value(), exception.getMessage());
            throw new DomainException("邀请邮件发送失败，请稍后重试或检查 SMTP 配置", exception);
        }
    }

    private static String plainText(String organizationName, String invitationUrl, Duration ttl) {
        return "你被邀请加入 Regent 组织「" + organizationName + "」。\n\n"
                + "打开下面的链接接受邀请：\n"
                + invitationUrl + "\n\n"
                + "邀请链接 " + Math.max(1, ttl.toDays()) + " 天内有效，仅可使用一次。\n"
                + "如果不是你本人操作，请忽略本邮件。";
    }

    private static String html(String organizationName, String invitationUrl, Duration ttl) {
        String safeName = escapeHtml(organizationName);
        String safeUrl = escapeHtml(invitationUrl);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f3f7fd;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:#1d1d1f;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                    <tr><td align="center" style="padding:40px 16px;">
                      <table role="presentation" width="520" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:520px;background:#ffffff;border:1px solid #dce5f2;border-radius:16px;overflow:hidden;">
                        <tr><td style="padding:28px 32px 0;">
                          <div style="font-size:20px;font-weight:700;">Regent</div>
                          <div style="margin-top:4px;font-size:12px;color:#6e6e73;">AI 任务执行平台</div>
                        </td></tr>
                        <tr><td style="padding:28px 32px 8px;font-size:15px;line-height:1.7;color:#3a3a3c;">
                          你被邀请加入组织「%s」，点击下面的按钮接受邀请。
                        </td></tr>
                        <tr><td style="padding:12px 32px 24px;">
                          <a href="%s" style="display:inline-block;padding:12px 20px;border-radius:8px;background:#0071e3;color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;">接受组织邀请</a>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px;font-size:13px;line-height:1.7;color:#6e6e73;">
                          邀请链接 %d 天内有效，仅可使用一次。请勿转发给他人。
                        </td></tr>
                        <tr><td style="padding:18px 32px;border-top:1px solid #edf1f7;font-size:12px;line-height:1.7;color:#86868b;">
                          如果不是你本人操作，请忽略本邮件。<br>
                          <a href="%s" style="color:#0071e3;text-decoration:none;">打开 Regent</a>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(safeName, safeUrl, Math.max(1, ttl.toDays()), safeUrl);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
