# JPQLのnull比較で未割当WorkItemを取得できないデバッグラボ

この教材は、Spring Data JPAの`@Query`で`workItem.assignee = :assignee`へnullを束縛し、未割当WorkItemを取得できなくなる不具合を再現・修正します。Jakarta Persistence Query Languageでは、未設定の関連・状態を確認するために`IS NULL`式を使えます。[1] Spring Data JPAは、リポジトリのクエリメソッドへ`@Query`を付けて宣言的なJPQLを直接関連付けられます。[2]

| 項目 | 内容 |
| --- | --- |
| 対象 | Java 21、Spring Boot 3.4.3、Spring Data JPA、Hibernate、H2、JUnit Jupiter |
| 原因 | JPQLの等価比較`= :assignee`でnullを検索し、`IS NULL`述語を使っていない |
| バグコミット | [`07d2d61`](../../commit/07d2d61) — null比較で未割当WorkItemを取得できない状態を再現する |
| 修正コミット | [`d1d89d7`](../../commit/d1d89d7) — null検索にIS NULL述語を使う |
| 実行境界 | `@DataJpaTest`、H2インメモリDB、実際の`JpaRepository`、EntityManager |

## この題材で守る契約

`draft-release`の担当者がnull、すなわち未割当であるとき、`findByAssignee(null)`はそのWorkItemを一件返さなければなりません。DBからIDで読み直して担当者がnullであること、検索結果が一件であること、検索IDが未割当WorkItemのIDと一致することを分けて検証します。

| 観測点 | 正しい状態 | バグ状態 |
| --- | --- | --- |
| DBから再読込した担当者 | `null` | `null`（DB保存は成功） |
| 未割当の検索件数 | `1` | `0` |
| 検索結果のID | `draft-release`のID | 空リスト |

## 最短の開始手順

修正済みの`main`で、H2を使う統合テスト全体をクリーン実行します。

```bash
mvn --batch-mode clean test
```

`WorkItemRepositoryTest`はリポジトリ境界と最終DB状態を、`JpqlNullComparisonObservationTest`は`= :parameter`と`IS NULL`のJPQL結果を直接確認します。修正済み状態の完全な出力は[`evidence/03-fixed-full-test-output.txt`](evidence/03-fixed-full-test-output.txt)に保存しています。

## バグを再現する

この節は**意図した失敗**を確認する手順です。未コミット変更のない作業ツリーで実行し、確認後には必ず`main`へ戻してください。

```bash
git switch --detach 07d2d61
mvn --batch-mode test -Dtest=WorkItemRepositoryTest
git switch main
```

バグコミットでは、`draft-release`がDBに保存されていることの確認は成功します。しかし、`findByAssignee(null)`の結果は空であるため、件数とIDのアサーションが失敗します。出力全体は[`evidence/01-bug-service-test-output.txt`](evidence/01-bug-service-test-output.txt)に保存しています。

原因をリポジトリの外へ切り出すため、[`JpqlNullComparisonObservationTest`](src/test/java/jp/tonbiattack/debuglab/workitem/JpqlNullComparisonObservationTest.java)では、同一のH2状態で`workItem.assignee = :assignee`と`workItem.assignee IS NULL`をEntityManagerから直接実行します。前者は空、後者は未割当IDを返します。この観測テストはバグ状態でも成功し、出力は[`evidence/02-jpql-null-observation-output.txt`](evidence/02-jpql-null-observation-output.txt)です。

## 原因と最小修正

バグ状態の`@Query`はnullを等価比較していました。nullを検索するケースでは、JPQLの`IS NULL`述語を使う必要があります。[1] 同じ公開メソッドでnull・非nullの双方を扱うため、null引数では`IS NULL`を、非null引数では等価比較を選ぶ一つのJPQLへ置き換えます。

```java
// バグ状態
@Query("select workItem from WorkItem workItem where workItem.assignee = :assignee")

// 修正状態
@Query("select workItem from WorkItem workItem "
        + "where (:assignee is null and workItem.assignee is null) "
        + "or workItem.assignee = :assignee")
```

この変更はJPQLのWHERE条件だけに限定しています。動的検索、N+1、バルク更新、永続化コンテキストのclear、楽観ロック、HTTP APIは変更しません。調査の詳細と回帰保証は[デバッグ記録](docs/debugging-record.md)を、既存コンテンツとの差分は[新規性レポート](docs/novelty-report.md)を参照してください。

## プロジェクト構成

| パス | 役割 |
| --- | --- |
| `src/main/java/.../WorkItem.java` | nullableな担当者を持つ最小のJPAエンティティ |
| `src/main/java/.../WorkItemRepository.java` | バグと最小修正を含む宣言的JPQLリポジトリ |
| `src/test/java/.../WorkItemRepositoryTest.java` | リポジトリの公開契約と最終DB状態を検証する統合テスト |
| `src/test/java/.../JpqlNullComparisonObservationTest.java` | JPQLの`=`と`IS NULL`を直接比較する観測テスト |
| `evidence/` | バグ状態、直接観測、修正状態のMaven出力 |
| `docs/topic-brief.md` | 題材境界、仮説、再現設計 |
| `docs/debugging-record.md` | 調査・最小修正・回帰保証 |
| `docs/novelty-report.md` | 既存Qiita原稿・先行教材との四軸比較 |

## References

[1]: https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-querylanguage/persistence-querylanguage.html "Jakarta EE Tutorial: The Jakarta Persistence Query Language"
[2]: https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html "Spring Data JPA Reference: JPA Query Methods"
