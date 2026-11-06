/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.account.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Locale;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.account.data.StandingInstructionDTO;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class StandingInstructionSortSecurityTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private ColumnValidator columnValidator;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private OfficeReadPlatformService officeReadPlatformService;
    @Mock
    private PortfolioAccountReadPlatformService portfolioAccountReadPlatformService;
    @Mock
    private DropdownReadPlatformService dropdownReadPlatformService;

    private StandingInstructionReadPlatformServiceImpl standingInstructionService;
    private StandingInstructionHistoryReadPlatformServiceImpl standingInstructionHistoryService;

    @BeforeEach
    void setUp() {
        standingInstructionService = new StandingInstructionReadPlatformServiceImpl(jdbcTemplate, clientReadPlatformService,
                officeReadPlatformService, portfolioAccountReadPlatformService, dropdownReadPlatformService, columnValidator, sqlGenerator,
                paginationHelper);
        standingInstructionHistoryService = new StandingInstructionHistoryReadPlatformServiceImpl(jdbcTemplate, columnValidator,
                sqlGenerator, paginationHelper);
    }

    @Test
    void rejectsInvalidOrderByForStandingInstructionBeforeJdbc() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> standingInstructionService.retrieveAll(dto(SearchParameters.builder().orderBy("notASortColumn").build())));
        verifyNoInteractions(jdbcTemplate, paginationHelper);
    }

    @Test
    void rejectsInvalidSortOrderForStandingInstructionBeforeJdbc() {
        assertThrows(PlatformApiDataValidationException.class, () -> standingInstructionService
                .retrieveAll(dto(SearchParameters.builder().orderBy("name").sortOrder("sideways").build())));
        verifyNoInteractions(jdbcTemplate, paginationHelper);
    }

    @Test
    void ordersStandingInstructionByNameDescending() {
        final String sql = captureStandingInstructionSql(SearchParameters.builder().orderBy("name").sortOrder("DESC").build());

        assertTrue(sql.endsWith(" order by name DESC"), sql);
    }

    @Test
    void acceptsMixedCaseStandingInstructionSortParameters() {
        final String sql = captureStandingInstructionSql(SearchParameters.builder().orderBy("Name").sortOrder("desc").build());

        assertTrue(sql.endsWith(" order by Name desc"), sql);
    }

    @Test
    void omitsStandingInstructionOrderByWhenNotRequested() {
        final String sql = captureStandingInstructionSql(SearchParameters.builder().build());

        assertFalse(sql.toLowerCase(Locale.ROOT).contains(" order by "), sql);
    }

    @Test
    void rejectsInvalidOrderByForStandingInstructionHistoryBeforeJdbc() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> standingInstructionHistoryService.retrieveAll(dto(SearchParameters.builder().orderBy("notASortColumn").build())));
        verifyNoInteractions(jdbcTemplate, paginationHelper);
    }

    @Test
    void rejectsInvalidSortOrderForStandingInstructionHistoryBeforeJdbc() {
        assertThrows(PlatformApiDataValidationException.class, () -> standingInstructionHistoryService
                .retrieveAll(dto(SearchParameters.builder().orderBy("name").sortOrder("sideways").build())));
        verifyNoInteractions(jdbcTemplate, paginationHelper);
    }

    @Test
    void ordersStandingInstructionHistoryByNameDescending() {
        final String sql = captureStandingInstructionHistorySql(SearchParameters.builder().orderBy("name").sortOrder("DESC").build());

        assertTrue(sql.endsWith(" order by name DESC"), sql);
    }

    @Test
    void acceptsMixedCaseStandingInstructionHistorySortParameters() {
        final String sql = captureStandingInstructionHistorySql(SearchParameters.builder().orderBy("Name").sortOrder("desc").build());

        assertTrue(sql.endsWith(" order by Name desc"), sql);
    }

    @Test
    void omitsStandingInstructionHistoryOrderByWhenNotRequested() {
        final String sql = captureStandingInstructionHistorySql(SearchParameters.builder().build());

        assertFalse(sql.toLowerCase(Locale.ROOT).contains(" order by "), sql);
    }

    private String captureStandingInstructionSql(final SearchParameters searchParameters) {
        standingInstructionService.retrieveAll(dto(searchParameters));
        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        return sqlCaptor.getValue();
    }

    private String captureStandingInstructionHistorySql(final SearchParameters searchParameters) {
        standingInstructionHistoryService.retrieveAll(dto(searchParameters));
        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(paginationHelper).fetchPage(eq(jdbcTemplate), sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        return sqlCaptor.getValue();
    }

    private static StandingInstructionDTO dto(final SearchParameters searchParameters) {
        return new StandingInstructionDTO(searchParameters, null, null, null, null, null, null, null);
    }
}
