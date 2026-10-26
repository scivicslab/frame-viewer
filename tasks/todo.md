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
