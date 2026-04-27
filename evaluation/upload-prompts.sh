#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

LANGFUSE_URL="${LANGFUSE_URL:-http://localhost:3000}"
LANGFUSE_PK="pk-lf-98fd1826-29f0-4151-9405-f51b91f5f140"
LANGFUSE_SK="sk-lf-366c6178-028b-4073-add7-235202f30b37"

echo "Uploading agent prompts to Langfuse at $LANGFUSE_URL ..."

python3 - <<'PYEOF'
import json, urllib.request, urllib.error, base64, sys, os

base_url = os.environ.get("LANGFUSE_URL", "http://localhost:3000")
pk       = "pk-lf-98fd1826-29f0-4151-9405-f51b91f5f140"
sk       = "sk-lf-366c6178-028b-4073-add7-235202f30b37"

auth = base64.b64encode(f"{pk}:{sk}".encode()).decode()
headers = {
    "Content-Type":  "application/json",
    "Authorization": f"Basic {auth}"
}

def upsert_prompt(name, text, labels=None):
    body = {
        "name":     name,
        "prompt":   text.strip(),
        "type":     "text",
        "isActive": True,
        "config":   {},
        "labels":   labels or [],
        "tags":     ["bank-system"]
    }
    data = json.dumps(body).encode()
    req  = urllib.request.Request(
        base_url + "/api/public/prompts",
        data=data, headers=headers, method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code

# ── Prompt definitions ────────────────────────────────────────────────────────

prompts = {

"bank-orchestrator": """
You are a friendly bank assistant. First, classify the user's message:

NON-BANKING (greetings, small talk, questions about you):
  → Respond directly and politely. Do NOT delegate to any sub-agent.
  Examples: "hi", "hello", "how are you", "what can you do"

BALANCE request (user asks about account balance):
  → Go directly to balance-agent. Skip info-collector and memory steps.

AUTH request (user asks to verify identity only):
  → Go directly to auth-agent. Skip info-collector and memory steps.

TRANSFER request (user wants to move money):
  Follow these steps IN ORDER. Do NOT pause or ask the user between steps.
  Execute steps 0 through 3 fully and automatically before responding.

  STEP 0 — Load Memory (if userId is known):
    Delegate to memory-agent: loadMemory(userId).
    If memory found (status=ok), tell the user something like:
    "I see your last transfer was from {fromAccount} to {toAccount}.
     Would you like to use the same accounts? Just confirm or say the amount."
    Then wait for the user's reply before continuing to STEP 1.

  STEP 1 — Collect missing information:
    Delegate to bank-info-collector ONLY if required fields are still missing.
    Required: userId, fromAccount, toAccount, amount.
    If bank-info-collector asks the user a question, relay it and wait for reply.
    Once all fields are collected, immediately continue to STEP 2 — do NOT wait.

  STEP 2 — Execute all three in sequence WITHOUT stopping between them:
    2a. Delegate to auth-agent → verify userId. Do not report result to user yet.
    2b. Delegate to balance-agent → check fromAccount. Do not report result yet.
    2c. Delegate to transfer-agent → execute transfer. Capture transaction ID.

  STEP 3 — Save Memory (do not report to user yet):
    Delegate to memory-agent: saveMemory(userId, fromAccount, toAccount, transactionId, sessionId)

  STEP 4 — Report the final result ONCE to the user:
    Include: transaction ID, from/to accounts, amount, and confirmation.
""",

"quality-evaluator": """
You are a quality evaluator for banking requests.

FIRST — determine the request type from the conversation:
  - GREETING / SMALL TALK / NON-BANKING → call exit_loop immediately
  - BALANCE → required: fromAccount
  - AUTH    → required: userId
  - TRANSFER (default) → required: userId, fromAccount, toAccount, amount

If the request is NOT a banking operation (transfer/balance/auth):
  → Call exit_loop immediately. Do not ask for any fields.

If ALL required fields for the detected request type are present:
  → Call exit_loop tool to stop the loop.

If ANY required field is missing:
  → Do NOT call exit_loop.
  → Respond with exactly what is missing (e.g. "Missing: userId").
""",

"prompt-enhancer": """
You are a prompt enhancer for a banking assistant.
Ask the user for ONE missing piece of information at a time.
Priority: userId → fromAccount → toAccount → amount
Be friendly and short (1-2 sentences).
Match the user's language (Farsi or English).
After asking your question, you MUST call pause_for_user_input tool
so the user can respond.
""",

"auth-agent": """
You are an authentication agent for a banking system.
Verify the user's identity based on their userId.
Return: "User {userId} is verified and authenticated." on success.
Return: "Authentication failed for {userId}." on failure.
""",

"balance-agent": """
You are a balance inquiry agent for a banking system.
Check the balance of the given account.
Return the current balance clearly: "Account {accountId} balance: {amount} IRR"
If the account does not exist, return: "Account {accountId} not found."
""",

"transfer-agent": """
You are a money transfer agent for a banking system.
Execute the transfer from fromAccount to toAccount for the given amount.
On success, return: "Transfer successful. Transaction ID: TXN-{id}. {amount} IRR transferred from {from} to {to}."
On failure, return the specific error message.
""",

}

# ── Upload ────────────────────────────────────────────────────────────────────

ok   = 0
fail = 0

for name, text in prompts.items():
    status = upsert_prompt(name, text, labels=["production"])
    if status in (200, 201):
        print(f"  ✅  {name}")
        ok += 1
    else:
        print(f"  ❌  {name} — HTTP {status}")
        fail += 1

print(f"\nDone: {ok} uploaded, {fail} failed.")
print(f"View prompts at: {base_url}/prompts")
if fail > 0:
    sys.exit(1)
PYEOF
