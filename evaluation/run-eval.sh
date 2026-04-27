#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# ── Validate env ──────────────────────────────────────────────────────────────
if [[ -z "${GOOGLE_API_KEY:-}" ]]; then
  echo "ERROR: GOOGLE_API_KEY is not set."
  echo "Usage: GOOGLE_API_KEY=<your-key> ./run-eval.sh"
  exit 1
fi

# ── Check orchestrator is up ──────────────────────────────────────────────────
ORCHESTRATOR_URL="${ORCHESTRATOR_URL:-http://localhost:8080}"
echo "Checking orchestrator at ${ORCHESTRATOR_URL}/actuator/health ..."
if ! curl -sf "${ORCHESTRATOR_URL}/actuator/health" > /dev/null 2>&1; then
  echo "WARNING: Orchestrator does not appear to be running at ${ORCHESTRATOR_URL}."
  echo "         Start it first with: cd orchestrator && mvn spring-boot:run"
  echo "         Continuing anyway..."
fi

# ── Run evaluation ────────────────────────────────────────────────────────────
echo "Starting LLM-as-a-Judge evaluation..."
mvn -q spring-boot:run \
  -Dspring-boot.run.jvmArguments="-DGOOGLE_API_KEY=${GOOGLE_API_KEY}" \
  -Dspring-boot.run.arguments="--orchestrator.url=${ORCHESTRATOR_URL}"

EXIT_CODE=$?

if [[ $EXIT_CODE -eq 0 ]]; then
  echo ""
  echo "All test cases passed."
elif [[ $EXIT_CODE -eq 1 ]]; then
  echo ""
  echo "Some test cases failed (score below threshold)."
  exit 1
else
  echo ""
  echo "Evaluation error (exit code $EXIT_CODE)."
  exit $EXIT_CODE
fi
