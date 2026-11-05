/**
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
package org.apache.fineract.portfolio.floatingrates.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.floatingrates.data.FloatingRateData;
import org.apache.fineract.portfolio.floatingrates.data.FloatingRatePeriodData;
import org.apache.fineract.portfolio.floatingrates.data.InterestRatePeriodData;
import org.apache.fineract.portfolio.floatingrates.exception.FloatingRateNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class FloatingRatesReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private FloatingRatesReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FloatingRatesReadPlatformServiceImpl(jdbcTemplate);
    }

    @Test
    void retrieveAllMapsRateMetadataWithoutPeriodLookup() throws SQLException {
        ResultSet row = rateRow();
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<FloatingRateData>>any())).thenAnswer(invocation -> {
            RowMapper<FloatingRateData> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });

        FloatingRateData result = service.retrieveAll().get(0);

        assertEquals(7L, result.getId());
        assertEquals("Prime", result.getName());
        assertTrue(result.getIsBaseLendingRate());
        assertTrue(result.getIsActive());
        assertNull(result.getRatePeriods());
    }

    @Test
    void retrieveAllActiveAddsActiveFilterAndPreservesEmptyResult() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<FloatingRateData>>any())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertTrue(sql.contains("where rate.is_active = true"));
            return List.of();
        });

        assertTrue(service.retrieveAllActive().isEmpty());
    }

    @Test
    void retrieveLookupActiveMapsLookupFields() throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(7L);
        when(row.getString("name")).thenReturn("Prime");
        when(row.getBoolean("isBaseLendingRate")).thenReturn(true);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<FloatingRateData>>any())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertTrue(sql.contains("where rate.is_active = true"));
            RowMapper<FloatingRateData> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });

        FloatingRateData result = service.retrieveLookupActive().get(0);

        assertEquals(7L, result.getId());
        assertEquals("Prime", result.getName());
        assertTrue(result.getIsBaseLendingRate());
        assertTrue(result.getIsActive());
    }

    @Test
    void retrieveOneLoadsAndMapsActivePeriodsWithRateIdentifier() throws SQLException {
        ResultSet rateRow = rateRow();
        ResultSet periodRow = mock(ResultSet.class);
        when(periodRow.getLong("id")).thenReturn(8L);
        when(periodRow.getDate("fromDate")).thenReturn(Date.valueOf("2025-01-02"));
        when(periodRow.getBigDecimal("interestRate")).thenReturn(new BigDecimal("5.25"));
        when(periodRow.getBoolean("isDifferentialToBaseLendingRate")).thenReturn(true);
        when(periodRow.getBoolean("isActive")).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), ArgumentMatchers.<RowMapper<FloatingRateData>>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertTrue(sql.contains("where rate.id = ?"));
                    assertArrayEquals(new Object[] { 7L }, (Object[]) invocation.getRawArguments()[2]);
                    RowMapper<FloatingRateData> mapper = invocation.getArgument(1);
                    return mapper.mapRow(rateRow, 0);
                });
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<FloatingRatePeriodData>>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertTrue(sql.contains("period.is_active = true"));
                    assertTrue(sql.contains("order by period.from_date desc"));
                    assertArrayEquals(new Object[] { 7L }, (Object[]) invocation.getRawArguments()[2]);
                    RowMapper<FloatingRatePeriodData> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(periodRow, 0));
                });

        FloatingRateData result = service.retrieveOne(7L);

        assertEquals(1, result.getRatePeriods().size());
        FloatingRatePeriodData period = result.getRatePeriods().get(0);
        assertEquals(8L, period.getId());
        assertEquals(LocalDate.of(2025, 1, 2), period.getFromDate());
        assertEquals(0, period.getInterestRate().compareTo(new BigDecimal("5.25")));
        assertTrue(period.getIsDifferentialToBaseLendingRate());
    }

    @Test
    void retrieveInterestRatePeriodsMapsDatesRatesAndProductArgument() throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getDate("linkedrateperiods_from_date")).thenReturn(Date.valueOf("2025-03-01"));
        when(row.getBigDecimal("linkedrateperiods_interest_rate")).thenReturn(new BigDecimal("1.25"));
        when(row.getBoolean("linkedrateperiods_is_differential_to_base_lending_rate")).thenReturn(true);
        when(row.getDate("baserate_from_date")).thenReturn(Date.valueOf("2025-01-01"));
        when(row.getBigDecimal("baserate_interest_rate")).thenReturn(new BigDecimal("5.00"));
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<InterestRatePeriodData>>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertTrue(sql.contains("and lp.id = ?"));
                    assertTrue(sql.contains("MAX(b.from_date)"));
                    assertArrayEquals(new Object[] { 11L }, (Object[]) invocation.getRawArguments()[2]);
                    RowMapper<InterestRatePeriodData> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });

        InterestRatePeriodData result = service.retrieveInterestRatePeriods(11L).get(0);

        assertEquals(LocalDate.of(2025, 3, 1), result.getFromDate());
        assertEquals(LocalDate.of(2025, 1, 1), result.getBlrFromDate());
        assertEquals(0, result.getInterestRate().compareTo(new BigDecimal("1.25")));
        assertEquals(0, result.getBlrInterestRate().compareTo(new BigDecimal("5.00")));
        assertTrue(result.isDifferentialToBLR());
    }

    @Test
    void retrieveInterestRatePeriodsAllowsNullDates() {
        ResultSet row = mock(ResultSet.class);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<InterestRatePeriodData>>any(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<InterestRatePeriodData> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });

        InterestRatePeriodData result = service.retrieveInterestRatePeriods(11L).get(0);

        assertNull(result.getFromDate());
        assertNull(result.getBlrFromDate());
    }

    @Test
    void missingRateIsTranslatedToNotFound() {
        when(jdbcTemplate.queryForObject(anyString(), ArgumentMatchers.<RowMapper<FloatingRateData>>any(), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        FloatingRateNotFoundException exception = assertThrows(FloatingRateNotFoundException.class, () -> service.retrieveOne(7L));

        assertEquals("error.msg.floatingrate.id.invalid", exception.getGlobalisationMessageCode());
        assertEquals("Floating Rate with identifier 7 does not exist", exception.getMessage());
    }

    @Test
    void baseLendingRateQueryRestrictsToActiveBaseRate() {
        when(jdbcTemplate.queryForObject(anyString(), ArgumentMatchers.<RowMapper<FloatingRateData>>any())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertTrue(sql.contains("rate.is_base_lending_rate = true and rate.is_active = true"));
            throw new EmptyResultDataAccessException(1);
        });

        FloatingRateNotFoundException exception = assertThrows(FloatingRateNotFoundException.class, service::retrieveBaseLendingRate);

        assertEquals("error.msg.floatingrate.base.lending.rate.not.found", exception.getGlobalisationMessageCode());
    }

    @Test
    void missingProductRateIsTranslatedToProductSpecificNotFound() {
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<InterestRatePeriodData>>any(), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        FloatingRateNotFoundException exception = assertThrows(FloatingRateNotFoundException.class,
                () -> service.retrieveInterestRatePeriods(11L));

        assertEquals("error.msg.floatingrate.not.found.for.product", exception.getGlobalisationMessageCode());
    }

    private static ResultSet rateRow() throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(7L);
        when(row.getString("name")).thenReturn("Prime");
        when(row.getBoolean("isBaseLendingRate")).thenReturn(true);
        when(row.getBoolean("isActive")).thenReturn(true);
        return row;
    }
}
