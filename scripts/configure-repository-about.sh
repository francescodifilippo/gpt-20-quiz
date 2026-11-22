#!/usr/bin/env bash
# Configure the public GitHub "About" section for this repository.
# Use an authenticated GitHub CLI account with repository admin permission.
set -euo pipefail

REPO="francescodifilippo/gpt-20-quiz"
DESCRIPTION="Multilingual Android Twenty Questions game powered by ChatGPT via SIWC. Choose your role, language, model and reasoning effort; play up to 20 turns."
TOPICS=(
  "android"
  "kotlin"
  "jetpack-compose"
  "twenty-questions"
  "ai-game"
  "chatgpt"
  "openai"
  "siwc"
  "oauth2"
  "multilingual"
  "reasoning"
  "sign-in-with-chatgpt"
)

command -v gh >/dev/null || { echo "GitHub CLI (gh) is required" >&2; exit 1; }
gh auth status --hostname github.com >/dev/null

# --add-topic preserves any existing topics, and the call is safe to rerun.
TOPICS_CSV="$(IFS=,; echo "${TOPICS[*]}")"
gh repo edit "$REPO" --description "$DESCRIPTION" --add-topic "$TOPICS_CSV"

echo "GitHub repository About after update:"
gh api "repos/$REPO" --jq '{description: .description, topics: .topics}'
