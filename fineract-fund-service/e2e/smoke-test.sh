#!/usr/bin/env bash
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
# Fund carve-out parity / E2E smoke test (the PRIMARY validation gate).
#
# Brings up the database-per-service compose stack (fund-db + fund-service + gateway) and
# asserts, THROUGH THE GATEWAY (http://localhost:8080):
#   1. GET /actuator/health                              -> 200 (gateway up)
#   2. GET /fineract-provider/api/v1/funds WITHOUT auth  -> 401 (route forwards + auth enforced)
#   3. With valid Basic auth (mifos/password): POST a fund then GET it by id -> fields round-trip
#      and match the monolith contract (id/name/externalId); the list is ordered by name.
#
# Exits non-zero on ANY failed assertion and tears the stack down at the end.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../docker-compose-fund-service.yml"
PROJECT_NAME="fund-e2e"
COMPOSE=(docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}")

GATEWAY="http://localhost:8080"
EXTERNAL_FUNDS_PATH="/fineract-provider/api/v1/funds"
AUTH_USER="mifos"
AUTH_PASS="password"

PASS_COUNT=0
fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { PASS_COUNT=$((PASS_COUNT + 1)); echo "PASS: $*"; }

cleanup() {
  echo "----------------------------------------------------------------"
  echo "Tearing down compose stack..."
  "${COMPOSE[@]}" down -v --remove-orphans || true
}
trap cleanup EXIT

echo "================================================================"
echo " Fund carve-out E2E smoke test"
echo "================================================================"

echo "---- Building images (this builds ONLY the two carve-out modules) ----"
# Build sequentially to bound peak memory of the in-image Gradle builds.
"${COMPOSE[@]}" build fund-service
"${COMPOSE[@]}" build gateway

echo "---- Starting stack ----"
"${COMPOSE[@]}" up -d

# ---------------------------------------------------------------------------
# Assertion 1: gateway health -> 200
# ---------------------------------------------------------------------------
echo "---- Waiting for gateway health (GET ${GATEWAY}/actuator/health) ----"
HEALTH_CODE=000
for i in $(seq 1 60); do
  HEALTH_CODE="$(curl -s -o /dev/null -w '%{http_code}' "${GATEWAY}/actuator/health" || true)"
  if [ "${HEALTH_CODE}" = "200" ]; then
    break
  fi
  sleep 5
done
[ "${HEALTH_CODE}" = "200" ] || { "${COMPOSE[@]}" logs --tail=120; fail "gateway /actuator/health returned ${HEALTH_CODE}, expected 200"; }
pass "gateway /actuator/health -> 200"

# Wait until the route forwards to a fully-booted fund-service (unauth should be 401, not 503/500).
echo "---- Waiting for fund-service route to be live through the gateway ----"
ROUTE_CODE=000
for i in $(seq 1 60); do
  ROUTE_CODE="$(curl -s -o /dev/null -w '%{http_code}' "${GATEWAY}${EXTERNAL_FUNDS_PATH}" || true)"
  if [ "${ROUTE_CODE}" = "401" ]; then
    break
  fi
  sleep 5
done

# ---------------------------------------------------------------------------
# Assertion 2: unauthenticated request through the gateway -> 401
# ---------------------------------------------------------------------------
UNAUTH_CODE="$(curl -s -o /dev/null -w '%{http_code}' "${GATEWAY}${EXTERNAL_FUNDS_PATH}" || true)"
[ "${UNAUTH_CODE}" = "401" ] || { "${COMPOSE[@]}" logs --tail=120; fail "unauthenticated GET ${EXTERNAL_FUNDS_PATH} returned ${UNAUTH_CODE}, expected 401"; }
pass "unauthenticated GET ${EXTERNAL_FUNDS_PATH} via gateway -> 401"

# ---------------------------------------------------------------------------
# Assertion 3: authed CRUD round-trip / parity through the gateway
# ---------------------------------------------------------------------------
echo "---- POST a fund (authed) through the gateway ----"
FUND_NAME="Growth Fund"
FUND_EXTID="EXT-SMOKE-001"
CREATE_BODY="$(curl -s -u "${AUTH_USER}:${AUTH_PASS}" -H 'Content-Type: application/json' \
  -X POST "${GATEWAY}${EXTERNAL_FUNDS_PATH}" \
  -d "{\"name\":\"${FUND_NAME}\",\"externalId\":\"${FUND_EXTID}\"}")"
echo "POST response: ${CREATE_BODY}"
NEW_ID="$(echo "${CREATE_BODY}" | jq -r '.resourceId')"
[ -n "${NEW_ID}" ] && [ "${NEW_ID}" != "null" ] || fail "POST did not return a resourceId (body: ${CREATE_BODY})"
pass "POST fund -> resourceId=${NEW_ID}"

echo "---- GET the fund by id (authed) through the gateway ----"
GET_BODY="$(curl -s -u "${AUTH_USER}:${AUTH_PASS}" "${GATEWAY}${EXTERNAL_FUNDS_PATH}/${NEW_ID}")"
echo "GET response: ${GET_BODY}"
GOT_ID="$(echo "${GET_BODY}" | jq -r '.id')"
GOT_NAME="$(echo "${GET_BODY}" | jq -r '.name')"
GOT_EXTID="$(echo "${GET_BODY}" | jq -r '.externalId')"
[ "${GOT_ID}" = "${NEW_ID}" ] || fail "id mismatch: got ${GOT_ID}, expected ${NEW_ID}"
[ "${GOT_NAME}" = "${FUND_NAME}" ] || fail "name mismatch: got '${GOT_NAME}', expected '${FUND_NAME}'"
[ "${GOT_EXTID}" = "${FUND_EXTID}" ] || fail "externalId mismatch: got '${GOT_EXTID}', expected '${FUND_EXTID}'"
pass "GET fund by id round-trips id/name/externalId (parity with monolith contract)"

echo "---- Create more funds and assert the list is ordered by name ----"
curl -s -u "${AUTH_USER}:${AUTH_PASS}" -H 'Content-Type: application/json' \
  -X POST "${GATEWAY}${EXTERNAL_FUNDS_PATH}" -d '{"name":"Alpha Fund"}' > /dev/null
curl -s -u "${AUTH_USER}:${AUTH_PASS}" -H 'Content-Type: application/json' \
  -X POST "${GATEWAY}${EXTERNAL_FUNDS_PATH}" -d '{"name":"Zeta Fund"}' > /dev/null
LIST_BODY="$(curl -s -u "${AUTH_USER}:${AUTH_PASS}" "${GATEWAY}${EXTERNAL_FUNDS_PATH}")"
echo "LIST response: ${LIST_BODY}"
NAMES="$(echo "${LIST_BODY}" | jq -r '.[].name')"
SORTED="$(echo "${NAMES}" | LC_ALL=C sort)"
[ "${NAMES}" = "${SORTED}" ] || fail "fund list is not ordered by name:\n${NAMES}"
pass "GET funds list is ordered by name"

echo "================================================================"
echo " SMOKE TEST PASSED (${PASS_COUNT} assertions)"
echo "================================================================"
