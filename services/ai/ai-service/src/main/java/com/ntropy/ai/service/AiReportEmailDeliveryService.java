package com.ntropy.ai.service;

import java.time.DateTimeException;
import java.time.YearMonth;

import org.springframework.stereotype.Service;

import com.ntropy.ai.email.EmailMessage;
import com.ntropy.ai.email.EmailSender;
import com.ntropy.ai.exception.AiReportErrorCode;
import com.ntropy.ai.port.payment.SubscriptionPort;
import com.ntropy.ai.port.user.AiUser;
import com.ntropy.ai.port.user.UserPort;
import com.ntropy.ai.api.client.AiReportQueryClient;
import com.ntropy.common.domain.Feature;
import com.ntropy.ai.api.dto.AiReportDetailSummary;
import com.ntropy.ai.api.dto.AiReportEmailDeliverySummary;
import com.ntropy.ai.api.dto.AiReportSummary;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 구독·사용자·리포트 검증부터 PDF 첨부 발송까지 수동 전달 흐름을 조정한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportEmailDeliveryService {

    private static final String FAILURE_NONE = "NONE";
    private static final String FAILURE_INVALID_REQUEST = "INVALID_REQUEST";
    private static final String FAILURE_SUBSCRIPTION_CHECK = "SUBSCRIPTION_CHECK_FAILED";
    private static final String FAILURE_SUBSCRIPTION_INELIGIBLE = "SUBSCRIPTION_NOT_ELIGIBLE";
    private static final String FAILURE_USER_LOOKUP = "USER_LOOKUP_FAILED";
    private static final String FAILURE_EMAIL_UNAVAILABLE = "EMAIL_NOT_AVAILABLE";
    private static final String FAILURE_REPORT_LOOKUP = "REPORT_LOOKUP_FAILED";
    private static final String FAILURE_PDF_GENERATION = "PDF_GENERATION_FAILED";
    private static final String FAILURE_EMAIL_DELIVERY = "EMAIL_DELIVERY_FAILED";

    private final SubscriptionPort subscriptionPort;
    private final UserPort userPort;
    private final AiReportQueryClient aiReportQueryClient;
    private final AiReportPdfService aiReportPdfService;
    private final EmailSender emailSender;

    public AiReportEmailDeliverySummary deliver(Long userId, String requestedYearMonth) {
        String yearMonth = validateYearMonth(requestedYearMonth);
        Long reportId = null;
        try {
            if (!subscriptionPort.supportsFeature(userId, Feature.AI_REPORT)) {
                throw new ServiceException(AiReportErrorCode.EMAIL_DELIVERY_FORBIDDEN);
            }

            AiUser user = userPort.findUser(userId);
            String recipient = user == null ? null : user.email();
            if (recipient == null || recipient.isBlank()) {
                throw new ServiceException(AiReportErrorCode.EMAIL_NOT_AVAILABLE);
            }

            AiReportSummary summary = aiReportQueryClient.findByUserIdAndYearMonth(userId, yearMonth);
            reportId = summary.reportId();
            sendReport(recipient, summary);

            log.info("AI 리포트 전달 완료. userId={}, reportId={}, yearMonth={}, channel=EMAIL, result=SUCCESS",
                    userId, reportId, yearMonth);
            return new AiReportEmailDeliverySummary(yearMonth, "EMAIL", maskEmail(recipient));
        } catch (RuntimeException exception) {
            log.error("AI 리포트 전달 실패. userId={}, reportId={}, yearMonth={}, channel=EMAIL, result=FAILED, failureCode={}",
                    userId, reportId, yearMonth, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    /** 저장이 완료된 월간 AI 리포트를 현재 구독자의 가입 이메일로 자동 발송한다. */
    public void deliverAutomatically(Long userId, String requestedYearMonth) {
        String yearMonth = requestedYearMonth;
        Long reportId = null;
        String failureCode = FAILURE_INVALID_REQUEST;

        try {
            yearMonth = validateYearMonth(requestedYearMonth);

            failureCode = FAILURE_SUBSCRIPTION_CHECK;
            if (!subscriptionPort.supportsFeature(userId, Feature.AI_REPORT)) {
                log.info("AI 리포트 자동 전달 건너뜀. userId={}, reportId={}, yearMonth={}, "
                                + "channel=EMAIL, deliveryType=AUTO, result=SKIPPED, "
                                + "failureCode={}",
                        userId, reportId, yearMonth, FAILURE_SUBSCRIPTION_INELIGIBLE);
                return;
            }

            failureCode = FAILURE_USER_LOOKUP;
            AiUser user = userPort.findUser(userId);
            String recipient = user == null ? null : user.email();
            if (recipient == null || recipient.isBlank()) {
                log.info("AI 리포트 자동 전달 건너뜀. userId={}, reportId={}, yearMonth={}, "
                                + "channel=EMAIL, deliveryType=AUTO, result=SKIPPED, "
                                + "failureCode={}",
                        userId, reportId, yearMonth, FAILURE_EMAIL_UNAVAILABLE);
                return;
            }

            failureCode = FAILURE_REPORT_LOOKUP;
            AiReportSummary summary = aiReportQueryClient.findByUserIdAndYearMonth(userId, yearMonth);
            reportId = summary.reportId();

            failureCode = FAILURE_PDF_GENERATION;
            byte[] pdf = generatePdf(summary);

            failureCode = FAILURE_EMAIL_DELIVERY;
            sendMessage(recipient, summary.yearMonth(), pdf);

            log.info("AI 리포트 자동 전달 완료. userId={}, reportId={}, yearMonth={}, "
                            + "channel=EMAIL, deliveryType=AUTO, result=SUCCESS, failureCode={}",
                    userId, reportId, yearMonth, FAILURE_NONE);
        } catch (RuntimeException ignored) {
            log.error("AI 리포트 자동 전달 실패. userId={}, reportId={}, yearMonth={}, "
                            + "channel=EMAIL, deliveryType=AUTO, result=FAILED, failureCode={}",
                    userId, reportId, yearMonth, failureCode);
        }
    }

    private void sendReport(String recipient, AiReportSummary summary) {
        byte[] pdf = generatePdf(summary);
        sendMessage(recipient, summary.yearMonth(), pdf);
    }

    private byte[] generatePdf(AiReportSummary summary) {
        return aiReportPdfService.generate(AiReportDetailSummary.from(summary));
    }

    private void sendMessage(String recipient, String yearMonth, byte[] pdf) {
        YearMonth parsedYearMonth = YearMonth.parse(yearMonth);
        emailSender.send(new EmailMessage(
                recipient,
                subject(parsedYearMonth),
                body(parsedYearMonth),
                "Ntropy_AI_Report_" + yearMonth + ".pdf",
                "application/pdf",
                pdf
        ));
    }

    private static String validateYearMonth(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(AiReportErrorCode.INVALID_REQUEST);
        }
        try {
            YearMonth parsed = YearMonth.parse(value);
            if (!parsed.toString().equals(value)) {
                throw new ServiceException(AiReportErrorCode.INVALID_REQUEST);
            }
            return value;
        } catch (DateTimeException exception) {
            throw new ServiceException(AiReportErrorCode.INVALID_REQUEST);
        }
    }

    private static String subject(YearMonth yearMonth) {
        return String.format("[Ntropy] %d년 %d월 AI 재무 리포트", yearMonth.getYear(), yearMonth.getMonthValue());
    }

    private static String body(YearMonth yearMonth) {
        return String.format(
                "안녕하세요.%n%n%d년 %d월 Ntropy AI 재무 리포트를 첨부했습니다.%n"
                        + "자세한 소비 분석과 맞춤 금융상품 추천을 PDF에서 확인해 주세요.%n%n"
                        + "감사합니다.%nNtropy 드림",
                yearMonth.getYear(), yearMonth.getMonthValue()
        );
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }
}
