# 各タブの検索

- [x] 検索語を語ごとに分け、大文字小文字を区別せず全語を含むかを判定する `Search` を書く
- [x] `search?tab=&q=` が、当たったツリーノードの ID と当たった場所（見出し・本文・規則など）を返す
  - Workflows：名前、YAML 本文
  - Contents：番号と見出し
  - Sections：番号と見出し、本文と概念、規則ファイル
  - Integrated frames：ID と名前、具象フレームの概念名、フレームの問い
- [x] 各タブのツリーの上に検索欄を置き、当たったノードと祖先だけを開いて見せる（jstree search プラグイン、照合はサーバーの結果）
- [x] 単体テスト、E2E に各タブ 1 語ずつの検索を足す
- [x] 版付き jar を配備、commit・push

## レビュー

- 実データで各タブ 1 語ずつ検索し、応答は 1 秒以内（Contents が最も遅く約 1.0 秒）。
- E2E（`FrameViewerLocalE2E` の手順 12）で、4 タブとも当たり件数とツリーで印の付いたノード数が一致し、ほかのノードが隠れ、欄を空にすると元に戻ることを確認した。

# Sections タブを 4 階層のツリーにし、Contents タブを廃止する（ユーザー要望 2026-10-26）

背景: Sections タブの左ペインは「本 → 節」の 2 階層だが、Contents タブは「本 → 部 → 章 → 節 → 小節」の目次の木になっている。Sections の中身は Contents と同じ節の並びなので、Sections を目次の木にして Contents を消す。
- [x] `BookPanel.sectionsTree()` を、目次(`outline`)の親子で組む。節の本文がある番号は今のラベル（長さ・単位数など）、本文の無い番号（部・章など）は「番号 見出し」。本文は目次に無い番号（大きい節を分けた `N.pK`）は、番号の末尾を落とした親の下に置く
- [x] ノードの ID は全部 `s:<本>|<番号>`。本文のあるノードは `data-section`、無いノードは `data-node`
- [x] 本文の無いノードを選ぶと `book-node` のパネル（どこに立つか、下に何があるか）を Sections タブに出す。パネルの中の `node-link` も Sections タブで開く
- [x] Sections の検索に、本文の無いノードの「番号 見出し」を足す。`searchContents`・`contentsTree`・`/tree?tab=contents`・`/search?tab=contents` を消す
- [x] `index.html` から Contents のタブと枠を消す。前回開いていたタブが `contents` だったときは `sections` を開く
- [x] 単体テスト（`BookPanelTest`、`SearchTest`）を書き換え、E2E(`FrameViewerLocalE2E` の Contents の手順)を Sections に移す
- [x] 版付き jar を `~/works/frame-viewer-1.0.0-SNAPSHOT-runner-<YYMMDDHHmm>.jar` に置き、利用者に AI-workspace からの再起動を頼む（28013 は触らない。確かめは 28041）

## レビュー

- 実データ（58 冊、19,609 ノード）で、`/tree?tab=sections` が 4 階層（部・章・節・小節）の木を返し、親の無いノードは 0 個。目次に親の番号が無い 8 ノード（`craft-of-research` の 2.2.3 など）は、番号の末尾を落とした最も近いノードの下に置く。
- 単体テスト 22 本緑（木の組み方 2 本、検索 1 本を書き換え）。E2E（`FrameViewerLocalE2E`、28041）が通った: タブは Workflows・Sections、Contents は無い、第 4 章の下に 4.1、その下に 4.1.1 が木で開き、本文が 2 ファイルから読まれる、検索は各タブで当たりと印が一致。
- 配備: `frame-viewer-1.0.0-SNAPSHOT-runner-2610261312.jar` に `frame-viewer.jar` のリンクを移した。28013 の再起動は、利用者が AI-workspace から行う。
- 旧 URL `/tree?tab=contents` は、未知のタブとして Workflows の木を返す（画面からは使われない）。

# Sections タブをサーバが描くページにする（FrameViewerConcept_261021_oo01、2026-11-18 着手）

背景: いまの画面は `GET /` の 1 枚の上で JavaScript がパネルを差し替えるので、アドレスバーが `/` のまま変わらない。仕様の書き直し（doc_Base010 `31aa4e5`）どおり、対象 1 つを自分の URL を持つ 1 枚のページにする。タブ 1 つずつ進め、最初は Sections。Workflows と Integrated frames は、順番が来るまでいまの画面のまま動かす。

