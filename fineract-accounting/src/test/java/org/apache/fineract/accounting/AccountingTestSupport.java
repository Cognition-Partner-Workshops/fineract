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
package org.apache.fineract.accounting;

import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.util.HashMap;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AccountingTestSupport {

    protected static final LocalDate BUSINESS_DATE = LocalDate.of(2025, 3, 20);
    protected final FromJsonHelper jsonHelper = new FromJsonHelper();

    @BeforeEach
    void initializeBusinessDate() {
        ThreadLocalContextUtil.reset();
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "test", "Test tenant", "UTC", null));
        HashMap<BusinessDateType, LocalDate> dates = new HashMap<>();
        dates.put(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE);
        ThreadLocalContextUtil.setBusinessDates(dates);
    }

    @AfterEach
    void clearContext() {
        ThreadLocalContextUtil.reset();
    }

    protected JsonCommand command(String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), jsonHelper, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
