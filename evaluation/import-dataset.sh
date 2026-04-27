#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

LANGFUSE_URL="${LANGFUSE_URL:-http://localhost:3000}"
LANGFUSE_PK="pk-lf-98fd1826-29f0-4151-9405-f51b91f5f140"
LANGFUSE_SK="sk-lf-366c6178-028b-4073-add7-235202f30b37"
DATASET_NAME="bank-golden-dataset"
DATASET_FILE="golden-dataset.json"

if [[ ! -f "$DATASET_FILE" ]]; then
  echo "ERROR: $DATASET_FILE not found in $(pwd)"
  exit 1
fi

echo "Importing golden dataset to Langfuse at $LANGFUSE_URL ..."

python3 - <<PYEOF
import json, urllib.request, urllib.error, base64, sys, os

base_url  = "${LANGFUSE_URL}"
pk        = "${LANGFUSE_PK}"
sk        = "${LANGFUSE_SK}"
ds_name   = "${DATASET_NAME}"
ds_file   = "${DATASET_FILE}"

auth = base64.b64encode(f"{pk}:{sk}".encode()).decode()
headers = {
    "Content-Type":  "application/json",
    "Authorization": f"Basic {auth}"
}

def post(path, body):
    data = json.dumps(body).encode()
    req  = urllib.request.Request(base_url + path, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

# Step 1 — Create dataset (idempotent: 200 or 409 both fine)
print(f"Creating dataset '{ds_name}' ...")
status, body = post("/api/public/datasets", {"name": ds_name})
if status in (200, 201, 409):
    print(f"  Dataset ready (HTTP {status})")
else:
    print(f"  WARNING: HTTP {status} — {body}")

# Step 2 — Import test cases
with open(ds_file) as f:
    dataset = json.load(f)

test_cases = dataset.get("testCases", [])
print(f"Importing {len(test_cases)} test cases ...")

ok = 0
fail = 0
for tc in test_cases:
    item = {
        "datasetName":    ds_name,
        "input":          {"id": tc["id"], "text": tc["input"], "language": tc.get("language","en")},
        "expectedOutput": tc.get("expectedOutput", {}),
        "metadata":       {"description": tc.get("description",""), "tags": tc.get("tags",[])}
    }
    status, body = post("/api/public/dataset-items", item)
    if status in (200, 201):
        print(f"  ✅  {tc['id']} — {tc.get('description','')[:60]}")
        ok += 1
    else:
        print(f"  ❌  {tc['id']} — HTTP {status}: {body}")
        fail += 1

print(f"\nDone: {ok} imported, {fail} failed.")
if fail > 0:
    sys.exit(1)
PYEOF
