package com.ntropy.ai.email;

/** 메모리 첨부파일 한 개를 포함하는 이메일 발송 명령. */
public record EmailMessage(
        String recipient,
        String subject,
        String body,
        String attachmentName,
        String attachmentContentType,
        byte[] attachment
) {
}
