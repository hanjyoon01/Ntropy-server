package com.ntropy.ai.email;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.springframework.stereotype.Component;

import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Gmail SMTP(STARTTLS) 기반 EmailSender 구현. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final MailProperties properties;

    @Override
    public void send(EmailMessage command) {
        if (!properties.isComplete()) {
            throw new ServiceException(AiReportErrorCode.EMAIL_CONFIGURATION_INVALID);
        }

        try {
            MimeMessage message = new MimeMessage(createSession());
            message.setFrom(new InternetAddress(properties.getFrom(), properties.getFromName(), "UTF-8"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(command.recipient()));
            message.setSubject(command.subject(), "UTF-8");

            MimeBodyPart body = new MimeBodyPart();
            body.setText(command.body(), "UTF-8");

            MimeBodyPart attachment = new MimeBodyPart();
            attachment.setDataHandler(new DataHandler(
                    new ByteArrayDataSource(command.attachment(), command.attachmentContentType())
            ));
            attachment.setFileName(command.attachmentName());

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(body);
            multipart.addBodyPart(attachment);
            message.setContent(multipart);

            Transport.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            log.error("AI 리포트 SMTP 발송 실패. errorType={}", exception.getClass().getSimpleName());
            throw new ServiceException(AiReportErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    Session createSession() {
        Properties sessionProperties = new Properties();
        sessionProperties.put("mail.smtp.host", properties.getHost());
        sessionProperties.put("mail.smtp.port", Integer.toString(properties.getPort()));
        sessionProperties.put("mail.smtp.auth", "true");
        sessionProperties.put("mail.smtp.starttls.enable", "true");
        sessionProperties.put("mail.smtp.starttls.required", "true");
        sessionProperties.put("mail.smtp.ssl.checkserveridentity", "true");
        sessionProperties.put("mail.smtp.connectiontimeout", "5000");
        sessionProperties.put("mail.smtp.timeout", "10000");
        sessionProperties.put("mail.smtp.writetimeout", "10000");

        return Session.getInstance(sessionProperties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(properties.getUsername(), properties.getPassword());
            }
        });
    }
}
