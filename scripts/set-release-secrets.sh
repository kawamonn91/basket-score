#!/usr/bin/env bash
# CD（.github/workflows/release.yml）が使う GitHub Secrets を、手元の鍵から登録する。
# 値はコマンドの引数に出さず、標準入力から gh に渡す。
#
# 使い方（リポジトリの直下で）: bash scripts/set-release-secrets.sh
# 要るもの: basket-score.jks・keystore.properties（どちらも Git の対象外。README の「署名鍵」）、gh のログイン
set -euo pipefail

repo="${REPO:-kawamonn91/basket-score}"
cd "$(dirname "$0")/.."

for f in basket-score.jks keystore.properties; do
  [ -f "$f" ] || { echo "$f がありません" >&2; exit 1; }
done

prop() { grep "^$2=" "$1" | head -n 1 | cut -d= -f2- | tr -d '\r\n'; }

base64 -w0 basket-score.jks | gh secret set RELEASE_KEYSTORE_BASE64 -R "$repo"
prop keystore.properties storePassword | gh secret set RELEASE_KEYSTORE_PASSWORD -R "$repo"
prop keystore.properties keyAlias | gh secret set RELEASE_KEY_ALIAS -R "$repo"
prop keystore.properties keyPassword | gh secret set RELEASE_KEY_PASSWORD -R "$repo"

gh secret list -R "$repo"
