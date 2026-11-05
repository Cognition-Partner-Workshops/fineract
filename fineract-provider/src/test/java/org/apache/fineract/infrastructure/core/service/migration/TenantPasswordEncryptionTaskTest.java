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
package org.apache.fineract.infrastructure.core.service.migration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import org.apache.fineract.infrastructure.core.service.database.DatabasePasswordEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

class TenantPasswordEncryptionTaskTest {

    private static final String MALICIOUS_VALUE = "x', master_password_hash = 'y' where 1=1; --";

    @Test
    void schemaPasswordUpdateBindsValuesAsParameters() throws Exception {
        verifyParameterizedUpdate(new TenantPasswordEncryptionTask(), "schema_password",
                "update tenant_server_connections set schema_password = ?, master_password_hash = ? where id = ?");
    }

    @Test
    void readOnlySchemaPasswordUpdateBindsValuesAsParameters() throws Exception {
        verifyParameterizedUpdate(new TenantReadOnlyPasswordEncryptionTask(), "readonly_schema_password",
                "update tenant_server_connections set readonly_schema_password = ?, master_password_hash = ? where id = ?");
    }

    private <T extends CustomTaskChange & ApplicationContextAware> void verifyParameterizedUpdate(T task, String passwordColumn,
            String expectedSql) throws Exception {
        DatabasePasswordEncryptor encryptor = mock(DatabasePasswordEncryptor.class);
        when(encryptor.encrypt("plain")).thenReturn(MALICIOUS_VALUE);
        when(encryptor.getMasterPasswordHash()).thenReturn(MALICIOUS_VALUE);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(DatabasePasswordEncryptor.class)).thenReturn(encryptor);
        task.setApplicationContext(applicationContext);

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("id")).thenReturn("1");
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getString(passwordColumn)).thenReturn("plain");

        Statement statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        JdbcConnection connection = mock(JdbcConnection.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(expectedSql)).thenReturn(preparedStatement);
        Database database = mock(Database.class);
        when(database.getConnection()).thenReturn(connection);

        task.execute(database);

        verify(statement, never()).execute(anyString());
        verify(statement, never()).executeUpdate(anyString());
        verify(preparedStatement).setString(1, MALICIOUS_VALUE);
        verify(preparedStatement).setString(2, MALICIOUS_VALUE);
        verify(preparedStatement).setLong(3, 1L);
        verify(preparedStatement).executeUpdate();
    }
}
