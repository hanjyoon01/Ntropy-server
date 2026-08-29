package com.ntropy.account.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AccountTransactionClassificationEventPublisherTest {

    @Test
    void publishesImmediatelyWhenNoTransactionIsActive() {
        RecordingApplicationEventPublisher events =
                new RecordingApplicationEventPublisher();
        AccountTransactionClassificationEventPublisher publisher =
                new AccountTransactionClassificationEventPublisher(events);

        publisher.publishAfterCommit(42L);

        assertEquals(
                List.of(new AccountTransactionsCollectedEvent(42L)),
                events.publishedEvents
        );
    }

    @Test
    void publishesOnlyAfterTransactionCommits() throws Exception {
        RecordingApplicationEventPublisher events =
                new RecordingApplicationEventPublisher();
        AccountTransactionClassificationEventPublisher publisher =
                new AccountTransactionClassificationEventPublisher(events);
        TransactionTemplate transaction = transactionTemplate();

        transaction.executeWithoutResult(status -> {
            publisher.publishAfterCommit(42L);
            assertTrue(events.publishedEvents.isEmpty());
        });

        assertEquals(
                List.of(new AccountTransactionsCollectedEvent(42L)),
                events.publishedEvents
        );
    }

    @Test
    void doesNotPublishWhenTransactionRollsBack() throws Exception {
        RecordingApplicationEventPublisher events =
                new RecordingApplicationEventPublisher();
        AccountTransactionClassificationEventPublisher publisher =
                new AccountTransactionClassificationEventPublisher(events);
        TransactionTemplate transaction = transactionTemplate();

        transaction.executeWithoutResult(status -> {
            publisher.publishAfterCommit(42L);
            status.setRollbackOnly();
        });

        assertTrue(events.publishedEvents.isEmpty());
    }

    private static TransactionTemplate transactionTemplate() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static class RecordingApplicationEventPublisher
            implements ApplicationEventPublisher {
        private final List<Object> publishedEvents = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            publishedEvents.add(event);
        }
    }
}
