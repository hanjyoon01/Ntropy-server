package com.ntropy.ai.email;

/** SMTP, SES 등 실제 전송 기술과 무관한 이메일 발송 계약. */
public interface EmailSender {

    void send(EmailMessage message);
}
