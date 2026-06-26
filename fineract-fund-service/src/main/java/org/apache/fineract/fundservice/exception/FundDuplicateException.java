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
 * Raised when a create/update violates a unique constraint, mirroring the monolith data-integrity codes
 * {@code error.msg.fund.duplicate.name} / {@code error.msg.fund.duplicate.externalId}. Mapped to HTTP 409.
 */
public class FundDuplicateException extends RuntimeException {

    private final String code;
    private final String parameterName;
    private final Object value;

    public FundDuplicateException(final String code, final String message, final String parameterName, final Object value,
            final Throwable cause) {
        super(message, cause);
        this.code = code;
        this.parameterName = parameterName;
        this.value = value;
    }

    public String getCode() {
        return this.code;
    }

    public String getParameterName() {
        return this.parameterName;
    }

    public Object getValue() {
        return this.value;
    }
}
