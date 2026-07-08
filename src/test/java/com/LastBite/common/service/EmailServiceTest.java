package com.LastBite.common.service;

import com.LastBite.common.config.MailProperties;
import com.LastBite.common.email.EmailDeliveryLog;
import com.LastBite.common.email.EmailDeliveryLogRepository;
import com.LastBite.common.email.EmailDeliveryStatus;
import com.LastBite.common.email.EmailMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailDeliveryLogRepository deliveryLogRepository = mock(EmailDeliveryLogRepository.class);
    private final MailProperties mailProperties = new MailProperties();
    private final EmailService emailService = new EmailService(
            mailSender,
            mailProperties,
            deliveryLogRepository,
            new ObjectMapper());

    @Test
    void otpEmailUsesConfiguredSenderCleanSubjectPlainTextAndIdempotencyHeader() throws Exception {
        mailProperties.setFromName("LastBite");
        mailProperties.setFromAddress("verify@mail.lastbite.vn");
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        when(deliveryLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendOtpEmail("Customer@Example.com", "Minh", "123456");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sent = messageCaptor.getValue();

        assertEquals("Ma xac minh LastBite", sent.getSubject());
        assertFalse(sent.getSubject().contains("123456"));
        assertTrue(sent.getFrom()[0].toString().contains("verify@mail.lastbite.vn"));
        assertNotNull(sent.getHeader("X-Entity-Ref-ID", null));
        assertNotNull(sent.getHeader("Resend-Idempotency-Key", null));

        String content = collectText(sent.getContent());
        assertTrue(content.contains("Ma xac minh email LastBite cua ban la: 123456"));
        assertTrue(content.contains("font-size:32px"));

        ArgumentCaptor<EmailDeliveryLog> logCaptor = ArgumentCaptor.forClass(EmailDeliveryLog.class);
        verify(deliveryLogRepository, org.mockito.Mockito.atLeastOnce()).save(logCaptor.capture());
        EmailDeliveryLog finalLog = logCaptor.getAllValues().getLast();
        assertEquals(EmailMessageType.OTP, finalLog.getMessageType());
        assertEquals(EmailDeliveryStatus.SENT, finalLog.getStatus());
        assertEquals("customer@example.com", finalLog.getRecipientEmail());
        assertEquals(1, finalLog.getAttempts());
    }

    private String collectText(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                builder.append(collectText(part.getContent()));
            }
            return builder.toString();
        }
        return "";
    }
}
