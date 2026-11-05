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
package org.apache.fineract.infrastructure.dataqueries.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.dataqueries.data.ReportData;
import org.apache.fineract.infrastructure.dataqueries.data.ReportParameterJoinData;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ReadReportingServiceImplTest {

    private static final Long REPORT_ID = 5L;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReadReportingServiceImpl underTest;

    private static ReportParameterJoinData row(final Long reportParameterId, final String parameterName) {
        return new ReportParameterJoinData(REPORT_ID, "Active Loans", "Table", null, "Loan", "desc", "select 1", true, true,
                reportParameterId, reportParameterId, parameterName, parameterName);
    }

    private void givenRows(final List<ReportParameterJoinData> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(REPORT_ID))).thenReturn(rows);
    }

    @Test
    void retrieveReportReturnsTheReportWithAllItsParameters() {
        givenRows(List.of(row(1L, "officeId"), row(2L, "loanOfficerId")));

        final ReportData report = underTest.retrieveReport(REPORT_ID);

        assertThat(report).hasFieldOrPropertyWithValue("id", REPORT_ID).hasFieldOrPropertyWithValue("reportName", "Active Loans");
        assertThat(report).extracting("reportParameters").asInstanceOf(InstanceOfAssertFactories.COLLECTION).hasSize(2);
    }

    @Test
    void retrieveReportReturnsNullWhenReportDoesNotExist() {
        givenRows(List.of());

        assertThat(underTest.retrieveReport(REPORT_ID)).isNull();
    }

    @Test
    void retrieveReportReturnsReportWithoutParameters() {
        givenRows(List.of(row(null, null)));

        final ReportData report = underTest.retrieveReport(REPORT_ID);

        assertThat(report).hasFieldOrPropertyWithValue("id", REPORT_ID);
        assertThat(report).extracting("reportParameters").isNull();
    }
}
