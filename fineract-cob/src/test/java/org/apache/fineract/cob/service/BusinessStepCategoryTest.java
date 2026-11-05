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
package org.apache.fineract.cob.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BusinessStepCategoryTest {

    @Test
    void loanIsTheOnlyCategory() {
        assertEquals(1, BusinessStepCategory.values().length);
        assertEquals(BusinessStepCategory.LOAN, BusinessStepCategory.getCategoryName("LOAN"));
        assertEquals("LOAN", BusinessStepCategory.LOAN.toString());
    }

    @Test
    void unknownOrEmptyCategoryIsRejected() {
        assertEquals("Category not found by name: UNKNOWN",
                assertThrows(IllegalArgumentException.class, () -> BusinessStepCategory.getCategoryName("UNKNOWN")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> BusinessStepCategory.getCategoryName(""));
    }

    @Test
    void nullCategoryThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> BusinessStepCategory.getCategoryName(null));
    }
}
