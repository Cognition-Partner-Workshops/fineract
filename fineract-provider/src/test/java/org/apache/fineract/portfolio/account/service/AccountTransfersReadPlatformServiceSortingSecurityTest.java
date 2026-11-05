package org.apache.fineract.portfolio.account.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.account.mapper.AccountTransfersMapper;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AccountTransfersReadPlatformServiceSortingSecurityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private OfficeReadPlatformService officeReadPlatformService;
    @Mock
    private PortfolioAccountReadPlatformService portfolioAccountReadPlatformService;
    @Mock
    private ColumnValidator columnValidator;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private AccountTransfersMapper accountTransfersMapper;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private SqlValidator sqlValidator;
    @InjectMocks
    private AccountTransfersReadPlatformServiceImpl underTest;

    @Test
    void rejectsSortOrderSqlExpressionForStandingInstructionTransfers() {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("id").sortOrder("desc, (select 1)").build();

        assertThrows(PlatformApiDataValidationException.class, () -> underTest.retrieveByStandingInstruction(1L, searchParameters));
    }

    @Test
    void rejectsOrderBySqlExpressionForStandingInstructionTransfers() {
        final SearchParameters searchParameters = SearchParameters.builder().orderBy("id, (select 1)").sortOrder("DESC").build();

        assertThrows(PlatformApiDataValidationException.class, () -> underTest.retrieveByStandingInstruction(1L, searchParameters));
    }

    @Test
    void acceptsCaseInsensitiveSortOrdersForAllowedColumn() {
        assertDoesNotThrow(
                () -> underTest.retrieveByStandingInstruction(1L, SearchParameters.builder().orderBy("id").sortOrder("DESC").build()));
        assertDoesNotThrow(
                () -> underTest.retrieveByStandingInstruction(1L, SearchParameters.builder().orderBy("id").sortOrder("asc").build()));
    }
}
