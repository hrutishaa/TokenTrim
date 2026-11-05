#!/usr/bin/env bash
# Hit a running TokenTrim server twice with the verbose fixture and print savings headers.
set -euo pipefail
BASE="${1:-http://localhost:8080}"
BODY="${2:-samples/verbose-request.json}"

echo "Request 1 (expect cache MISS)"
curl -sS -D - -o /tmp/tokentrim-1.json \
  -H 'Content-Type: application/json' \
  --data @"$BODY" \
  "$BASE/v1/chat/completions"
echo
echo "Body:"
head -c 500 /tmp/tokentrim-1.json
echo
echo
echo "Request 2 (expect cache HIT)"
curl -sS -D - -o /tmp/tokentrim-2.json \
  -H 'Content-Type: application/json' \
  --data @"$BODY" \
  "$BASE/v1/chat/completions"
echo
echo "Stats:"
curl -sS "$BASE/v1/tokentrim/stats"
echo
