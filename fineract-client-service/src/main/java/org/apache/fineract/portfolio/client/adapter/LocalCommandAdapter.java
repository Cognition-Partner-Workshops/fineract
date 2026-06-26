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
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.client.port.CommandPort;
import org.springframework.stereotype.Component;

/**
 * Local command adapter that delegates to the existing command processor. When running within the monolith WAR, this
 * uses the standard maker-checker flow. When running standalone, the command processor is simplified to direct
 * execution.
 *
 * <p>
 * The CommandWrapper passed in must already contain the JSON body (via CommandWrapperBuilder.withJson()), since
 * PortfolioCommandSourceWritePlatformServiceImpl.logCommandSource extracts JSON from wrapper.getJson().
 * </p>
 */
@Component
@RequiredArgsConstructor
public class LocalCommandAdapter implements CommandPort {

    private final PortfolioCommandSourceWritePlatformService commandSourceWritePlatformService;

    @Override
    public CommandProcessingResult processCommand(CommandWrapper wrapper) {
        return commandSourceWritePlatformService.logCommandSource(wrapper);
    }
}
