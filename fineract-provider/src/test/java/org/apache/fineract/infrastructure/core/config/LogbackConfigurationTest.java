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
package org.apache.fineract.infrastructure.core.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.qos.logback.classic.LoggerContext;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class LogbackConfigurationTest {

    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void restoreLogging() {
        loggingSystem.cleanUp();
        ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        loggingSystem.initialize(new LoggingInitializationContext(new StandardEnvironment()), "classpath:logback.xml", null);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "false", "true" })
    void configuresSelectedAppender(String jsonLogging) {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        StandardEnvironment environment = new StandardEnvironment();
        if (!jsonLogging.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of("fineract.logging.json.enabled", jsonLogging)));
        }
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment),
                Path.of("src/main/resources/logback-spring.xml").toUri().toString(), null);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        boolean json = Boolean.parseBoolean(jsonLogging);
        assertNotNull(root.getAppender(json ? "JSON" : "CONSOLE"));
        assertNull(root.getAppender(json ? "CONSOLE" : "JSON"));
    }
}
