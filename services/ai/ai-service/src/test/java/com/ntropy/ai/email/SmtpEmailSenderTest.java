package com.ntropy.ai.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.mail.Session;

import org.junit.jupiter.api.Test;

import com.ntropy.common.exception.ServiceException;

class SmtpEmailSenderTest {

    @Test
    void missingCredentialsFailBeforeNetworkAccess() {
        MailProperties properties = new MailProperties("smtp.gmail.com", 587, "", "", "", "Ntropy");
        SmtpEmailSender sender = new SmtpEmailSender(properties);

        ServiceException exception = assertThrows(ServiceException.class, () -> sender.send(
                new EmailMessage("recipient.invalid", "subject", "body", "report.pdf", "application/pdf",
                        new byte[] {1})
        ));

        assertEquals(500, exception.getStatusCode());
    }

    @Test
    void requiresStartTlsAndVerifiesTheServerIdentity() throws Exception {
        MailProperties properties = new MailProperties(
                "smtp.invalid", 587, "configured-user", "configured-secret", "sender@invalid.test", "Ntropy"
        );
        SmtpEmailSender sender = new SmtpEmailSender(properties);

        Session session = sender.createSession();

        assertTrue(Boolean.parseBoolean(session.getProperty("mail.smtp.starttls.enable")));
        assertTrue(Boolean.parseBoolean(session.getProperty("mail.smtp.starttls.required")));
        assertTrue(Boolean.parseBoolean(session.getProperty("mail.smtp.ssl.checkserveridentity")));
    }
}
