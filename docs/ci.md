# CI・CD と自動マージ

## CI（`.github/workflows/ci.yml`）

PR（main 向け）と main への push のたびに、GitHub Actions のジョブ `build` が次を行う。

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`（単体テスト・lint・デバッグ版のビルド）
- デバッグ版の APK（`app-debug`）と、テスト・lint のレポート（`reports`）を成果物として 7 日残す

JDK は手元と同じ Temurin 25。足りない SDK は AGP が取得する。アクションはコミットの SHA で固定している（更新するときは SHA ごと書き換える）。

## main の保護と自動マージ

- main は `build` が通らないとマージできない（ブランチの保護。レビューは必須にしない。管理者は対象外にしてあり、緊急時は直接 push できる）
- リポジトリで自動マージを有効にし、マージしたブランチは自動で消える
- PR を作ったら、自動マージを予約する。`build` が通った時点でマージされ、失敗したらマージされない

```bash
gh pr create --base main --title "…" --body "…"
gh pr merge --auto --merge   # build が通ったらマージする（マージコミット）
```

## CD（`.github/workflows/release.yml`）

`v` で始まるタグ（`v1.0.0` など）を push すると、ジョブ `release` が次を行う。

1. タグが `app/build.gradle.kts` の `versionName` と合っているか確かめる（`v` + versionName）
2. GitHub Secrets から署名鍵を書き出す
3. 単体テストとリリース版のビルド（R8 で縮める）
4. `apksigner` で署名を確かめる。証明書の SHA-256 がストアに出す版と同じか（ワークフローの `EXPECTED_CERT_SHA256`）も見る
5. `basket-score-<版>.apk` と `SHA256SUMS` を GitHub Releases に置く

手で動かす（`gh workflow run release.yml`）と、4 までを行ってリリースは作らない（CD の動作確認用）。

### GitHub Secrets

| 名前 | 中身 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `basket-score.jks` を base64 にしたもの |
| `RELEASE_KEYSTORE_PASSWORD` | `keystore.properties` の `storePassword` |
| `RELEASE_KEY_ALIAS` | `keyAlias` |
| `RELEASE_KEY_PASSWORD` | `keyPassword` |

登録（し直し）は `bash scripts/set-release-secrets.sh`。手元のファイルから読み、値をコマンドの引数に出さずに標準入力から `gh secret set` に渡す。
PowerShell から動かすときは、WSL の bash ではなく Git Bash を指定する（`& "C:\Program Files\Git\bin\bash.exe" scripts/set-release-secrets.sh`）。

### 新しい版を出す

1. 作業ブランチで `app/build.gradle.kts` の `versionCode` を 1 つ上げ、`versionName` を変える（T-tech Store は同じ versionCode を受け付けない）
2. PR を作り、自動マージで main に入るのを待つ
3. main でタグを付けて push し、`release` が終わるのを待つ

   ```bash
   git switch main && git pull --ff-only
   git tag v1.0.0 && git push origin v1.0.0
   gh run watch "$(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId')"
   ```

4. Releases の APK を手元に取り、appstore の `publish-app.mjs` で T-tech Store に出す（本番の鍵は raspi1 から環境変数にだけ読む。`~/.claude/notes/infra.md` の「T-tech Store のスクリプトを本番に向けて動かす」）。初めて出すときは、名前・説明・カテゴリ・アイコン・スクリーンショットも載せる

   ```bash
   gh release download v1.0.0 -R kawamonn91/basket-score -p '*.apk' -D "$SP"
   cd /c/dev/appstore/web
   while IFS='=' read -r k v; do [ -n "$k" ] && export "$k=$v"; done < <(ssh -o BatchMode=yes raspi1 'grep -E "^(NEXT_PUBLIC_SUPABASE_URL|SUPABASE_SERVICE_ROLE_KEY|R2_[A-Z_]+)=" /home/pi/.config/ttech-store/env')
   node scripts/publish-app.mjs --apk "$SP/basket-score-1.0.0.apk" --name "Basket Score" --notes "…" --publish
   ```
