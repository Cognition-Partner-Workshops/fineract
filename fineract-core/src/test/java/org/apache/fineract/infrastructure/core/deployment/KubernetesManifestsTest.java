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
package org.apache.fineract.infrastructure.core.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Guards the least-privilege settings of the workload manifests in the top-level kubernetes directory: pods must not
 * automount a service account token (none of them use the Kubernetes API) and every container must cap its ephemeral
 * storage.
 */
class KubernetesManifestsTest {

    private static final List<String> WORKLOAD_KINDS = List.of("Deployment", "StatefulSet", "DaemonSet", "ReplicaSet", "Job");

    @Test
    void podsDisableServiceAccountTokenAutomountAndContainersLimitEphemeralStorage() throws IOException {
        List<Path> manifests = manifests();
        assertFalse(manifests.isEmpty(), "no kubernetes manifests found");
        int workloads = 0;
        for (Path manifest : manifests) {
            for (Map<String, Object> doc : load(manifest)) {
                if (!WORKLOAD_KINDS.contains(doc.get("kind"))) {
                    continue;
                }
                workloads++;
                String where = manifest.getFileName() + " " + doc.get("kind") + "/" + map(doc.get("metadata")).get("name");
                Map<String, Object> podSpec = map(map(map(doc.get("spec")).get("template")).get("spec"));
                assertEquals(Boolean.FALSE, podSpec.get("automountServiceAccountToken"),
                        where + ": pod spec must set automountServiceAccountToken: false");
                List<Map<String, Object>> containers = new ArrayList<>(list(podSpec.get("containers")));
                containers.addAll(list(podSpec.get("initContainers")));
                assertFalse(containers.isEmpty(), where + ": no containers");
                for (Map<String, Object> container : containers) {
                    Map<String, Object> limits = map(map(container.get("resources")).get("limits"));
                    assertNotNull(limits.get("ephemeral-storage"),
                            where + " container " + container.get("name") + ": resources.limits.ephemeral-storage is required");
                }
            }
        }
        assertTrue(workloads > 0, "no workload resources found in kubernetes manifests");
    }

    private static List<Path> manifests() throws IOException {
        Path dir = Path.of("..", "kubernetes");
        if (!Files.isDirectory(dir)) {
            dir = Path.of("kubernetes");
        }
        assertTrue(Files.isDirectory(dir), "kubernetes directory not found from " + Path.of("").toAbsolutePath());
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml")).sorted().toList();
        }
    }

    private static List<Map<String, Object>> load(Path manifest) throws IOException {
        List<Map<String, Object>> docs = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(manifest)) {
            for (Object doc : new Yaml(new SafeConstructor(new LoaderOptions())).loadAll(reader)) {
                if (doc != null) {
                    docs.add(map(doc));
                }
            }
        }
        return docs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }
}
