# デバッグ記録: JPQLのnull比較で未割当WorkItemを取得できない

## 実行環境と再現境界

このラボはJava 21、Spring Boot 3.4.3、Spring Data JPA、Hibernate、H2、JUnit Jupiterを使います。`@DataJpaTest`がH2インメモリDBと実際のJPAリポジトリを起動し、各テストはトランザクション内で実行されます。時刻、乱数、ネットワーク、外部I/Oには依存しません。

| 境界 | 内容 |
| --- | --- |
| Arrange | `draft-release`（assignee=null）と`review-release`（assignee=`mika`）を`saveAndFlush`で保存する。 |
| Act | 公開メソッド`WorkItemRepository#findByAssignee(null)`を実行する。 |
| Assert | 検索結果が一件であり、そのIDが未割当WorkItemのIDと一致することを確認する。 |
| Observe | EntityManagerをclearしてIDでDBから再読込し、未割当行の担当者がnullで残ることを独立に確認する。 |

対象はJPQLにおけるnullの比較式だけです。動的フィルタ、N+1、バルク更新、Persistence Contextのclear、トランザクション分離、楽観ロック、HTTP APIは対象外です。

## 最初に観測した事実

バグコミット[`07d2d61`](../../commit/07d2d61)で、次を実行すると意図したアサーション差分が再現します。

```bash
git switch --detach 07d2d61
mvn --batch-mode test -Dtest=WorkItemRepositoryTest
git switch main
```

| 観測点 | 期待 | バグ状態の実測 |
| --- | --- | --- |
| `draft-release`のDB上の担当者 | `null` | `null` |
| `findByAssignee(null)`の件数 | `1` | `0` |
| 検索結果のID | 保存済みの未割当ID | `[]` |

失敗出力は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)に保存しています。DBから再読込した`draft-release`は依然として未割当であるため、保存漏れやEntityManager内だけの状態ではなく、検索条件が問題の中心であると分かります。

## 競合仮説と検証

| 仮説 | 最小の検証 | 結果 | 判断 |
| --- | --- | --- | --- |
| 未割当WorkItemがDBへ保存されていない | `saveAndFlush`後にclearし、IDで再読込する | `draft-release`の担当者はnull | 棄却 |
| リポジトリのクエリが実行されていない | `@Query`文字列を確認し、EntityManagerで同じ比較式を直接実行する | 等価比較のSQLが実行され、空結果となる | 棄却 |
| nullを`=`で比較し、`IS NULL`が必要である | `= :assignee`と`IS NULL`の結果IDを同一DBで比較する | 前者は空、後者は未割当IDを返す | 採用 |

[`JpqlNullComparisonObservationTest`](../src/test/java/jp/tonbiattack/debuglab/workitem/JpqlNullComparisonObservationTest.java)は、サービス層やリポジトリのメソッド名を通さず、EntityManagerで二つのJPQLを直接実行します。バグ状態で成功する観測出力は[`evidence/02-jpql-null-observation-output.txt`](../evidence/02-jpql-null-observation-output.txt)にあります。

## 確定した原因

Jakarta Persistence Query Languageでは、未設定の関係・状態を確認するために`IS NULL`式を使用できます。[1] `workItem.assignee = :assignee`にnullを束縛しても、`workItem.assignee IS NULL`という述語にはなりません。本件では、この等価比較が未割当行を選択しないため、DBに存在する`draft-release`を空の検索結果として返しました。

> `IS NULL`式は、関係が設定されているかどうかを検査するために使える。— Jakarta EE Tutorial: Jakarta Persistence Query Language [1]

Spring Data JPAは、`@Query`をリポジトリメソッドへ関連付けてJPQLを宣言できます。[2] そのため、メソッド名や戻り値型は正しくても、`@Query`のWHERE条件がnullの意味を表していなければ、実行された結果セットは契約と異なります。

## 最小修正

`WorkItemRepository#findByAssignee`のJPQLだけを置き換えます。null引数では`IS NULL`を使い、非null引数では従来どおり等価比較を使います。これにより、公開メソッドと非null検索の能力を維持します。

```diff
- @Query("select workItem from WorkItem workItem where workItem.assignee = :assignee")
+ @Query("select workItem from WorkItem workItem "
+         + "where (:assignee is null and workItem.assignee is null) "
+         + "or workItem.assignee = :assignee")
```

修正はコミット[`d1d89d7`](../../commit/d1d89d7)にあります。バルク更新へ`clearAutomatically`を付ける、EntityManagerのライフサイクルを変える、動的クエリ基盤へ置き換えるといった変更は原因と無関係のため加えていません。

## 回帰保証

修正済みの`main`で、H2を使う全統合テストをクリーン実行します。

```bash
mvn --batch-mode clean test
```

全出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。`WorkItemRepositoryTest`がリポジトリの公開契約と最終DB状態を、直接観測テストがJPQL規則を別々に固定します。

### 再発防止テスト

`WorkItemRepositoryTest#findByAssignee_withNull_returnsPersistedUnassignedWorkItem`は、検索前のDB再読込、検索件数、検索IDを独立に検証します。将来、検索結果だけを偽装するような修正や、未割当行がそもそも保存されない回帰を検出します。

`JpqlNullComparisonObservationTest#equalityComparisonWithNullReturnsNoRows_butIsNullFindsUnassignedRow`は、`= :assignee`と`IS NULL`の差をH2/JPA上で直接固定します。これはJPAプロバイダやSQL方言の詳細を一般化する証明ではなく、このラボのJPQL条件選択の根拠を残すための最小実験です。

## 再現手順

修正済み状態は、リポジトリ直下で`mvn --batch-mode clean test`を実行します。バグ状態の確認には`07d2d61`へ一時的に切り替え、`mvn --batch-mode test -Dtest=WorkItemRepositoryTest`を実行します。確認後は`git switch main`で修正済み状態へ戻してください。未コミット変更のある作業ツリーで切替を実行しないでください。

## スコープと注意点

このラボは「nullを渡したときに未割当行を取得する」という明示された検索契約を前提にします。実運用ではnullを「このフィルタを指定しない」と解釈する場合もあります。その場合は、`(:assignee is null or workItem.assignee = :assignee)`のように別の契約を明示し、未割当だけを検索する契約と混同しないでください。ここでのJPQLをすべての任意フィルタへ機械的に流用するべきだとは主張しません。

## References

[1]: https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-querylanguage/persistence-querylanguage.html "Jakarta EE Tutorial: The Jakarta Persistence Query Language"
[2]: https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html "Spring Data JPA Reference: JPA Query Methods"
