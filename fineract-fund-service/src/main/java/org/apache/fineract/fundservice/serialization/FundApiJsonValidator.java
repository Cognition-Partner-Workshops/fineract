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
package org.apache.fineract.fundservice.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.fineract.fundservice.data.FundRequest;
import org.apache.fineract.fundservice.exception.ApiParameterError;
import org.apache.fineract.fundservice.exception.FundValidationException;
import org.springframework.stereotype.Component;

/**
 * Validates inbound Fund JSON, mirroring the monolith {@code FundCommandFromApiJsonDeserializer}: only {@code name} and
 * {@code externalId} are accepted (unknown parameters are rejected), {@code name} is required and capped at 100 chars,
 * and {@code externalId} is capped at 100 chars. Validation failures are collected and raised as a single
 * {@link FundValidationException} (HTTP 400).
 */
@Component
public class FundApiJsonValidator {

    public static final String NAME = "name";
    public static final String EXTERNAL_ID = "externalId";
    private static final int MAX_LENGTH = 100;
    private static final Set<String> SUPPORTED_PARAMETERS = Set.of(NAME, EXTERNAL_ID);

    private final ObjectMapper objectMapper;

    public FundApiJsonValidator(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FundRequest validateForCreate(final String json) {
        final ObjectNode root = parse(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        checkForUnsupportedParameters(root, errors);

        final String name = extractString(root, NAME);
        validateName(name, errors);

        final String externalId = extractString(root, EXTERNAL_ID);
        validateExternalId(externalId, errors);

        throwIfErrors(errors);

        final FundRequest request = new FundRequest();
        request.setName(name);
        request.setExternalId(externalId);
        return request;
    }

    public FundRequest validateForUpdate(final String json) {
        final ObjectNode root = parse(json);
        final List<ApiParameterError> errors = new ArrayList<>();
        checkForUnsupportedParameters(root, errors);

        final FundRequest request = new FundRequest();
        if (root.has(NAME)) {
            final String name = extractString(root, NAME);
            validateName(name, errors);
            request.setName(name);
        }
        if (root.has(EXTERNAL_ID)) {
            final String externalId = extractString(root, EXTERNAL_ID);
            validateExternalId(externalId, errors);
            request.setExternalId(externalId);
        }

        throwIfErrors(errors);
        return request;
    }

    private ObjectNode parse(final String json) {
        if (json == null || json.isBlank()) {
            throw new FundValidationException(List.of(ApiParameterError.parameterError("error.msg.fund.invalid.json",
                    "The request body is missing or is not valid JSON.", null, null)));
        }
        final JsonNode node;
        try {
            node = this.objectMapper.readTree(json);
        } catch (final com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new FundValidationException(List.of(ApiParameterError.parameterError("error.msg.fund.invalid.json",
                    "The request body is not valid JSON.", null, null)), e);
        }
        if (node == null || !node.isObject()) {
            throw new FundValidationException(List.of(ApiParameterError.parameterError("error.msg.fund.invalid.json",
                    "The request body must be a JSON object.", null, null)));
        }
        return (ObjectNode) node;
    }

    private void checkForUnsupportedParameters(final ObjectNode root, final List<ApiParameterError> errors) {
        final Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            final String fieldName = fieldNames.next();
            if (!SUPPORTED_PARAMETERS.contains(fieldName)) {
                errors.add(ApiParameterError.parameterError("error.msg.fund.unsupported.parameter",
                        "The parameter '" + fieldName + "' is not supported.", fieldName, null));
            }
        }
    }

    private String extractString(final ObjectNode root, final String parameterName) {
        final JsonNode value = root.get(parameterName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private void validateName(final String name, final List<ApiParameterError> errors) {
        if (name == null || name.isBlank()) {
            errors.add(ApiParameterError.parameterError("validation.msg.fund.name.cannot.be.blank",
                    "The parameter 'name' is mandatory and cannot be blank.", NAME, name));
            return;
        }
        if (name.length() > MAX_LENGTH) {
            errors.add(ApiParameterError.parameterError("validation.msg.fund.name.exceeds.max.length",
                    "The parameter 'name' exceeds the maximum length of " + MAX_LENGTH + ".", NAME, name));
        }
    }

    private void validateExternalId(final String externalId, final List<ApiParameterError> errors) {
        if (externalId != null && externalId.length() > MAX_LENGTH) {
            errors.add(ApiParameterError.parameterError("validation.msg.fund.externalId.exceeds.max.length",
                    "The parameter 'externalId' exceeds the maximum length of " + MAX_LENGTH + ".", EXTERNAL_ID, externalId));
        }
    }

    private void throwIfErrors(final List<ApiParameterError> errors) {
        if (!errors.isEmpty()) {
            throw new FundValidationException(errors);
        }
    }
}
