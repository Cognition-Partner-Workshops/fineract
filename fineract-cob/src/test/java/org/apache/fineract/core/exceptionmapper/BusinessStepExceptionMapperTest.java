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
package org.apache.fineract.core.exceptionmapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.fineract.cob.exceptions.BusinessStepException;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.junit.jupiter.api.Test;

class BusinessStepExceptionMapperTest {

    @Test
    void mapsExceptionToBadRequestWithJsonError() {
        BusinessStepExceptionMapper underTest = new BusinessStepExceptionMapper();

        try (Response response = underTest.toResponse(new BusinessStepException("invalid step"))) {
            assertEquals(400, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            ApiParameterError error = assertInstanceOf(ApiParameterError.class, response.getEntity());
            assertEquals("error.msg.invalid.request.body", error.getUserMessageGlobalisationCode());
            assertEquals("invalid step", error.getDefaultUserMessage());
            assertEquals("stepName", error.getParameterName());
        }
    }
}
