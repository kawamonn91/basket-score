# Basket Score

バスケットボールの試合を Android タブレットで記録し、公式と同じ並びのスコアシート（PDF）と、試合の分析を出すアプリ。
T-tech Store で無料で配る（パッケージ名 `com.ttech.basketscore`。掲載内容は `store/listing.md`）。

- 大会のテーブルオフィシャルズ（スコアラー）として、JBA の公式スコアシートと同じ項目・記号で記録する
- 自分のチームの試合では、リバウンド・アシスト・ブロック・スティール・ターンオーバー、シュートの位置と成否、ゲームクロックの時刻も記録し、ボックススコア・シュートチャート・時間帯ごとの分析などを出す
- 通信しない（記録はすべて端末の中）

**状態: 1.0.0（設計書 [docs/design.md](docs/design.md) の 1〜8 段目）。** 要件は [docs/requirements.md](docs/requirements.md)。

## 構成

| 場所 | 中身 |
| --- | --- |
| `app/src/main/java/com/ttech/basketscore/rules/` | 試合の規則（大会の区分ごとの初期値、タイムアウトの欄に書く経過時間、L2M、残りのタイムアウト、チームファウル） |
| `app/src/main/java/com/ttech/basketscore/game/` | 試合の設定、出来事（得点・ファウル・交代など）の型、出来事の並びから試合の状態を作る計算（`GameEngine`）、JSON |
| `app/src/main/java/com/ttech/basketscore/sheet/` | 公式のスコアシートの中身（欄への割り当て）・描き方・PDF・共有と印刷 |
| `app/src/main/java/com/ttech/basketscore/stats/` | コートの寸法、ポゼッション、ボックススコア、分析（ラン・ラインナップ・時間帯・22 区域・期待値・種別・アドバンスド）、用語解説 |
| `app/src/main/java/com/ttech/basketscore/report/` | 分析のグラフ（画面と PDF で共通）・分析の PDF・CSV |
| `app/src/main/java/com/ttech/basketscore/data/` | Room（チーム・選手・試合・出来事・更新履歴）、時計の保存、バックアップと復元 |
| `app/src/main/java/com/ttech/basketscore/ui/` | 画面（Compose）: ホーム・チーム・試合の準備・記録・スコアシート・記録の一覧・分析 |
| `store/listing.md` | T-tech Store の掲載内容 |
| `docs/requirements.md` | 要件定義書 |
| `docs/design.md` | 1.0 の設計書（データ・画面・作る順番） |
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
