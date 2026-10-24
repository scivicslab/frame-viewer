// The Relations tab: the generalized frames of the registry and the relations between them, drawn around one frame.
// The frame chosen sits in the middle; the frames it is related to, up to the depth chosen, are laid out around it by a
// small force simulation (frames push each other apart, a relation pulls its two frames together). Clicking a frame
// moves the middle to it and shows its panel on the right.
(function () {
  var KINDS = {
    'is-a (tree)': { color: '#2b6cb0', label: 'is-a (the tree)', width: 2.6 },
    'is-a': { color: '#63b3ed', label: 'is-a (a section said)', width: 1.6, dash: '5,3' },
    'part-of': { color: '#2f855a', label: 'part-of', width: 1.6 },
    'feature': { color: '#b7791f', label: 'feature', width: 1.6 },
    'operation': { color: '#805ad5', label: 'operation', width: 1.6 },
    'other': { color: '#a0aec0', label: 'other', width: 1.2 }
  };
  var MAX_NODES = 70;
  var NS = 'http://www.w3.org/2000/svg';

  function el(name, attrs) {
    var e = document.createElementNS(NS, name);
    for (var k in attrs) e.setAttribute(k, attrs[k]);
    return e;
  }

  window.RelationsGraph = function ($pane, onFocus) {
    var graph = null, byId = {}, adj = {}, shown = {}, depth = 1, focusId = null;
    var $svg = $pane.find('svg.graph'), svg = $svg[0];
    var view = { x: -500, y: -350, w: 1000, h: 700 };

    function kindOn(k) { return shown[k] !== false; }

    function legend() {
      var $l = $pane.find('.graph-legend').empty();
      Object.keys(KINDS).forEach(function (k) {
        var id = 'kind-' + k.replace(/\W+/g, '-');
        var $c = $('<label class="graph-kind"></label>');
        $('<input type="checkbox">').attr('id', id).prop('checked', kindOn(k)).on('change', function () {
          shown[k] = this.checked; if (focusId) focus(focusId, true);
        }).appendTo($c);
        $('<span class="swatch"></span>').css({ background: KINDS[k].color }).appendTo($c);
        $c.append(document.createTextNode(KINDS[k].label));
        $l.append($c);
      });
    }

    function load(done) {
      $.getJSON('registry-graph', function (g) {
        graph = g; byId = {}; adj = {};
        g.nodes.forEach(function (n) { byId[n.id] = n; adj[n.id] = []; });
        g.edges.forEach(function (e) {
          if (!adj[e.from] || !adj[e.to]) return;
          adj[e.from].push({ other: e.to, edge: e });
          adj[e.to].push({ other: e.from, edge: e });
        });
        var $list = $pane.find('#graph-frames').empty();
        g.nodes.forEach(function (n) { $list.append($('<option>').attr('value', n.id + ' ' + n.name)); });
        $pane.find('.graph-count').text(g.nodes.length + ' frames, ' + g.edges.length + ' lines');
        if (done) done();
      });
    }

    // the frames within the depth chosen, nearest first, at most MAX_NODES of them
    function neighbourhood(id) {
      var seen = {}, order = [id], frontier = [id];
      seen[id] = 0;
      for (var d = 1; d <= depth && order.length < MAX_NODES; d++) {
        var next = [];
        frontier.forEach(function (x) {
          adj[x].forEach(function (a) {
            if (!kindOn(a.edge.kind) || seen[a.other] !== undefined || order.length >= MAX_NODES) return;
            seen[a.other] = d; order.push(a.other); next.push(a.other);
          });
        });
        frontier = next;
      }
      return { ids: order, dist: seen };
    }

    function layout(ids, dist, centre) {
      var pos = {}, n = ids.length;
      ids.forEach(function (id, i) {
        var a = 2 * Math.PI * i / Math.max(1, n - 1), r = id === centre ? 0 : 160 * dist[id];
        pos[id] = { x: Math.cos(a) * r * 1.8 + (Math.random() - 0.5) * 20, y: Math.sin(a) * r * 1.4 + (Math.random() - 0.5) * 20 };
      });
      var inSet = {}; ids.forEach(function (id) { inSet[id] = true; });
      var links = graph.edges.filter(function (e) { return inSet[e.from] && inSet[e.to] && kindOn(e.kind) && e.from !== e.to; });
      // more frames need longer lines, or their names run into each other
      var L = Math.min(230, 110 + n * 2.2), temp = 80;
      for (var it = 0; it < 350; it++) {
        var disp = {};
        ids.forEach(function (id) { disp[id] = { x: 0, y: 0 }; });
        for (var i = 0; i < n; i++) for (var j = i + 1; j < n; j++) {
          var p = pos[ids[i]], q = pos[ids[j]], dx = p.x - q.x, dy = p.y - q.y, d2 = dx * dx + dy * dy + 0.01, d = Math.sqrt(d2);
          var f = (L * L) / d;
          disp[ids[i]].x += dx / d * f; disp[ids[i]].y += dy / d * f;
          disp[ids[j]].x -= dx / d * f; disp[ids[j]].y -= dy / d * f;
        }
        links.forEach(function (e) {
          var p = pos[e.from], q = pos[e.to], dx = p.x - q.x, dy = p.y - q.y, d = Math.sqrt(dx * dx + dy * dy) + 0.01;
          var f = (d * d) / L;
          disp[e.from].x -= dx / d * f; disp[e.from].y -= dy / d * f;
          disp[e.to].x += dx / d * f; disp[e.to].y += dy / d * f;
        });
        ids.forEach(function (id) {
          if (id === centre) return;
          var dd = disp[id], len = Math.sqrt(dd.x * dd.x + dd.y * dd.y) + 0.01, step = Math.min(len, temp);
          pos[id].x += dd.x / len * step - pos[id].x * 0.01;
          pos[id].y += dd.y / len * step - pos[id].y * 0.01;
        });
        temp = Math.max(2, temp * 0.985);
      }
      pos[centre] = { x: 0, y: 0 };
      return { pos: pos, links: links };
    }

    function radius(n) { return 6 + Math.min(14, Math.sqrt(n.size) * 2.2); }

    function draw(ids, lay, centre) {
      while (svg.firstChild) svg.removeChild(svg.firstChild);
      var defs = el('defs', {});
      Object.keys(KINDS).forEach(function (k) {
        var m = el('marker', { id: 'arrow-' + k.replace(/\W+/g, '-'), viewBox: '0 0 10 10', refX: '10', refY: '5', markerWidth: '7', markerHeight: '7', orient: 'auto-start-reverse' });
        m.appendChild(el('path', { d: 'M 0 0 L 10 5 L 0 10 z', fill: KINDS[k].color }));
        defs.appendChild(m);
      });
      svg.appendChild(defs);
      var gl = el('g', { 'class': 'links' }), gn = el('g', { 'class': 'nodes' });
      svg.appendChild(gl); svg.appendChild(gn);
      // several lines between one pair of frames bend apart, so that each stays visible
      var pairCount = {}, pairIndex = {};
      lay.links.forEach(function (e) { var k = [e.from, e.to].sort().join('|'); pairCount[k] = (pairCount[k] || 0) + 1; });
      lay.links.forEach(function (e) {
        var p = lay.pos[e.from], q = lay.pos[e.to], k = KINDS[e.kind] || KINDS.other;
        var dx = q.x - p.x, dy = q.y - p.y, d = Math.sqrt(dx * dx + dy * dy) + 0.01, rq = radius(byId[e.to]) + 2, rp = radius(byId[e.from]);
        var key = [e.from, e.to].sort().join('|'), i = pairIndex[key] = (pairIndex[key] || 0) + 1, cnt = pairCount[key];
        var bend = cnt === 1 ? 0 : (i - (cnt + 1) / 2) * 28 * (e.from < e.to ? 1 : -1);
        var x1 = p.x + dx / d * rp, y1 = p.y + dy / d * rp, x2 = q.x - dx / d * rq, y2 = q.y - dy / d * rq;
        var cx = (x1 + x2) / 2 - dy / d * bend, cy = (y1 + y2) / 2 + dx / d * bend;
        var line = el('path', {
          d: 'M ' + x1 + ' ' + y1 + ' Q ' + cx + ' ' + cy + ' ' + x2 + ' ' + y2, fill: 'none',
          stroke: k.color, 'stroke-width': k.width, 'marker-end': 'url(#arrow-' + e.kind.replace(/\W+/g, '-') + ')',
          'data-from': e.from, 'data-to': e.to
        });
        if (k.dash) line.setAttribute('stroke-dasharray', k.dash);
        var t = el('title', {}); t.textContent = byId[e.from].name + ' ' + e.kind + ' ' + byId[e.to].name + (e.count > 1 ? ' (' + e.count + ' sections)' : '');
        line.appendChild(t);
        gl.appendChild(line);
      });
      ids.forEach(function (id) {
        var n = byId[id], p = lay.pos[id], g = el('g', { 'class': 'node' + (id === centre ? ' centre' : ''), transform: 'translate(' + p.x + ',' + p.y + ')', 'data-id': id });
        g.appendChild(el('circle', { r: radius(n) }));
        var label = el('text', { x: radius(n) + 4, y: 4 }); label.textContent = n.name;
        g.appendChild(label);
        var t = el('title', {}); t.textContent = n.id + ' ' + n.name + ' · ' + n.size + ' concrete frame(s)';
        g.appendChild(t);
        g.addEventListener('click', function (ev) { ev.stopPropagation(); focus(id); });
        g.addEventListener('mouseenter', function () { $(svg).find('path[data-from]').each(function () {
          var on = this.getAttribute('data-from') === id || this.getAttribute('data-to') === id;
          this.classList.toggle('dim', !on); }); });
        g.addEventListener('mouseleave', function () { $(svg).find('path[data-from]').each(function () { this.classList.remove('dim'); }); });
        gn.appendChild(g);
      });
      // fit what was drawn
      var xs = ids.map(function (id) { return lay.pos[id].x; }), ys = ids.map(function (id) { return lay.pos[id].y; });
      var minX = Math.min.apply(null, xs) - 60, maxX = Math.max.apply(null, xs) + 180, minY = Math.min.apply(null, ys) - 40, maxY = Math.max.apply(null, ys) + 40;
      // a small neighbourhood is not blown up: the view is at least as large as a middle-sized one, centred on it
      var w = Math.max(640, maxX - minX), h = Math.max(420, maxY - minY);
      view = { x: (minX + maxX) / 2 - w / 2, y: (minY + maxY) / 2 - h / 2, w: w, h: h };
      setView();
    }

    function setView() { svg.setAttribute('viewBox', view.x + ' ' + view.y + ' ' + view.w + ' ' + view.h); }

    function focus(id, keepPanel) {
      if (!byId[id]) return;
      focusId = id;
      var nb = neighbourhood(id), lay = layout(nb.ids, nb.dist, id);
      draw(nb.ids, lay, id);
      $pane.find('.graph-focus').text(byId[id].id + ' ' + byId[id].name + ' · ' + (nb.ids.length - 1) + ' related frame(s) shown');
      if (!keepPanel && onFocus) onFocus(id);
    }

    // a wheel zooms about the pointer; dragging the background moves the view
    svg.addEventListener('wheel', function (ev) {
      ev.preventDefault();
      var r = svg.getBoundingClientRect(), fx = (ev.clientX - r.left) / r.width, fy = (ev.clientY - r.top) / r.height;
      var s = ev.deltaY > 0 ? 1.15 : 1 / 1.15, nw = view.w * s, nh = view.h * s;
      view.x += (view.w - nw) * fx; view.y += (view.h - nh) * fy; view.w = nw; view.h = nh; setView();
    }, { passive: false });
    var drag = null;
    svg.addEventListener('mousedown', function (ev) { drag = { x: ev.clientX, y: ev.clientY, vx: view.x, vy: view.y }; });
    window.addEventListener('mousemove', function (ev) {
      if (!drag) return;
      var r = svg.getBoundingClientRect();
      view.x = drag.vx - (ev.clientX - drag.x) * view.w / r.width; view.y = drag.vy - (ev.clientY - drag.y) * view.h / r.height; setView();
    });
    window.addEventListener('mouseup', function () { drag = null; });

    $pane.find('.graph-search').on('change', function () {
      var id = (this.value || '').split(' ')[0];
      if (byId[id]) focus(id);
    });
    $pane.find('.graph-depth').on('change', function () { depth = parseInt(this.value, 10) || 1; if (focusId) focus(focusId, true); });
    legend();

    return {
      // the first time the tab is shown: the graph is read, and the frame with the most relations is put in the middle
      start: function () {
        if (graph) return;
        load(function () {
          var best = null;
          graph.nodes.forEach(function (n) { if (!best || adj[n.id].length > adj[best].length) best = n.id; });
          if (best) focus(best);
        });
      },
      focus: focus
    };
  };
})();
