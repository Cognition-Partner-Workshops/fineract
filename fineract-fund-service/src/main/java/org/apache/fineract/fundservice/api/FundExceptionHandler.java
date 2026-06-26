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
package org.apache.fineract.fundservice.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.fundservice.exception.ApiParameterError;
import org.apache.fineract.fundservice.exception.FundDuplicateException;
import org.apache.fineract.fundservice.exception.FundNotFoundException;
import org.apache.fineract.fundservice.exception.FundValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain/validation failures to the HTTP status codes required by the Fund contract: 404 for a missing fund, 400
 * for validation/malformed-body failures, and 409 for unique-constraint (duplicate name/externalId) violations.
 */
@RestControllerAdvice
public class FundExceptionHandler {

    @ExceptionHandler(FundNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(final FundNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "error.msg.fund.id.invalid", ex.getMessage(), null);
    }

    @ExceptionHandler(FundValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(final FundValidationException ex) {
        final Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "validation.msg.validation.errors.exist",
                "Validation errors exist.");
        final List<Map<String, Object>> errors = new ArrayList<>();
        for (final ApiParameterError error : ex.getErrors()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userMessageGlobalisationCode", error.getUserMessageGlobalisationCode());
            entry.put("defaultUserMessage", error.getDefaultUserMessage());
            entry.put("parameterName", error.getParameterName());
            entry.put("value", error.getValue());
            errors.add(entry);
        }
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(final HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "error.msg.fund.invalid.json", "The request body is missing or is not valid JSON.", null);
    }

    @ExceptionHandler(FundDuplicateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(final FundDuplicateException ex) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), ex.getParameterName());
    }

    private ResponseEntity<Map<String, Object>> build(final HttpStatus status, final String code, final String message,
            final String parameterName) {
        final Map<String, Object> body = baseBody(status, code, message);
        if (parameterName != null) {
            body.put("parameterName", parameterName);
        }
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> baseBody(final HttpStatus status, final String code, final String message) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("httpStatusCode", String.valueOf(status.value()));
        body.put("errorCode", code);
        body.put("defaultUserMessage", message);
        return body;
    }
}
