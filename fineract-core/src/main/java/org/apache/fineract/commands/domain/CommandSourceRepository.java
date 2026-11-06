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
package org.apache.fineract.commands.domain;

import java.time.OffsetDateTime;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface CommandSourceRepository extends JpaRepository<CommandSource, Long>, JpaSpecificationExecutor<CommandSource> {

    CommandSource findByActionNameAndEntityNameAndIdempotencyKey(String actionName, String entityName, String idempotencyKey);

    @Modifying(flushAutomatically = true)
    @Query("delete from CommandSource c where c.status = :status and c.madeOnDate is not null and c.madeOnDate <= :dateForPurgeCriteria")
    void deleteOlderEventsWithStatus(@Param("status") Integer status, @Param("dateForPurgeCriteria") OffsetDateTime dateForPurgeCriteria);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update CommandSource c
            set c.checker = :checker, c.checkedOnDate = :checkedOnDate, c.approvalClaimToken = :approvalClaimToken
            where c.id = :id and c.status = :status and c.checkedOnDate is null
            """)
    int claimForApproval(@Param("id") Long id, @Param("checker") AppUser checker, @Param("checkedOnDate") OffsetDateTime checkedOnDate,
            @Param("approvalClaimToken") String approvalClaimToken, @Param("status") Integer status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update CommandSource c
            set c.checker = null, c.checkedOnDate = null, c.approvalClaimToken = null
            where c.id = :id and c.checker = :checker and c.approvalClaimToken = :approvalClaimToken and c.status = :status
            """)
    int releaseApprovalClaim(@Param("id") Long id, @Param("checker") AppUser checker,
            @Param("approvalClaimToken") String approvalClaimToken, @Param("status") Integer status);

}
