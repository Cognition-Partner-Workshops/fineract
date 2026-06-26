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
package org.apache.fineract.fundservice.data;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Write-model DTO for creating/updating a fund, mirroring the monolith {@code FundRequest} contract.
 */
@Data
@NoArgsConstructor
public class FundRequest {

    private String name;
    private String externalId;

    /**
     * Whether the JSON key was present in the request body. Lets the update path distinguish an absent field (leave
     * untouched) from one explicitly set to null/empty (clear it), mirroring the monolith's {@code parameterExists}
     * semantics.
     */
    @Schema(hidden = true)
    private boolean nameProvided;
    @Schema(hidden = true)
    private boolean externalIdProvided;
}
