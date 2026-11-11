#!/usr/bin/env bash
# Puts one release on the server this runs on: the code and the images of one
# commit. Run by the Deploy workflow over SSH, whose key may run nothing else
# (the commit arrives as SSH_ORIGINAL_COMMAND), or by hand:
#
#   deploy/deploy.sh            the newest commit on main
#   deploy/deploy.sh <commit>   that commit: this is also how to go back
set -euo pipefail

# Everything is inside a function, so the whole file is read before the
# checkout below replaces it with the version of the release.
main() {
  cd "$(dirname "$(readlink -f "$0")")/.."

  local commit="${1:-${SSH_ORIGINAL_COMMAND:-}}"
  if [[ -n "$commit" && ! "$commit" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Not a commit: expected 40 hexadecimal characters." >&2
    exit 2
  fi

  git fetch --quiet origin main
  commit="${commit:-$(git rev-parse origin/main)}"
  git checkout --quiet --detach "$commit"

  # The images were built from this very commit and are tagged with it.
  export IMAGE_TAG="$commit"
  docker compose pull --quiet
  # Only what changed is restarted; --wait returns when everything is healthy, or fails.
  docker compose up --detach --wait --remove-orphans
  docker image prune --force --filter "until=168h" > /dev/null

  echo "Released $commit"
}

main "$@"
