package org.apache.fineract.accounting.journalentry.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper;
import org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JournalEntryReadPlatformServiceSortingSecurityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private GLAccountReadPlatformService glAccountReadPlatformService;
    @Mock
    private OfficeReadPlatformService officeReadPlatformService;
    @Mock
    private ColumnValidator columnValidator;
    @Mock
    private FinancialActivityAccountRepositoryWrapper financialActivityAccountRepositoryWrapper;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @InjectMocks
    private JournalEntryReadPlatformServiceImpl underTest;

    @Test
    void rejectsSortOrderSqlExpression() {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("id").sortOrder("desc, (select 1)").build();

        assertThrows(PlatformApiDataValidationException.class, () -> retrieveAll(searchParameters));
    }

    @Test
    void rejectsOrderBySqlExpression() {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("id, (select 1)").sortOrder("DESC").build();

        assertThrows(PlatformApiDataValidationException.class, () -> retrieveAll(searchParameters));
    }

    @Test
    void acceptsCaseInsensitiveSortOrdersForAllowedColumn() {
        assertDoesNotThrow(() -> retrieveAll(SearchParameters.builder().orderBy("id").sortOrder("DESC").build()));
        assertDoesNotThrow(() -> retrieveAll(SearchParameters.builder().orderBy("id").sortOrder("asc").build()));
    }

    private void retrieveAll(final SearchParameters searchParameters) {
        underTest.retrieveAll(searchParameters, null, null, null, null, null, null, null, null, null);
    }
}
