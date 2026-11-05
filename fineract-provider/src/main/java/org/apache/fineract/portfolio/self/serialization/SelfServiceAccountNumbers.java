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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.fineract.infrastructure.core.service.database.JdbcJavaType;
import org.apache.fineract.infrastructure.dataqueries.data.GenericResultsetData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetRowData;

public final class SelfServiceAccountNumbers {

    private static final Pattern ERROR_ACCOUNT = Pattern.compile("(accountNumber\\.)(.*?)(?=\\.accountType\\.|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SelfServiceAccountNumbers() {}

    public static boolean isAccountNumber(String name) {
        String normalized = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.endsWith("accountno") || normalized.endsWith("accountnumber") || normalized.endsWith("iban")
                || normalized.endsWith("ibanno");
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty() || (value.startsWith("****") && value.length() <= 8)) {
            return value;
        }
        return "****" + value.substring(Math.max(0, value.length() - 4));
    }

    public static String maskErrorCode(String value) {
        return ERROR_ACCOUNT.matcher(value).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + mask(match.group(2))));
    }

    public static Map<String, String> maskReportParameters(Map<String, String> parameters) {
        Map<String, String> result = new LinkedHashMap<>();
        parameters.forEach((key, value) -> result.put(key, isAccountNumber(key) ? mask(value) : value));
        return result;
    }

    public static GenericResultsetData maskReport(GenericResultsetData source) {
        List<ResultsetColumnHeaderData> columns = new ArrayList<>(source.getColumnHeaders());
        for (int i = 0; i < columns.size(); i++) {
            if (isAccountNumber(columns.get(i).getColumnName())) {
                columns.set(i, columns.get(i).withColumnType(JdbcJavaType.VARCHAR));
            }
        }
        List<ResultsetRowData> rows = new ArrayList<>();
        for (ResultsetRowData sourceRow : source.getData()) {
            List<Object> values = new ArrayList<>(sourceRow.getRow());
            for (int i = 0; i < columns.size() && i < values.size(); i++) {
                if (isAccountNumber(columns.get(i).getColumnName()) && values.get(i) != null) {
                    values.set(i, mask(values.get(i).toString()));
                }
            }
            rows.add(ResultsetRowData.create(values));
        }
        return new GenericResultsetData(columns, rows);
    }
}
