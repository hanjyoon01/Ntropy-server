package com.ntropy.account.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;

@ExtendWith(MockitoExtension.class)
class VirtualFinancialDataSeedInitializerTest {

    @Mock
    private VirtualFinancialDataService virtualFinancialDataService;

    @Test
    void generatesOnStartupWhenEnabled() {
        when(virtualFinancialDataService.generate()).thenReturn(new GenerationSummary(
                50, 100, 15, 15_000, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)
        ));
        VirtualFinancialDataSeedInitializer initializer =
                new VirtualFinancialDataSeedInitializer(virtualFinancialDataService, true);

        initializer.afterSingletonsInstantiated();

        verify(virtualFinancialDataService).generate();
    }

    @Test
    void doesNothingOnStartupWhenDisabled() {
        VirtualFinancialDataSeedInitializer initializer =
                new VirtualFinancialDataSeedInitializer(virtualFinancialDataService, false);

        initializer.afterSingletonsInstantiated();

        verify(virtualFinancialDataService, never()).generate();
    }
}
