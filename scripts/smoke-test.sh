#!/usr/bin/env bash
# End-to-end smoke test of the Docker Compose stack, entirely through nginx (http://localhost:8080 by default).
# Used by the "docker" CI job; also handy locally after `docker compose up -d --wait`. Needs curl and jq.
set -euo pipefail

BASE="${1:-http://localhost:8080}"
fail() { echo "FAIL: $*" >&2; exit 1; }
json() { curl -fsS -H 'Content-Type: application/json' "$@"; }

echo "== health"
curl -fsS "$BASE/actuator/health" | jq -e '.status == "UP"' >/dev/null || fail "backend is not UP"

echo "== SPA and security headers"
headers=$(curl -fsS -D - -o /dev/null "$BASE/login")
grep -qi '^content-security-policy:.*frame-ancestors' <<<"$headers" || fail "missing Content-Security-Policy"
grep -qi '^x-content-type-options: nosniff' <<<"$headers" || fail "missing X-Content-Type-Options"
grep -qi '^referrer-policy:' <<<"$headers" || fail "missing Referrer-Policy"

email="smoke-$(date +%s)-$RANDOM@example.com"
password="smoke-pass-$RANDOM"

echo "== register $email"
json -X POST "$BASE/api/auth/register" \
  -d "$(jq -n --arg e "$email" --arg p "$password" '{email: $e, password: $p, displayName: "Smoke Test"}')" \
  | jq -e '.token | length > 20' >/dev/null || fail "register returned no token"

echo "== login"
token=$(json -X POST "$BASE/api/auth/login" \
  -d "$(jq -n --arg e "$email" --arg p "$password" '{email: $e, password: $p}')" | jq -r '.token')
[[ -n "$token" && "$token" != "null" ]] || fail "login returned no token"
auth=(-H "Authorization: Bearer $token")

echo "== me"
curl -fsS "${auth[@]}" "$BASE/api/auth/me" | jq -e --arg e "$email" '.email == $e' >/dev/null || fail "/me"

echo "== wrong password is 401"
code=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -X POST "$BASE/api/auth/login" \
  -d "$(jq -n --arg e "$email" '{email: $e, password: "definitely-wrong"}')")
[[ "$code" == 401 ]] || fail "expected 401 for a wrong password, got $code"

echo "== create, move, stale move (PostgreSQL + Flyway V2)"
created=$(json "${auth[@]}" -H 'X-Time-Zone: Africa/Tunis' -X POST "$BASE/api/applications" \
  -d '{"company": "Smoke Co", "role": "Tester", "status": "APPLIED", "tags": ["ci"]}')
id=$(jq -r '.id' <<<"$created")
version=$(jq -r '.version' <<<"$created")
json "${auth[@]}" -X PATCH "$BASE/api/applications/$id/status" \
  -d "{\"status\": \"INTERVIEW\", \"version\": $version}" | jq -e '.status == "INTERVIEW"' >/dev/null \
  || fail "status change"
code=$(curl -s -o /dev/null -w '%{http_code}' "${auth[@]}" -H 'Content-Type: application/json' \
  -X PATCH "$BASE/api/applications/$id/status" -d "{\"status\": \"OFFER\", \"version\": $version}")
[[ "$code" == 409 ]] || fail "expected 409 for a stale version, got $code"

echo "== stats"
curl -fsS "${auth[@]}" "$BASE/api/stats" | jq -e '.total == 1 and .interviewRate == 100' >/dev/null || fail "stats"

echo "All smoke checks passed."
