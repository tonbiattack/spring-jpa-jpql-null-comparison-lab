# 新規性レポート: JPQLのnull比較で未割当WorkItemを取得できない

## 結論

本ラボは、Spring Data JPAの宣言的JPQLで`= :parameter`にnullを束縛して未割当行を検索しようとし、検索結果が空になる問題を扱います。DBへ保存された未割当WorkItemの最終状態、リポジトリ境界の結果、EntityManagerでの`=`と`IS NULL`の直接比較を分けて観測します。Jakarta Persistence Query Languageには未設定の関係・状態を検査する`IS NULL`式があります。[1]

既存のSpring Data JPA原稿にあるバルク更新後のPersistence Context、`orphanRemoval`、楽観ロックとは、直接原因、実境界、観測契約、最小修正がすべて異なります。先行するJava/Spring教材にも、JPQLへnullを束縛する検索契約を扱うものはありません。

## 監査方法

2026-08-20に`/home/ubuntu/qiita`配下のMarkdownを対象として、`JPQL`、`IS NULL`、`@Query`、`setParameter`、`未割当`、`未担当`、JPA・Hibernate・Spring Data JPAを含むファイル名を確認しました。特に既存のJPA主題原稿について、JPQL null比較・`IS NULL`・宣言的クエリの有無を本文から確認しました。また、ホームディレクトリ直下の`java-*lab`と`spring-*lab`を列挙し、先行教材との四軸比較を行いました。

| 監査対象 | 確認結果 | 本ラボへの影響 |
| --- | --- | --- |
| Qiita原稿のJPQL null比較・未割当検索 | 同じ原因・境界・契約を持つ原稿は確認されなかった | `= :parameter`と`IS NULL`を比較する題材は採用可能。 |
| 既存JPAデバッグ原稿 | バルク更新とPersistence Context、`orphanRemoval`、楽観ロックを確認 | いずれも近接するJPA題材だが、原因と修正が異なる。 |
| 先行Java/Spring教材 | `record`配列等値性、文字列、Map、URI、URL、Scanner、既定ロケールを確認 | JPQLのnull条件やH2上のJPA統合テストを扱う教材は存在しない。 |
| Repository Catalog | 規定の`/home/ubuntu/repository-catalog`が存在しない | カタログ更新・検証・語彙スクリーニングは実行できず、カタログ未記載のローカル専用教材は調査対象外である。 |

この調査は既存のローカルQiita原稿・先行教材と、API名・原因語・症状語の検索に基づきます。外部Web全体の文章類似性、閲覧数、将来の新規記事は判定対象ではありません。

## 既存JPA題材との四軸比較

| 比較対象 | 直接原因 | 実境界 | 観測契約 | 最小修正 | 本ラボとの差分 |
| --- | --- | --- | --- | --- | --- |
| 本ラボ | JPQLの`= :assignee`へnullを束縛し、`IS NULL`を使わない | `WorkItemRepository#findByAssignee(null)`とEntityManager | 未割当DB行を検索結果として一件返す | WHERE条件でnull引数を`IS NULL`へ分岐 | 基準 |
| Spring Data JPAのバルク更新とPersistence Context | JPQLバルクUPDATE後の管理エンティティが古い状態を保持する | `@Modifying`更新と同一トランザクションのdirty checking | DBの停止状態が後続更新で上書きされない | `clearAutomatically`などで管理状態を同期する | 本ラボはSELECTのnull条件であり、更新・管理状態・dirty checkingを扱わない。 |
| Spring Data JPAの`orphanRemoval` | 親子関連の所有側・コレクション操作が削除を伝播しない | 親エンティティから明細を外す操作 | 明細行がDBから削除される | 関連の整合性を保つエンティティ操作 | 本ラボは単一エンティティのnullable状態をSELECTし、関連削除を扱わない。 |
| Spring Data JPAの楽観ロック | `@Version`による競合更新の検出・再試行が不適切 | 複数更新の永続化 | 古い状態で在庫を上書きしない | versionを持つ更新の競合処理 | 本ラボは並行更新・version・再試行を使わず、単一トランザクションの検索条件を扱う。 |
| Springの@Transactional自己呼び出し | プロキシを経由しない内部呼出しでトランザクション設定が適用されない | サービスメソッド呼出しとロールバック | 例外後に残高更新が残らない | 呼出し境界をプロキシ経由にする | 本ラボはトランザクション境界でなく、JPAクエリの条件式を扱う。 |

