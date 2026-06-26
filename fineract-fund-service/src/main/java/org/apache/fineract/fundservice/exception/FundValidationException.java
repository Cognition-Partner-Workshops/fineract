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
package org.apache.fineract.fundservice.exception;

import java.util.List;

/**
 * Raised when an inbound request fails validation, mirroring the monolith {@code PlatformApiDataValidationException}
 * ({@code validation.msg.validation.errors.exist}). Mapped to HTTP 400 by the web layer.
 */
public class FundValidationException extends RuntimeException {

    private final List<ApiParameterError> errors;

    public FundValidationException(final List<ApiParameterError> errors) {
        this(errors, null);
    }

    public FundValidationException(final List<ApiParameterError> errors, final Throwable cause) {
        super("Validation errors exist.", cause);
        this.errors = List.copyOf(errors);
    }

    public List<ApiParameterError> getErrors() {
        return this.errors;
    }
}
