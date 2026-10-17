package com.pydlove.regent.identity.infrastructure.mail;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.port.VerificationEmailSender;
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

/** SMTP 验证码邮件。内容与 Regent 的浅色 macOS 视觉保持一致。 */
@Component
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String homeUrl;

    public SmtpVerificationEmailSender(JavaMailSender mailSender,
                                       @Value("${spring.mail.username:}") String from,
                                       @Value("${regent.auth.email-code.home-url:http://127.0.0.1:12580}") String homeUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.homeUrl = homeUrl;
    }

    @Override
    public void sendRegistrationCode(Email email, String code, Duration ttl) {
        if (from == null || from.isBlank()) {
            throw new DomainException("邮件服务未配置，请设置 SPRING_MAIL_USERNAME 和 SPRING_MAIL_PASSWORD");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.value());
            helper.setSubject("Regent AI 任务执行平台 · 邮箱验证码");
            helper.setText(plainText(code, ttl), html(code, ttl));
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            log.warn("验证码邮件发送失败 email={}：{}", email.value(), exception.getMessage());
            throw new DomainException("验证码邮件发送失败，请稍后重试或检查 SMTP 配置", exception);
        }
    }

    private static String plainText(String code, Duration ttl) {
        return "你正在注册 Regent AI 任务执行平台。\n\n"
                + "邮箱验证码：" + code + "\n"
                + "验证码 " + Math.max(1, ttl.toMinutes()) + " 分钟内有效，仅可使用一次。\n\n"
                + "如果不是你本人操作，请忽略本邮件。";
    }

    private String html(String code, Duration ttl) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f3f7fd;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:#1d1d1f;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                    <tr><td align="center" style="padding:40px 16px;">
                      <table role="presentation" width="520" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:520px;background:#ffffff;border:1px solid #dce5f2;border-radius:16px;overflow:hidden;">
                        <tr><td style="padding:28px 32px 0;">
                          <div style="font-size:20px;font-weight:700;">Regent</div>
                          <div style="margin-top:4px;font-size:12px;color:#6e6e73;">AI 任务执行平台</div>
                        </td></tr>
                        <tr><td style="padding:28px 32px 8px;font-size:15px;line-height:1.7;color:#3a3a3c;">
                          你正在注册 Regent 账号，请输入下面的邮箱验证码：
                        </td></tr>
                        <tr><td style="padding:12px 32px 24px;">
                          <div style="padding:22px;background:#eef5ff;border-radius:12px;text-align:center;font-size:40px;font-weight:700;letter-spacing:10px;color:#0071e3;">{{code}}</div>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px;font-size:13px;line-height:1.7;color:#6e6e73;">
                          验证码 {{ttl}} 分钟内有效，仅可使用一次。请勿转发给他人。
                        </td></tr>
                        <tr><td style="padding:18px 32px;border-top:1px solid #edf1f7;font-size:12px;line-height:1.7;color:#86868b;">
                          如果不是你本人操作，请忽略本邮件。<br>
                          <a href="{{url}}" style="color:#0071e3;text-decoration:none;">打开 Regent AI 任务执行平台</a>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{code}}", code)
                .replace("{{ttl}}", String.valueOf(Math.max(1, ttl.toMinutes())))
                .replace("{{url}}", homeUrl);
    }
}
