# Basket Score

バスケットボールの試合を Android タブレットで記録し、公式と同じ並びのスコアシート（PDF）と、試合の分析を出すアプリ。
T-tech Store で無料で配る予定（パッケージ名 `com.ttech.basketscore`）。

- 大会のテーブルオフィシャルズ（スコアラー）として、JBA の公式スコアシートと同じ項目・記号で記録する
- 自分のチームの試合では、リバウンド・アシスト・ブロック・スティール・ターンオーバー、シュートの位置と成否、ゲームクロックの時刻も記録し、ボックススコア・シュートチャート・時間帯ごとの分析などを出す
- 通信しない（記録はすべて端末の中）

**状態: 開発を始めたところ（ひな形）。** 要件は [docs/requirements.md](docs/requirements.md)。

## 構成

| 場所 | 中身 |
| --- | --- |
| `app/src/main/java/com/ttech/basketscore/rules/` | 試合の規則（大会の区分ごとの初期値、タイムアウトの欄に書く経過時間、L2M、残りのタイムアウト、チームファウル） |
| `app/src/main/java/com/ttech/basketscore/ui/` | 画面（Compose） |
| `docs/requirements.md` | 要件定義書 |
| `docs/ci.md` | CI・CD と、新しい版を出す手順 |

公式のスコアシートの様式（PDF）は、作成者の様式なのでリポジトリに入れない。手元の `reference/` に置く（Git の対象外）。

## 署名鍵

リリース版の署名鍵は `basket-score.jks` と `keystore.properties`（どちらも Git の対象外。**なくすとストアで更新を配れない**ので、別の場所にも控えておく）。
写しは GitHub Secrets（`RELEASE_KEYSTORE_BASE64` など。docs/ci.md）にもある。

```properties
storeFile=basket-score.jks
storePassword=…
keyAlias=basket-score
keyPassword=…
```

## ビルドとテスト

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug   # CI と同じ
```

## ライセンス

まだ決めていない（決めるまでは、ソースの公開だけで、利用の許可はしていない）。