- [x] ページ `sections`（本の一覧）、`sections/<book>`（本 1 冊、すぐ下の節点）、`sections/<book>/<number>`（節点 1 つと、同じ番号の節の本文と中間データ）を Qute のテンプレート `page.html` で描く。上にタブ、左に検索欄と木、右に本文
- [x] 節点のページの本文は、見出し、Where it stands、Under it のあとに、いまの `book-section` パネルの中身（Read from 以下）を置く。本文の無い節点と、`book-sections` を走らせていない本は 1 行で言う。`?view=rst` で規則ファイルに切り替える
- [x] 本文の中のリンク（節点、節、似た概念の節）を、ページの道筋の段の数から作った `../` の列を前に付けた相対の `href` にする
- [x] 左の木を、いまの対象までの枝だけ開いてサーバが描く。閉じた枝は、押すと `tree/sections/<book>[/<number>]` の JSON から、すぐ下の節点を差し込む
- [x] 検索の結果のページ `search?in=sections&q=` を描く。`search?tab=` の JSON は Workflows と Integrated frames の木のために残す
- [x] いまの画面から Sections の枠と `tree?tab=sections`・`book-node`・`book-section` を除く。上のタブは `sections` と `?tab=workflows`・`?tab=integrated` へのリンクにする。Integrated frames のパネルの節へのリンクは `sections/<book>/<number>` へのふつうのリンクにする
- [x] 単体テストを書き換え・足す。W206 で `rm -rf target` のあと `mvn package`（テスト込み）
- [x] Chromium（Playwright）で、節を開くと URL が変わる、再読み込みで同じ節が出る、戻るで前の節に戻ることを確かめる。`curl` で節の URL に本文が入っていることを確かめる
- [x] commit（push しない）

## レビュー

- W206（使い捨て Pod `fv60-build`、GraalVM CE 25、Maven 3.9.9）で `rm -rf target` のあと `mvn package`：単体テスト 24 本緑（`BookPanelTest` 16、`RegistryPanelTest` 3、`SearchTest` 3、`WorkflowPanelTest` 2）。木のテストは `sectionsTree()` から `children()`・`tree()` に書き換え、木が今の節点までしか開かないこと、道筋の符号化、節点のページの組み立て、具象フレームから節のページへのリンクを足した。
- 仕様の達成状態のコマンド：`curl -s "$FV/sections/core-java-14-vol1/4.1.1" | grep -c 'A class specifies how objects are made.'` は 4。
- Chromium（Playwright）で 28041 に立てた版を確かめた：本 → 章 4 → 閉じた枝 4.1 をその場で開く（URL は変わらない）→ 4.1.1、の各段でアドレスバーがそのページの URL になる。再読み込みで 4.1.1 のまま、`?view=rst` も自分の URL、戻るで rst → 4.1.1 → 章 4、進むで 4.1.1。検索の結果から 4.1.1 を開いて戻ると結果のページ。日本語の本の名前の URL を直接開ける。上のタブから Integrated frames と Workflows の画面に移れる。スクリプトのエラーは 0。
- 見つけた既存の問題（このコミットでは直していない）：要求のたびにデータベースのファイルが書き換わるので、本のキャッシュ（ファイルの更新時刻と大きさが鍵）が毎回捨てられ、全部の本を読み直す。いまの 28011 でも `book-section` 1 回に約 5 秒。新しいページは最初、1 枚に読み直しが 3 回入って約 16 秒かかったので、1 回の要求で本を 1 度だけ取るようにした（データベースの複製で 1 枚 1.5〜1.9 秒、検索 3.4 秒）。`BookStore` を開くたびのスキーマ文が書く分は、ストアを 1 つ保てば消える（`/tmp/fv60/keep-one-bookstore.patch`）が、読むだけでもファイルは書き換わり、大きくなる（複製で約 10 回の要求で 658 MB → 701 MB）。CLOB を読むと H2 が一時的な LOB をファイルに書くためだと見ているが、まだ確かめていない。

# books データベースの構造を直す（2026-11-19、段階 1：原因と提案。コードとデータはまだ変えない）

## 測定（W206 の Pod、本物のファイルの複製だけで）

本物の `books.mv.db`（657,989,632 B、sha256 `d42cd03a…`）を Pod に流し込み、いったん開いて閉じた「落ち着いた複製」を作り、場合ごとに新しい複製で測った。数は H2 自身の数（`INFORMATION_SCHEMA.SETTINGS` の `info.FILE_WRITE`・`info.FILE_WRITE_BYTES`）と、ファイルの大きさ・更新時刻。

