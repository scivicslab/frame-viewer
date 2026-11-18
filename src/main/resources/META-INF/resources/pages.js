// The script of the pages the server draws. A page is whole without it: every node of the tree and every name in the text is
// a plain link. It does two things a link cannot. It opens a closed branch of the left tree in place, from the branch's
// tree/... path, without leaving the page. And it turns an edit link into the code-server URL that opens the file.
(function () {
  var body = document.body, base = body.getAttribute('data-base') || '';

  function entry(e) {
    var li = document.createElement('li');
    li.className = e.branch ? 'branch' : 'leaf';
    if (e.branch) {
      var t = document.createElement('span');
      t.className = 'twisty';
      t.setAttribute('data-tree', e.tree);
      li.appendChild(t);
    }
    var a = document.createElement('a');
    a.href = base + e.path;
    a.textContent = e.label;
    li.appendChild(a);
    return li;
  }

  // a closed branch is opened from the server the first time, then opened and closed in place
  document.addEventListener('click', function (ev) {
    var t = ev.target.closest('.twisty');
    if (!t) return;
    var li = t.parentNode, ul = li.querySelector(':scope > ul');
    if (ul) { li.classList.toggle('open'); return; }
    if (li.classList.contains('loading')) return;
    li.classList.add('loading');
    fetch(base + t.getAttribute('data-tree')).then(function (r) { return r.json(); }).then(function (list) {
      var sub = document.createElement('ul');
      sub.className = 'toc';
      list.forEach(function (e) { sub.appendChild(entry(e)); });
      li.appendChild(sub);
      li.classList.add('open');
    }).finally(function () { li.classList.remove('loading'); });
  });

  // code-server listens on 127.0.0.1:8080 of this host, so the edit links open only from a browser on that machine.
  // The URL is the one code-raptor's own file links use: the works folder, and a payload that opens the file.
  document.addEventListener('click', function (ev) {
    var a = ev.target.closest('a.edit-link');
    if (!a) return;
    ev.preventDefault();
    var works = body.getAttribute('data-works-dir');
    var abs = works + '/' + a.getAttribute('data-edit-works');
    var origin = location.protocol + '//' + location.hostname + ':8080';
    var payload = JSON.stringify([['openFile', 'vscode-remote://' + location.hostname + ':8080' + abs + ':1'], ['gotoLineMode', 'true']]);
    window.open(origin + '/?folder=' + encodeURIComponent(works) + '&payload=' + encodeURIComponent(payload), '_blank');
  });

  // the current node of the tree is scrolled into view
  var cur = document.querySelector('.tree a.current');
  if (cur) cur.scrollIntoView({ block: 'center' });
})();
