package com.thetimelessvault.settings;

import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.config.AppProperties;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMailerTest {

    @Mock
    AppSettingRepository settings;
    @Mock
    JavaMailSender mailSender;

    AppProperties properties;
    NotificationMailer mailer;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.setAlertToEmail("from-env@example.com");
        mailer = new NotificationMailer(settings, properties, mailSender, "localhost", 1025, "", "");
    }

    @Test
    void usesEnvWhenSettingMissing() {
        when(settings.findById(NotificationMailer.TO_KEY)).thenReturn(Optional.empty());
        assertEquals("from-env@example.com", mailer.recipient());
    }

    @Test
    void prefersSavedSetting() {
        when(settings.findById(NotificationMailer.TO_KEY))
                .thenReturn(Optional.of(new AppSetting(NotificationMailer.TO_KEY, "saved@example.com")));
        assertEquals("saved@example.com", mailer.recipient());
    }

    @Test
    void treatsMailpitAsCatcherUntilSmtpCredentialsExist() {
        assertTrue(mailer.usesCatcher());
        assertFalse(mailer.deliversToInbox());
        NotificationMailer gmail = new NotificationMailer(
                settings, properties, mailSender, "smtp.gmail.com", 587, "you@gmail.com", "app-password");
        assertFalse(gmail.usesCatcher());
        assertTrue(gmail.deliversToInbox());
    }

    @Test
    void sendsOnlyOverSmtp() {
        when(settings.findById(NotificationMailer.TO_KEY)).thenReturn(Optional.empty());
        mailer.send("New listing", "Spider Droid appeared on eBay.");
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertEquals("from-env@example.com", sent.getValue().getTo()[0]);
        assertEquals("New listing", sent.getValue().getSubject());
        assertEquals("Spider Droid appeared on eBay.", sent.getValue().getText());
    }

    @Test
    void rejectsInvalidAddress() {
        assertThrows(ApiException.class, () -> NotificationMailer.validateAddress("not-an-email"));
    }
}
