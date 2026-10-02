#!/usr/bin/env bash
set -euo pipefail
# CI uses the same user-managed Kotlin SDK and executable entry point as the lane.
if [[ ! -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  curl --fail --silent --show-error --location https://get.sdkman.io | bash
fi
set +u
# shellcheck source=/dev/null
source "$HOME/.sdkman/bin/sdkman-init.sh"
if [[ ! -d "$HOME/.sdkman/candidates/kotlin/2.2.21" ]]; then
  sdk install kotlin 2.2.21
fi
sdk use kotlin 2.2.21
set -u
mkdir -p "$HOME/.local/bin"
ln -sf "$HOME/.sdkman/candidates/kotlin/2.2.21/bin/kotlin" "$HOME/.local/bin/kotlin"
ln -sf "$HOME/.sdkman/candidates/kotlin/2.2.21/bin/kotlinc" "$HOME/.local/bin/kotlinc"
echo "$HOME/.local/bin" >> "$GITHUB_PATH"
"$HOME/.local/bin/kotlin" -version
