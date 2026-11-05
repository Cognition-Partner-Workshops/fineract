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
package org.apache.fineract.infrastructure.core.validator;

import java.util.regex.Pattern;

/**
 * Validates the structure of an e-mail address. All quantifiers are possessive so matching runs iteratively and cannot
 * overflow the stack on long input; the accepted language is unchanged because no character class overlaps the
 * separator that follows it.
 */
public final class EmailAddressValidator {

    private static final Pattern EMAIL_PATTERN = Pattern
            .compile("^[\\w!#$%&’*+/=?`{|}~^-]++(?:\\.[\\w!#$%&’*+/=?`{|}~^-]++)*+@(?:[a-zA-Z0-9-]++\\.)++[a-zA-Z]{2,6}$");

    private EmailAddressValidator() {}

    public static boolean isValid(final String email) {
        if (email == null || email.endsWith(".")) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
