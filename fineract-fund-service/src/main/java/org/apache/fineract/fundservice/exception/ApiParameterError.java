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

/**
 * A single field-level validation error, mirroring the monolith {@code ApiParameterError} shape closely enough to give
 * API clients a helpful, machine-readable message for each rejected parameter.
 */
public final class ApiParameterError {

    private final String userMessageGlobalisationCode;
    private final String defaultUserMessage;
    private final String parameterName;
    private final Object value;

    private ApiParameterError(final String userMessageGlobalisationCode, final String defaultUserMessage, final String parameterName,
            final Object value) {
        this.userMessageGlobalisationCode = userMessageGlobalisationCode;
        this.defaultUserMessage = defaultUserMessage;
        this.parameterName = parameterName;
        this.value = value;
    }

    public static ApiParameterError parameterError(final String userMessageGlobalisationCode, final String defaultUserMessage,
            final String parameterName, final Object value) {
        return new ApiParameterError(userMessageGlobalisationCode, defaultUserMessage, parameterName, value);
    }

    public String getUserMessageGlobalisationCode() {
        return this.userMessageGlobalisationCode;
    }

    public String getDefaultUserMessage() {
        return this.defaultUserMessage;
    }

    public String getParameterName() {
        return this.parameterName;
    }

    public Object getValue() {
        return this.value;
    }
}