## 先行Java/Spring教材との比較

| 比較対象 | 直接原因 | 実境界 | 観測契約 | 最小修正 | 本ラボとの差分 |
| --- | --- | --- | --- | --- | --- |
| `spring-webhook-record-array-dedup-lab` | `record`内の`byte[]`が参照比較される | Spring MVCのWebhook受付 | 同一Webhookを重複処理しない | 配列内容に基づく等値性 | JPA・DB・JPQLを使わず、HTTP入力の同一性を扱う。 |
| `java-string-split-trailing-empty-lab` | `String.split`が末尾空列を捨てる | CSV取込 | 任意の空列を保持する | `split`のlimit指定 | 文字列分割規則であり、DB検索条件を扱わない。 |
| `java-collectors-tomap-duplicate-key-lab` | `Collectors.toMap`にマージ規則がない | ストリームからMap構築 | 重複SKUで公開を失敗させない | マージ関数を指定 | Map構築時の例外であり、JPQLのnull検索ではない。 |
| `java-priorityqueue-iteration-order-lab` | 反復順と優先取出し順を混同する | 配信順の構築 | 優先度順に配信する | `poll`で取り出す | コレクション順序を扱い、永続状態を扱わない。 |
| `java-regex-replacement-literal-lab` | 置換値の`$`がグループ参照になる | テンプレートのレンダリング | 金額文字列を文字どおり出す | `Matcher.quoteReplacement` | 正規表現置換を扱い、JPQLを使わない。 |
| `java-uri-resolve-leading-slash-lab` | 先頭`/`が基底パスを置換する | API URIの解決 | バージョンパスを保持する | 相対参照を渡す | URI解決であり、DBのnull状態を扱わない。 |
| `java-map-getordefault-null-lab` | 明示的nullマッピングと既定値を混同する | Mapの参照 | 未設定とnull設定を区別する | nullを明示判定する | Java Mapの値取得規則であり、JPQLのWHERE条件ではない。 |
| `java-list-remove-integer-overload-lab` | `int`が値でなく添字のオーバーロードを選ぶ | ジョブID削除 | 指定IDの要素を削除する | `Integer.valueOf`を渡す | オーバーロード解決であり、JPAを使わない。 |
| `java-urldecoder-plus-token-lab` | `+`が空白として復号される | トークン照合 | 不透明トークンを一致させる | 入力形式に合う復号を選ぶ | URL形式と検索条件の題材は異なる。 |
| `java-scanner-nextline-newline-lab` | 数値読取後の改行が残る | コンソール入力 | 次の行の入力を読む | 残った改行を消費する | 入力カーソルの位置を扱い、永続化を使わない。 |
| `java-map-merge-null-removal-lab` | `Map.merge`のnull返却がマッピング削除を意味する | 在庫台帳の調整 | ゼロ在庫SKUを追跡に残す | nullでなく整数0を返す | nullという表層語は共通でも、Map更新とJPQL選択条件は別のAPI・最終状態・修正である。 |
| `java-string-format-default-locale-lab` | 既定ロケールで数値キーの小数区切りが変わる | 外部価格キー検索 | `tea:12.50`を検索できる | `Locale.ROOT`を指定する | 文字列形式の環境差であり、DBのnull条件を扱わない。 |

## 採用判断

本ラボは、JPA近接題材との混同を避けるため、原因を**JPQLのnull比較**だけへ、境界を`@Query`付きリポジトリとEntityManagerへ、最終観測をDBの未割当状態と結果セットへ限定しました。修正も一つのWHERE条件でnullを`IS NULL`として扱う変更に留めています。したがって、既存Qiita原稿および先行十二件のJava/Spring教材と重複しないSpring Data JPAデバッグ教材として採用します。

## References

[1]: https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-querylanguage/persistence-querylanguage.html "Jakarta EE Tutorial: The Jakarta Persistence Query Language"
