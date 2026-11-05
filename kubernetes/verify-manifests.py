#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
"""Checks that every Deployment in this directory does not auto-mount a
service account token and sets ephemeral-storage requests and limits on
each container. Requires PyYAML. Usage: python3 kubernetes/verify-manifests.py"""

import pathlib
import sys

import yaml

failures = []
for path in sorted(pathlib.Path(__file__).parent.glob("*.yml")):
    for doc in yaml.safe_load_all(path.read_text()):
        if not doc or doc.get("kind") != "Deployment":
            continue
        name = f"{path.name}:{doc['metadata']['name']}"
        pod = doc["spec"]["template"]["spec"]
        if pod.get("automountServiceAccountToken") is not False:
            failures.append(f"{name}: automountServiceAccountToken is not false")
        for container in pod.get("containers", []):
            resources = container.get("resources", {})
            for section in ("requests", "limits"):
                if "ephemeral-storage" not in resources.get(section, {}):
                    failures.append(f"{name}/{container['name']}: no ephemeral-storage {section}")
        if not any(f.startswith(name) for f in failures):
            print(f"PASS {name}")

for failure in failures:
    print(f"FAIL {failure}")
sys.exit(1 if failures else 0)
