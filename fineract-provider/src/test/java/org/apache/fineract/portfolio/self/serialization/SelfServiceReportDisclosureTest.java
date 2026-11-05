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
package org.apache.fineract.portfolio.self.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.MultivaluedHashMap;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseType;
import org.apache.fineract.infrastructure.core.service.database.JdbcJavaType;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;
import org.apache.fineract.infrastructure.dataqueries.service.GenericDataService;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingServiceImpl;
import org.apache.fineract.infrastructure.dataqueries.service.export.CsvDatatableReportExportServiceImpl;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlInjectionPreventerService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

class SelfServiceReportDisclosureTest {

    private final GenericDataService data = mock(GenericDataService.class);
    private ReadReportingServiceImpl service;
    private GenericResultsetData original;

    @BeforeEach
    void setup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        SqlInjectionPreventerService sql = mock(SqlInjectionPreventerService.class);
        DatabaseSpecificSQLGenerator generator = mock(DatabaseSpecificSQLGenerator.class);
        FineractProperties properties = mock(FineractProperties.class);
        SqlRowSet sqlRows = mock(SqlRowSet.class);
        AppUser user = mock(AppUser.class);
        Office office = mock(Office.class);
        when(context.authenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(user.getOffice()).thenReturn(office);
        when(office.getHierarchy()).thenReturn(".");
        when(sql.quoteIdentifier(anyString())).thenAnswer(i -> i.getArgument(0));
        when(data.wrapSQL(anyString())).thenAnswer(i -> i.getArgument(0));
        when(data.replace(anyString(), anyString(), nullable(String.class))).thenAnswer(i -> i.getArgument(0));
        when(jdbc.queryForRowSet(anyString(), any(Object[].class))).thenReturn(sqlRows);
        when(sqlRows.next()).thenReturn(true);
        when(sqlRows.getString("the_sql")).thenReturn("select accounts");
        when(generator.currentBusinessDate()).thenReturn("CURRENT_DATE");
        when(generator.currentTenantDateTime()).thenReturn("CURRENT_TIMESTAMP");
        original = new GenericResultsetData(
                List.of(ResultsetColumnHeaderData.basic("Loan Account No.", "VARCHAR", DatabaseType.MYSQL),
                        ResultsetColumnHeaderData.basic("account_number", "BIGINT", DatabaseType.MYSQL),
                        ResultsetColumnHeaderData.basic("balance", "DECIMAL", DatabaseType.MYSQL)),
                List.of(ResultsetRowData.create(List.of("001234567890", 12345678L, new BigDecimal("12345678.50")))));
        when(data.fillGenericResultSet(anyString())).thenReturn(original);
        service = new ReadReportingServiceImpl(jdbc, context, data, sql, generator, properties);
    }

    @Test
    void selfReportDatasetMasksAccountColumnsWithoutMutatingSourceOrAmounts() {
        GenericResultsetData output = service.retrieveGenericResultset("Accounts", "report", Map.of(), true);
        assertEquals("****7890", output.getData().getFirst().getRow().get(0));
        assertEquals("****5678", output.getData().getFirst().getRow().get(1));
        assertEquals(new BigDecimal("12345678.50"), output.getData().getFirst().getRow().get(2));
        assertEquals(JdbcJavaType.VARCHAR, output.getColumnHeaders().get(1).getColumnType());
        assertEquals("001234567890", original.getData().getFirst().getRow().getFirst());
        assertEquals(JdbcJavaType.BIGINT, original.getColumnHeaders().get(1).getColumnType());
    }

    @Test
    void staffReportDatasetIsUnchanged() {
        assertSame(original, service.retrieveGenericResultset("Accounts", "report", Map.of(), false));
    }

    @Test
    void emptyReportsPreserveTheirSchemaWithoutInventingRows() {
        when(data.fillGenericResultSet(anyString())).thenReturn(new GenericResultsetData(original.getColumnHeaders(), List.of()));
        GenericResultsetData output = service.retrieveGenericResultset("Accounts", "report", Map.of(), true);
        assertTrue(output.getData().isEmpty());
        assertEquals(3, output.getColumnHeaders().size());
    }

    @Test
    void csvDownloadNamesHideAccountParametersButSqlInputsAndStaffNamesAreUnchanged() {
        ReadReportingService reports = mock(ReadReportingService.class);
        CsvDatatableReportExportServiceImpl exporter = new CsvDatatableReportExportServiceImpl(reports);
        Map<String, String> parameters = Map.of("R_accountNo", "001234567890");
        try (MockedStatic<DateUtils> dates = mockStatic(DateUtils.class)) {
            dates.when(DateUtils::getOffsetDateTimeOfTenant).thenReturn(OffsetDateTime.parse("2026-11-05T12:00:00Z"));
            String self = exporter.export("Accounts", new MultivaluedHashMap<>(), parameters, true, "report").headers().toString();
            String staff = exporter.export("Accounts", new MultivaluedHashMap<>(), parameters, false, "report").headers().toString();
            assertFalse(self.contains("001234567890"));
            assertTrue(self.contains("7890"));
            assertTrue(staff.contains("001234567890"));
            verify(reports).retrieveReportCSV("Accounts", "report", parameters, true);
            assertEquals("001234567890", parameters.get("R_accountNo"));
        }
    }

    @Test
    void selfCsvExportUsesTheSameMaskedDataset() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.retrieveReportCSV("Accounts", "report", Map.of(), true).write(output);
        String csv = output.toString(StandardCharsets.UTF_8);
        assertFalse(csv.contains("001234567890"));
        assertTrue(csv.contains("****7890"));
        assertTrue(csv.contains("****5678"));
        assertTrue(csv.contains("12345678.50"));
    }
}
