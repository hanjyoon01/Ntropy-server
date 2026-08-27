package com.ntropy.ai.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.ai.email.EmailMessage;
import com.ntropy.ai.email.EmailSender;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.ai.port.payment.SubscriptionPort;
import com.ntropy.ai.port.user.AiUser;
import com.ntropy.ai.port.user.UserPort;
import com.ntropy.ai.api.client.AiReportQueryClient;
import com.ntropy.common.domain.Feature;
import com.ntropy.ai.api.dto.AiReportEmailDeliverySummary;
import com.ntropy.ai.api.dto.AiReportSummary;
import com.ntropy.common.exception.ServiceException;

class AiReportEmailDeliveryServiceTest {

    private SubscriptionPort subscriptionClient;
    private UserPort userClient;
    private AiReportQueryClient reportClient;
    private AiReportPdfService pdfService;
    private EmailSender emailSender;
    private AiReportEmailDeliveryService service;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionClient = mock(SubscriptionPort.class);
        userClient = mock(UserPort.class);
        reportClient = mock(AiReportQueryClient.class);
        pdfService = mock(AiReportPdfService.class);
        emailSender = mock(EmailSender.class);
        service = new AiReportEmailDeliveryService(
                subscriptionClient, userClient, reportClient, pdfService, emailSender
        );

