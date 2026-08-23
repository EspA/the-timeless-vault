package com.thetimelessvault.settings;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.opportunities.NotificationEmail;
import com.thetimelessvault.opportunities.NotificationEmailRenderer;
import com.thetimelessvault.opportunities.BuyingOpportunity;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationMailer {

    public static final String TO_KEY = "alert.to_email";

    private static final Logger log = LoggerFactory.getLogger(NotificationMailer.class);

    private final AppSettingRepository settings;
    private final AppProperties properties;
    private final JavaMailSender mailSender;
    private final String mailHost;
    private final int mailPort;
    private final String mailUsername;
    private final String mailPassword;

    public NotificationMailer(
            AppSettingRepository settings,
            AppProperties properties,
            JavaMailSender mailSender,
            @Value("${spring.mail.host:localhost}") String mailHost,
            @Value("${spring.mail.port:1025}") int mailPort,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword
    ) {
        this.settings = settings;
        this.properties = properties;
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
    }

    public String recipient() {
        return settings.findById(TO_KEY)
                .map(AppSetting::getValue)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> properties.getAlertToEmail() == null ? "" : properties.getAlertToEmail().trim());
    }

    public String saveRecipient(String email) {
        String value = email == null ? "" : email.trim();
        if (!value.isBlank()) {
            validateAddress(value);
        }
        settings.save(new AppSetting(TO_KEY, value));
        return value.isBlank() ? recipient() : value;
    }

    public Map<String, Object> status() {
        boolean catcher = usesCatcher();
        return Map.of(
                "notificationTo", recipient(),
                "alertTo", recipient(),
                "mailFrom", properties.getMailFrom() == null ? "" : properties.getMailFrom(),
                "mailHost", mailHost,
                "mailCatcher", catcher,
                "mailDeliversToInbox", !catcher
        );
    }

    public boolean sendQuietly(String subject, String body) {
        return sendQuietly(subject, body, null);
    }

    public boolean sendQuietly(String subject, String textBody, String htmlBody) {
        try {
            send(subject, textBody, htmlBody);
            return true;
        } catch (Exception e) {
            log.warn("Could not send notification email", e);
            return false;
        }
    }

    public void send(String subject, String body) {
        send(subject, body, null);
    }

    public void send(String subject, String textBody, String htmlBody) {
        String to = recipient();
        if (to.isBlank()) {
            throw ApiException.badRequest("Set a notification email in Settings first.");
        }
        validateAddress(to);
        sendSmtp(to, subject, textBody, htmlBody);
    }

    public void sendTest() {
        NotificationEmail sample = new NotificationEmail(
                BuyingOpportunity.TYPE_BUYING_OPPORTUNITY,
                "75017-1",
                "Duel on Geonosis",
                null,
                "$315.00",
                "-12%",
                properties.getBaseUrl(),
                null,
                "EBAY",
                "brickshop",
                "Feedback 1,842 / 99.8%",
                Instant.now(),
                null
        );
        send(
                "Test notification from The Timeless Vault",
                "If you received this, notification email is working.\n\nRecipient: " + recipient()
                        + "\n\n" + NotificationEmailRenderer.text(sample),
                NotificationEmailRenderer.html(sample, properties.getBaseUrl())
        );
    }

    boolean usesCatcher() {
        if (hasSmtpCredentials()) {
            return false;
        }
        String host = mailHost == null ? "" : mailHost.trim().toLowerCase(Locale.ROOT);
        return host.isBlank()
                || host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.contains("mailpit")
                || mailPort == 1025;
    }

    boolean deliversToInbox() {
        return !usesCatcher();
    }

    private boolean hasSmtpCredentials() {
        return mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    private void sendSmtp(String to, String subject, String textBody, String htmlBody) {
        String from = fromAddress();
        if (htmlBody == null || htmlBody.isBlank()) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(textBody);
            mailSender.send(message);
            return;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody == null ? "" : textBody, htmlBody);
            mailSender.send(mime);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not send email");
        }
    }

    private String fromAddress() {
        String from = properties.getMailFrom();
        if (from == null || from.isBlank()) {
            return hasSmtpCredentials() ? mailUsername : "vault@thetimelessvault.com";
        }
        return from;
    }

    static void validateAddress(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1 || !email.substring(at).contains(".")) {
            throw ApiException.badRequest("Enter a valid email address.");
        }
    }
}
