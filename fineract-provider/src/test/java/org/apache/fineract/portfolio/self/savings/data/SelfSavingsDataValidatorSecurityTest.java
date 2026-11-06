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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fineract.portfolio.self.savings.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class SelfSavingsDataValidatorSecurityTest {

    private final SelfSavingsDataValidator underTest = new SelfSavingsDataValidator(new FromJsonHelper());

    @Test
    void rejectsFieldsOutsideCustomerSettableAllowlist() {
        for (String parameter : List.of("fieldOfficerId", "accountNo", "externalId", "status", "allowOverdraft")) {
            final String json = "{\"clientId\":1,\"" + parameter + "\":1}";
            assertThrows(UnsupportedParameterException.class, () -> underTest.validateSavingsApplication(json), parameter);
        }
    }

    @Test
    void acceptsCustomerSettableApplicationFields() {
        final HashMap<String, Object> result = underTest.validateSavingsApplication(
                "{\"clientId\":1,\"productId\":2,\"submittedOnDate\":\"2025-01-01\",\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\"}");

        assertEquals(1L, result.get(SelfSavingsAccountConstants.clientIdParameterName));
    }
}