        ObjectMapper mapper = new ObjectMapper();
        when(subscriptionClient.supportsFeature(7L, Feature.AI_REPORT)).thenReturn(true);
        when(userClient.findUser(7L)).thenReturn(
                new AiUser(7L, "billing.owner@example.com")
        );
        when(reportClient.findByUserIdAndYearMonth(7L, "2026-05")).thenReturn(
                new AiReportSummary(31L, 7L, "2026-05", mapper.readTree("{\"total_income\":10}"),
                        mapper.readTree("{}"), LocalDateTime.of(2026, 6, 1, 1, 2))
        );
        when(pdfService.generate(any())).thenReturn(new byte[] {1, 2, 3});
    }

    @Test
    void deliversOnlyToServerResolvedEmailAndReturnsMaskedRecipient() {
        AiReportEmailDeliverySummary result = service.deliver(7L, "2026-05");

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        EmailMessage message = captor.getValue();
        assertEquals("billing.owner@example.com", message.recipient());
        assertEquals("[Ntropy] 2026년 5월 AI 재무 리포트", message.subject());
        assertEquals("Ntropy_AI_Report_2026-05.pdf", message.attachmentName());
        assertArrayEquals(new byte[] {1, 2, 3}, message.attachment());
        assertEquals("2026-05", result.yearMonth());
        assertEquals("EMAIL", result.channel());
        assertEquals("bi***@example.com", result.recipientEmail());
    }

    @Test
    void rejectsUserWithoutAiReportFeatureBeforeReadingSensitiveData() {
        when(subscriptionClient.supportsFeature(7L, Feature.AI_REPORT)).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.deliver(7L, "2026-05"));

        assertEquals(AiReportErrorCode.EMAIL_DELIVERY_FORBIDDEN.getStatusCode(), exception.getStatusCode());
        verify(userClient, never()).findUser(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    void rejectsMissingEmailWithoutLookingUpReport() {
        when(userClient.findUser(7L)).thenReturn(
                new AiUser(7L, "  ")
        );

        ServiceException exception = assertThrows(ServiceException.class, () -> service.deliver(7L, "2026-05"));

        assertEquals(AiReportErrorCode.EMAIL_NOT_AVAILABLE.getStatusCode(), exception.getStatusCode());
        verify(reportClient, never()).findByUserIdAndYearMonth(any(), any());
    }

    @Test
    void rejectsInvalidYearMonthBeforeAnyDomainLookup() {
        ServiceException exception = assertThrows(ServiceException.class, () -> service.deliver(7L, "2026-13"));

        assertEquals(400, exception.getStatusCode());
        verify(subscriptionClient, never()).supportsFeature(any(), any());
    }

    @Test
    void automaticDeliveryReloadsStoredReportAndReusesPdfAndEmailComposition() {
        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));

        verify(subscriptionClient).supportsFeature(7L, Feature.AI_REPORT);
        verify(userClient).findUser(7L);
        verify(reportClient).findByUserIdAndYearMonth(7L, "2026-05");
        verify(pdfService).generate(any());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        EmailMessage message = captor.getValue();
        assertEquals("billing.owner@example.com", message.recipient());
        assertEquals("[Ntropy] 2026년 5월 AI 재무 리포트", message.subject());
        assertEquals("Ntropy_AI_Report_2026-05.pdf", message.attachmentName());
        assertEquals("application/pdf", message.attachmentContentType());
        assertArrayEquals(new byte[] {1, 2, 3}, message.attachment());
    }

    @Test
    void automaticDeliverySkipsUserWithoutAiReportFeature() {
        when(subscriptionClient.supportsFeature(7L, Feature.AI_REPORT)).thenReturn(false);

        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));

        verify(userClient, never()).findUser(any());
        verify(reportClient, never()).findByUserIdAndYearMonth(any(), any());
        verify(pdfService, never()).generate(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    void automaticDeliverySkipsWhenUserLookupReturnsNull() {
        when(userClient.findUser(7L)).thenReturn(null);

        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));

        verify(reportClient, never()).findByUserIdAndYearMonth(any(), any());
        verify(pdfService, never()).generate(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    void automaticDeliverySkipsNullOrBlankEmail() {
        when(userClient.findUser(7L))
                .thenReturn(new AiUser(7L, null))
                .thenReturn(new AiUser(7L, "  "));

        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));
        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));

        verify(reportClient, never()).findByUserIdAndYearMonth(any(), any());
        verify(pdfService, never()).generate(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    void automaticDeliveryIsolatesReportLookupFailure() {
        when(reportClient.findByUserIdAndYearMonth(7L, "2026-05"))
                .thenThrow(new IllegalStateException("simulated report lookup failure"));

        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));

        verify(pdfService, never()).generate(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    void automaticDeliveryIsolatesPdfFailureWithoutCallingSmtp() {
        when(pdfService.generate(any()))
                .thenThrow(new IllegalStateException("simulated PDF failure"));

        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));

        verify(emailSender, never()).send(any());
    }

    @Test
    void automaticDeliveryIsolatesSmtpFailure() {
        doThrow(new IllegalStateException("simulated SMTP failure"))
                .when(emailSender).send(any());

        assertDoesNotThrow(() -> service.deliverAutomatically(7L, "2026-05"));

        verify(emailSender).send(any());
    }

    @Test
    void automaticFailureLogsOnlySafeCodesWithoutSensitiveMessagesOrThrowable() {
        Logger logger = (Logger) LogManager.getLogger(AiReportEmailDeliveryService.class);
        Level originalLevel = logger.getLevel();
        CollectingAppender appender = new CollectingAppender("automatic-delivery-security-test");
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);

        String emailSentinel = "private-recipient@sentinel.invalid";
        String smtpSentinel = "MAIL_USERNAME=sender@sentinel.invalid MAIL_PASSWORD=secret-sentinel";
        String amountSentinel = "totalIncome=987654321원";

        try {
            when(reportClient.findByUserIdAndYearMonth(7L, "2026-05"))
                    .thenThrow(new IllegalStateException(emailSentinel));
            service.deliverAutomatically(7L, "2026-05");

            doReturn(reportSummary()).when(reportClient)
                    .findByUserIdAndYearMonth(7L, "2026-05");
            when(pdfService.generate(any()))
                    .thenThrow(new IllegalStateException(amountSentinel));
            service.deliverAutomatically(7L, "2026-05");

            doReturn(new byte[] {1, 2, 3}).when(pdfService).generate(any());
            doThrow(new IllegalStateException(smtpSentinel)).when(emailSender).send(any());
            service.deliverAutomatically(7L, "2026-05");

            List<LogEvent> failureEvents = appender.getEvents().stream()
                    .filter(event -> event.getMessage().getFormattedMessage().contains("result=FAILED"))
                    .toList();
            String combinedMessages = failureEvents.stream()
                    .map(event -> event.getMessage().getFormattedMessage())
                    .reduce("", (left, right) -> left + "\n" + right);

            assertEquals(3, failureEvents.size());
            assertTrue(combinedMessages.contains("failureCode=REPORT_LOOKUP_FAILED"));
            assertTrue(combinedMessages.contains("failureCode=PDF_GENERATION_FAILED"));
            assertTrue(combinedMessages.contains("failureCode=EMAIL_DELIVERY_FAILED"));
            assertFalse(combinedMessages.contains(emailSentinel));
            assertFalse(combinedMessages.contains(smtpSentinel));
            assertFalse(combinedMessages.contains(amountSentinel));
            assertTrue(failureEvents.stream().allMatch(event -> event.getThrown() == null));
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    private AiReportSummary reportSummary() {
        return new AiReportSummary(
                31L,
                7L,
                "2026-05",
                new ObjectMapper().createObjectNode().put("totalIncome", 10),
                new ObjectMapper().createObjectNode(),
                LocalDateTime.of(2026, 6, 1, 1, 2)
        );
    }

    private static final class CollectingAppender extends AbstractAppender {

        private final List<LogEvent> events = new ArrayList<>();

        private CollectingAppender(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        private List<LogEvent> getEvents() {
            return events;
        }
    }
}
