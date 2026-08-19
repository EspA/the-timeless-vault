package com.thetimelessvault.settings;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class AlertMailer {

    public static final String TO_KEY = "alert.to_email";

    private static final Logger log = LoggerFactory.getLogger(AlertMailer.class);

    private final AppSettingRepository settings;
    private final AppProperties properties;
    private final JavaMailSender mailSender;
    private final String mailHost;
    private final int mailPort;
    private final String mailUsername;
    private final String mailPassword;

    public AlertMailer(
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
                "alertTo", recipient(),
                "mailFrom", properties.getMailFrom() == null ? "" : properties.getMailFrom(),
                "mailHost", mailHost,
                "mailCatcher", catcher,
                "mailDeliversToInbox", !catcher
        );
    }

    public boolean sendQuietly(String subject, String body) {
        try {
            send(subject, body);
            return true;
        } catch (Exception e) {
            log.warn("Could not send alert email", e);
            return false;
        }
    }

    public void send(String subject, String body) {
        String to = recipient();
        if (to.isBlank()) {
            throw ApiException.badRequest("Set an alert email in Settings first.");
        }
        validateAddress(to);
        sendSmtp(to, subject, body);
    }

    public void sendTest() {
        send(
                "Test alert from The Timeless Vault",
                "If you received this, alert email is working.\n\nRecipient: " + recipient()
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

    private void sendSmtp(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        String from = properties.getMailFrom();
        if (from == null || from.isBlank()) {
            from = hasSmtpCredentials() ? mailUsername : "vault@thetimelessvault.com";
        }
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    static void validateAddress(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1 || !email.substring(at).contains(".")) {
            throw ApiException.badRequest("Enter a valid email address.");
        }
    }
}
