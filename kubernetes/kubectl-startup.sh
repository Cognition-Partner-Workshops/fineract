#!/bin/bash
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

set -e

SECRET_NAME=fineract-tenants-db-secret

if ! kubectl get secret "$SECRET_NAME" >/dev/null 2>&1; then
    printf 'Kubernetes Secret %s is required. Create it with non-empty username and password keys before starting Fineract.\n' "$SECRET_NAME" >&2
    exit 1
fi

for key in username password; do
    if [ -z "$(kubectl get secret "$SECRET_NAME" -o "jsonpath={.data.${key}}" 2>/dev/null)" ]; then
        printf 'Kubernetes Secret %s must contain a non-empty %s key.\n' "$SECRET_NAME" "$key" >&2
        exit 1
    fi
done

echo "Setting up Fineract service configuration..."
kubectl apply -f fineractmysql-configmap.yml

echo
echo "Starting fineractmysql..."
kubectl apply -f fineractmysql-deployment.yml

echo "Waiting for fineractmysql to be ready..."
kubectl wait --for=condition=ready pod -l tier=fineractmysql --timeout=300s

echo
echo "Starting fineract server..."
kubectl apply -f fineract-server-deployment.yml

echo "Waiting for fineract-server to be ready..."
kubectl wait --for=condition=ready pod -l app=fineract-server --timeout=300s

echo "Fineract server is up and running"

echo
echo "Starting Mifos Community UI..."
kubectl apply -f fineract-mifoscommunity-deployment.yml

echo "Waiting for mifos-community to be ready..."
kubectl wait --for=condition=ready pod -l app=mifos-community --timeout=300s

echo "Mifos Community UI is up and running"

echo
echo "============================================"
echo "Fineract Kubernetes deployment is ready!"
echo "============================================"
echo
echo "To access the Mifos web application:"
echo "  kubectl port-forward service/mifos-community 9090:9090"
echo
echo "To access the Fineract API directly:"
echo "  kubectl port-forward service/fineract-server 8443:8443"
echo
echo "Change the seeded admin password on first login"
echo
echo "To check pod status:"
echo "  kubectl get pods"
echo
echo "To view logs:"
echo "  kubectl logs deployment/fineract-server"
echo "  kubectl logs deployment/mifos-community"
echo