| 場合 | 繰り返し | 書いた回数 | 書いた量 | ファイル |
|---|---|---|---|---|
| (a) 接続を開いて閉じるだけ（ほかに開いている者なし） | 20 | — | — | 658 MB のまま、更新時刻も変わらず |
| (a') `new BookStore(...)` を開いて閉じる（スキーマ文を毎回、ほかに開いている者なし） | 20 | — | — | 変わらず |
| (a'') 同じく、Frame Viewer のように別の接続が開いたまま | 20 | 0 | 0 | 変わらず |
| (b) CLOB でない列（`toc_node`、`book`）を全部の本で | 20 | 0 | 0 | 変わらず |
| (c) CLOB の列（`section_text.text`・`files`、`section_rules.rules`、`section_code.text`、`frame_rule.text`）、1 冊を 1 回 | 1 | 1 | 150 KB | 大きさは同じ、更新時刻が変わる |
| (c) 同じく、全部の本を 1 回（Frame Viewer の読み込み 1 回分） | 1 | 4 | 3.1 MB | 同上 |
| (c) 同じく、全部の本を 20 回 | 20 | 40 | 162 MB | **658 → 710 MB** |
| (c') (c) と同じ列を `CAST(… AS VARCHAR)` で、全部の本を 20 回 | 20 | 0 | 0 | 変わらず |
| (d) (c) を別のプロセスから `AUTO_SERVER` 越しに、全部の本を 1 回 | 1 | 9 | 4.1 MB | 更新時刻が変わる |
| (d) 同じく 20 回 | 20 | 130 | 212 MB | **658 → 719 MB** |
| (d') (c') を別のプロセスから、20 回 | 20 | 0 | 0 | 変わらず |
| 試作：CLOB の列 22 本を全部 `VARCHAR(1000000)` に変え、`SHUTDOWN COMPACT` した複製で (c) を 20 回 | 20 | 0 | 0 | 302 MB のまま |
| 同じ試作で (d) を 20 回 | 20 | 0 | 0 | 302 MB のまま |

ほかに分かったこと：

- どの場合も、データベースを開いた直後に H2 が 1 度だけ 2 回・約 45 KB 書く。開く者が来るたびに更新時刻は変わる。
- 開いたままのファイルを複製すると、複製を最初に開いたとき H2 が 17.6 MB 書く（閉じ方がきれいでないファイルの後始末）。測定はこれを済ませた複製で行った。
- 最も長い値は `section_rules.rules` の 181,647 字、`section_text.text` の 120,427 字。H2 2.3.232 の `VARCHAR(1048577)` も作れた。CLOB の列の中身は合わせて約 1.75 億字。
- 試作の変換（22 列）は 8 秒、途中でファイルは 1.44 GB になり、`SHUTDOWN COMPACT` のあと 302 MB（もとの 46%）。`NOT NULL` は残る。
- `AUTO_SERVER` 越しに全部の本を読むと、同じプロセスの中で読むより約 25 倍遅い（20 回で 7 秒と 184 秒）。

## 原因

CLOB の列を読むと、H2 は読んだ値をファイルの中の一時的な LOB に写す。これが「読むだけでファイルが書き換わり、大きくなる」の原因である（表の (c) と (c')、(d) と (d')）。Frame Viewer は本を全部読むたびに CLOB を読み、ファイルの更新時刻が変わるので、次の要求で本をまた全部読む。読むたびにファイルは育つ。

`BookStore` を開くたびのスキーマ文（`CREATE TABLE IF NOT EXISTS`、`ALTER TABLE … ADD COLUMN IF NOT EXISTS`）は、変えるものが無いときは 1 バイトも書かない（(a')、(a'')）。ファイルが育つ原因ではない。

## 提案する構造

1. **本の文は `VARCHAR` で持つ。** CLOB の列 22 本（使われていない 4 本を含む）を `VARCHAR(1000000)` にする。ファイルに分けて持つ案は採らない。文と目次と中間データが 1 つのトランザクションでそろう今の性質を失い、ファイルとデータベースのずれを見張る仕組みが新しく要る。`VARCHAR` なら読んでも書かない（試作で 0）、ファイルは 302 MB に縮み、読む側のコードは `getString` のままで変わらない。100 万字を超える値は書く側でエラーにする（いまの最長は 18 万字）。
2. **スキーマは書く側だけが作り、変える。** スキーマ文と移行を `BookStore` のコンストラクタから新しいクラス `BookSchema` に移し、表 `schema_version` に版の番号を持つ。書く側（`BookStructure`、`BookImport`）は `BookStore.forWriting(url)` で開き、そこで足りない移行を流す。読む側（Frame Viewer）は `BookStore.forReading(url)` で開き、DDL を流さず、版が合わなければ「このデータベースは版 N、この読み手は版 M」と言って止まる。測定ではスキーマ文はファイルを書かないので、これは育つのを止めるためではない。古い読み手や新しい読み手が、稼働中のデータベースの形を勝手に変えないためである。
3. **変わったことは版の数で知る。** 表 `data_version`（1 行）の数を、`BookStore` の全ての `save*`・`add*`・`clear*` が同じトランザクションの中で 1 増やす。Frame Viewer は要求ごとに `SELECT version FROM data_version` を読み（1 行、CLOB なし、書かない）、前と違うときだけ本を読み直す。ファイルの更新時刻は、開く者が来るだけで変わるので見ない。
4. **書く側で変わること。** 書くのは chat-ui-with-audit-trail の `book-*` workflow（`BookStructure` アクター、`app/pom.xml` で plugin-rst-frame を取り込む）と、コマンドの `BookImport`。どちらも `forWriting` で開くようにする。`save*` が版の数を上げるのは `BookStore` の中なので、workflow の YAML は変わらない。古い plugin のままの chat-ui-with-audit-trail も移行後のデータベースに書けるが、版の数を上げないので Frame Viewer が気づかない。chat-ui-with-audit-trail を新しい plugin で作り直し、同じときに入れ替える必要がある（担当の開発者との段取りが要る）。
5. **本物の 658 MB の移行。** 移行は `BookImport migrate <db>` として書き、次の順に行う。どの段も、手前の段に戻せる。
   1. 書く者を止める：`book-*` workflow が走っていないことを確かめ、28011 を AI-workspace から止める（利用者の操作。H2 はファイルを 1 つのプロセスにしか開かせない）。
   2. 控え：`db/backup/books-<YYMMDDHHmm>.mv.db` に写し、sha256 を記録する。戻すときはこれを書き戻すだけ。
   3. 移行：CLOB の列を `VARCHAR(1000000)` に、`schema_version`・`data_version` を作る。前と後で、表ごとの行数と、列ごとの `SUM(CHAR_LENGTH)` と内容のハッシュが同じことを確かめる。違えば控えを書き戻す。
   4. 縮める：`SHUTDOWN COMPACT`。大きさを記録する（複製では 658 → 302 MB）。
   5. 新しい Frame Viewer と chat-ui-with-audit-trail で起動する。戻すときは、控えのファイルと前の jar に戻す。
   - 実験の残りの表（`section_pattern_ex1`〜`ex3`、`section_pattern_run1`〜`run7`）は移行の対象に含めるが、消さない。消すかどうかは利用者が決める。
6. **試験。**
   - plugin-rst-frame の単体テスト（一時ディレクトリの H2 ファイル、外部サービスなし）：長い文を書き、開き直して落ち着かせたあと、全部の読み取りを 20 回して `info.FILE_WRITE` が増えないこと。`save*` のあと `data_version` が上がり、読むだけでは上がらないこと。`forReading` が DDL を流さず、版が違えば止まること。CLOB のデータベースを移行すると、行数・字数・ハッシュが保たれ、CLOB の列が残らないこと。
   - Frame Viewer：版の数が同じあいだは本を読み直さないこと（要求 2 回目が速い）、書いたあとは読み直すこと。
   - 複製で：移行 → Frame Viewer を立てて Playwright でページを 50 枚開く → ファイルの大きさと `info.FILE_WRITE` が開いた直後から増えないことを数で示す。

## 段階 2（2026-11-19）：実装と、複製での移行

利用者が段階 1 の提案を 3 点とも承認した（構造、chat-ui-with-audit-trail との入れ替え、実験の表は消さずに移行）。

- [x] plugin-rst-frame：`BookSchema`（`schema_version`、版 2）、`BookStore.forWriting`・`forReading`、`data_version`（書く 25 のメソッドが同じトランザクションで 1 足す。自動コミットで書いていた 12 は、トランザクションに包んだ）、`BookMigration`（`BookImport migrate <db>`）
- [x] 書く側は版の付いていない古いデータベースを変換しない（`BookImport migrate` を促して止まる）。変換は控えを取る `migrate` だけが行う。新しい plugin の chat-ui が、移行の前に本物を書き換え始めないため
- [x] Frame Viewer：`forReading` を 1 つ保ち、本は `data_version` が動いたときだけ読み直す
- [x] 単体テスト：plugin-rst-frame 162 本（新しい `BookStoreStorageTest` 5 本）、Frame Viewer 25 本（新しい `BooksCacheTest`）。W206 で `rm -rf target` のあと、POJO-actor（270）・Turing-workflow（372）を `install`、続けて plugin、frame-viewer
- [x] 複製で移行：26 秒、37 表すべて行数・字数・行のハッシュが一致、22 列を変換、658 MB → 302 MB、控えの sha256 は本物と同じ
- [x] 50 ページ：移行した複製と新しい Frame Viewer で、書き込み 0 回、大きさ・更新時刻とも変わらず、0.3 秒。比べに、移行前の複製と前の版（`ca22b59`）では 237 回・685 MB 書き、658 → 942 MB、79 秒
- [x] Chromium の確かめ（全部 PASS、スクリプトなしの読み手も PASS）を移行した複製で行った
