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
