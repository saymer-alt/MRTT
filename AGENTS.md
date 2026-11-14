# AGENTS.md — minimal saymer-alt agent policy

Coding agents must follow the [shared task contract](https://github.com/saymer-alt/saymer-alt/blob/main/AGENT-TASK-CONTRACT.md) and [shared retry budget](https://github.com/saymer-alt/saymer-alt/blob/main/AGENT-RETRY-BUDGET.md). Respect project-specific README/CONTRIBUTING, secrets and safety rules.

**Token economy:** after **2 identical FAIL** with unchanged evidence, change hypothesis or mark BLOCKED. For one blocker, **3 fix attempts OR 3 full heavy builds** (whichever first, across CI/local/agents/sessions) means **STOP**, preserve diagnostics and ask owner GO. Do not rerun an unchanged PASS; run cheap checks before expensive builds. Never skip required tests or treat CI as hardware acceptance.

Do not merge, release, deploy or change real systems without separate explicit owner approval. This file is intentionally short; load linked documents only when relevant.
