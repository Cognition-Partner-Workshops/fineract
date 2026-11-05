/*
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
package org.apache.fineract.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class KubernetesSecretConfigurationSecurityTest {

    private static final Pattern INLINE_PASSWORD = Pattern.compile(
            "(?i)(--from-literal=[^\\s]*password=|\\b(?:password|passwd|pwd)\\s*=\\s*[^$\\s]+|\\b(?:password|passwd|pwd):\\s*['\"]?[^$\\s{]+)");
    private static final Pattern CREDENTIAL_ECHO = Pattern
            .compile("(?im)^\\s*echo\\s+[^\\n]*(?:default\\s+credentials|username\\s*:|password\\s*:)");
    private static final Pattern DEFAULT_CREDENTIAL_ECHO = Pattern
            .compile("(?im)^\\s*echo\\s+[\"']?\\s*(?:default\\s+credentials|username\\s*:|password\\s*:)");
    private static final List<String> KUBERNETES_FILES = List.of("kubectl-startup.sh", "kubectl-shutdown.sh",
            "fineract-server-deployment.yml", "fineract-mifoscommunity-deployment.yml", "fineractmysql-deployment.yml",
            "fineractmysql-configmap.yml");

    @Test
    void startupRequiresOperatorSecretWithoutCreatingOrEchoingCredentials() throws IOException {
        String startup = Files.readString(kubernetesDirectory().resolve("kubectl-startup.sh"));

        assertFalse(startup.contains("kubectl create secret"), "Startup must not create a generated credential fallback");
        assertTrue(startup.contains("SECRET_NAME=fineract-tenants-db-secret") && startup.contains("kubectl get secret \"$SECRET_NAME\"")
                && startup.contains("for key in username password"), "Startup must verify the operator-supplied database Secret");
        assertTrue(startup.contains("Kubernetes Secret %s") && startup.contains("fineract-tenants-db-secret"),
                "Missing Secret errors must tell the operator what is required");
        assertFalse(CREDENTIAL_ECHO.matcher(startup).find(), "Startup must not echo credentials");
        assertFalse(DEFAULT_CREDENTIAL_ECHO.matcher(startup).find(), "Startup must not print the seeded default username or password");
    }

    @Test
    void kubernetesScriptsAndManifestsContainNoInlinePasswordValues() throws IOException {
        Path kubernetesDirectory = kubernetesDirectory();
        for (String fileName : KUBERNETES_FILES) {
            String contents = Files.readString(kubernetesDirectory.resolve(fileName));
            assertFalse(INLINE_PASSWORD.matcher(contents).find(), () -> fileName + " contains an inline password value");
            assertFalse(CREDENTIAL_ECHO.matcher(contents).find(), () -> fileName + " echoes credentials");
        }
    }

    @Test
    void kubernetesServicesAreNotExposedAsLoadBalancers() throws IOException {
        try (var files = Files.list(kubernetesDirectory())) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".yml")).toList()) {
                boolean inService = false;
                for (String line : Files.readAllLines(file)) {
                    String trimmedLine = line.trim();
                    if ("---".equals(trimmedLine)) {
                        inService = false;
                    }
                    if ("kind: Service".equals(trimmedLine)) {
                        inService = true;
                    }
                    assertFalse(inService && "type: LoadBalancer".equals(trimmedLine),
                            () -> file.getFileName() + " exposes a Kubernetes Service as a LoadBalancer");
                }
            }
        }
    }

    private static Path kubernetesDirectory() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        if (Files.isDirectory(currentDirectory.resolve("kubernetes"))) {
            return currentDirectory.resolve("kubernetes");
        }
        return currentDirectory.getParent().resolve("kubernetes");
    }
}
