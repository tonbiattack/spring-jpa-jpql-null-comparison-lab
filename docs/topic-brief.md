# 題材企画: JPQLの`= :parameter`で未割当エンティティを取得できない

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象技術 | Java 21、Spring Boot、Spring Data JPA、Hibernate、H2、JUnit Jupiter |
| 対象読者 | Spring Data JPAの宣言的JPQLで、nullを持つ状態フィールドや未割当レコードを検索する開発者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | JPQLの`workItem.assignee = :assignee`にnullを束縛しても、`assignee IS NULL`の意味にはならない。未割当のWorkItemを一覧へ出す契約で、DBには未割当行が存在するのに検索結果が空になる。リポジトリ境界とEntityManagerによる最小観測を分離できる。 |
| 実行基盤 | Maven、Java 21、Spring Boot 3.4.3、Spring Data JPA、Hibernate、H2、JUnit Jupiter |
| JPA固有性 | 原因はJPQLのnull比較とSQLの三値論理であり、Spring Data JPAの`@Query`からJPAプロバイダへ渡るクエリ契約にある。 |

## 学習する契約

> WorkItem `"draft-release"`の`assignee`がnullであるとき、`findByAssignee(null)`はそのWorkItemを一件返すべきだが、バグ状態のJPQL `assignee = :assignee`は空リストを返す。

### 対象の直接原因

JPQLの等価比較演算子`=`をnull値に対して使い、`IS NULL`述語を使っていない。`= null`の比較はtrueにならず、WHERE句で対象行が除外される。

### 対象外

このラボは任意のフィルタ条件を一つのクエリへ組み合わせる動的検索、ソート、ページング、N+1、バルク更新、永続化コンテキストのclear、楽観ロック、論理削除、HTTP APIを扱わない。nullの担当者を持つ一件のWorkItemをJPQLで取得する境界だけを扱う。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `WorkItemRepository#findByAssignee(String)`。Spring Data JPAの`@Query`経由で実行する。 |
| 入力・初期状態 | `draft-release`（assignee=null）と`review-release`（assignee=`"mika"`）をH2へ保存し、`findByAssignee(null)`を呼ぶ。 |
| Redの観測 | 未割当のIDが一件返るべきだが、バグ状態では空リストとなる。 |
| 最終観測 | DBから再読込した`draft-release`の担当者がnullであること、検索件数が1であること、検索IDが未割当WorkItemのIDと一致することを独立に確認する。 |
| 直接観測 | 同じ初期状態で、EntityManagerの`assignee = :assignee`と`assignee IS NULL`の結果件数・IDを比較する。 |
| 決定性 | H2インメモリDB、固定文字列、テストトランザクションを使う。時刻、乱数、ネットワーク、外部I/Oは使わない。 |
| 固定状態の検証コマンド | `mvn --batch-mode clean test` |
| バグ状態の確認コマンド | `git checkout 07d2d61`で`mvn --batch-mode test -Dtest=WorkItemRepositoryTest`を実行する。 |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| A: 未割当WorkItemがDBへ保存されていない | `saveAndFlush`後にEntityManagerをclearし、IDで再読込して担当者がnullであることを確認する。 |
| B: リポジトリの`@Query`が実行されず、別の条件で検索している | リポジトリメソッドのクエリ文字列を確認し、EntityManagerで同じ比較式を直接実行する。 |
| C: null値を`=`で比較しており、`IS NULL`述語が必要である | `= :assignee`と`IS NULL`の同一DB上の結果を直接比較する。 |

## 予定する履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | null比較で未割当WorkItemを取得できない状態を再現する | 未割当のDB行は存在するが、リポジトリの検索件数は0となり契約テストが失敗する。 |
| 2 | null検索にIS NULL述語を使う | 同じ統合テストと全テストが成功する。 |
