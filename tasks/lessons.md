
# Frame Viewer の試験は books データベースの複製で行う（2026-11-18）

- 誤り: 28041 に立てた試験用の版を、28011 が開いている本物の `rst-on-lisp/db/books` に向けた。H2 の `AUTO_SERVER` で 28011 経由に読み、読むだけでファイルが 641 MB から 658 MB に育った。
- ルール: 試験用の版は必ず `books.mv.db` の複製を `/tmp` に置き、`-Dframe-viewer.books-db=<複製>` で起動する。終わったら複製を消す。
