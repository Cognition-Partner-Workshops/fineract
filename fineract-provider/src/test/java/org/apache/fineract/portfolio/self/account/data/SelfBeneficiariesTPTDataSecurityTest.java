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
package org.apache.fineract.portfolio.self.account.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SelfBeneficiariesTPTDataSecurityTest {

    @Test
    void masksOwnerNameAndDoesNotExposeClientId() throws Exception {
        final SelfBeneficiariesTPTData beneficiary = new SelfBeneficiariesTPTData(1L, "nickname", "office", "Example Owner", null,
                "synthetic-account", 100L);

        final Field clientName = SelfBeneficiariesTPTData.class.getDeclaredField("clientName");
        clientName.setAccessible(true);
        assertEquals("E***", clientName.get(beneficiary));
        assertNotNull(SelfBeneficiariesTPTData.class.getDeclaredField("accountNumber"));
        assertFalse(
                Arrays.stream(SelfBeneficiariesTPTData.class.getDeclaredFields()).anyMatch(field -> field.getName().equals("clientId")));
    }
}
