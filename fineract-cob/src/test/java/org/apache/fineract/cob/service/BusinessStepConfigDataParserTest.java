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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.cob.data.BusinessStep;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class BusinessStepConfigDataParserTest {

    private final BusinessStepConfigDataParser underTest = new BusinessStepConfigDataParser(new FromJsonHelper());

    @Test
    void parseUpdateReadsNamesAndOrders() {
        JsonCommand command = command(
                "{\"businessSteps\":[{\"stepName\":\"FIRST\",\"order\":2}," + "{\"stepName\":\"SECOND\",\"order\":1}]}");

        List<BusinessStep> result = underTest.parseUpdate(command);

        assertEquals(2, result.size());
        assertEquals("FIRST", result.get(0).getStepName());
        assertEquals(2L, result.get(0).getOrder());
        assertEquals("SECOND", result.get(1).getStepName());
        assertEquals(1L, result.get(1).getOrder());
    }

    @Test
    void parseUpdateAllowsEmptyBusinessSteps() {
        assertEquals(List.of(), underTest.parseUpdate(command("{\"businessSteps\":[]}")));
    }

    @Test
    void parseUpdateRejectsBlankJson() {
        assertThrows(InvalidJsonException.class, () -> underTest.parseUpdate(command(" ")));
    }

    @Test
    void parseUpdateRejectsMissingBusinessSteps() {
        assertThrows(NullPointerException.class, () -> underTest.parseUpdate(command("{}")));
    }

    private static JsonCommand command(String json) {
        JsonCommand command = mock(JsonCommand.class);
        when(command.json()).thenReturn(json);
        return command;
    }
}
