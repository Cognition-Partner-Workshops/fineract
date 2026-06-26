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
package org.apache.fineract.portfolio.client.adapter;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.exception.StaffNotFoundException;
import org.apache.fineract.portfolio.client.domain.ClientServiceStaffRepository;
import org.apache.fineract.portfolio.client.port.StaffPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fineract.client-service.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SharedDbStaffAdapter implements StaffPort {

    private final ClientServiceStaffRepository staffRepository;

    @Override
    public Staff findById(Long staffId) {
        return staffRepository.findById(staffId).orElseThrow(() -> new StaffNotFoundException(staffId));
    }

    @Override
    public Staff findByOffice(Long staffId, Long officeId) {
        return staffRepository.findByIdAndOfficeId(staffId, officeId).orElseThrow(() -> new StaffNotFoundException(staffId));
    }
}
