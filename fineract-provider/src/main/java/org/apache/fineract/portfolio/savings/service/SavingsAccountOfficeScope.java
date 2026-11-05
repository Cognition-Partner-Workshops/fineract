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
package org.apache.fineract.portfolio.savings.service;

import java.util.Optional;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;

/**
 * Restricts savings/deposit account queries to accounts whose client or group office is within the caller's office
 * hierarchy. Requests without an authenticated user (background jobs) fall back to the head office hierarchy.
 */
public final class SavingsAccountOfficeScope {

    private SavingsAccountOfficeScope() {}

    public static String sqlFilter(final String savingsAccountIdColumn) {
        return " and exists (select 1 from m_savings_account scope_sa left join m_client scope_c on scope_c.id = scope_sa.client_id"
                + " left join m_group scope_g on scope_g.id = scope_sa.group_id"
                + " join m_office scope_o on scope_o.id = coalesce(scope_c.office_id, scope_g.office_id)" + " where scope_sa.id = "
                + savingsAccountIdColumn + " and scope_o.hierarchy like ?) ";
    }

    public static String hierarchySearchString(final PlatformSecurityContext context) {
        final AppUser currentUser = context == null ? null : context.getAuthenticatedUserIfPresent();
        return Optional.ofNullable(currentUser).map(user -> user.getOffice().getHierarchy()).orElse(".") + "%";
    }
}
