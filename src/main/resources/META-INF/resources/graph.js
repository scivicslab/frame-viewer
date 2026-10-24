// The drawing in the Integrated frames tab: the generalized frames of the registry and the relations between them, drawn around one frame
// with Cytoscape.js. The frame chosen sits in the middle; the frames it is related to, up to the depth chosen, are laid
// out around it by fCoSE. The is-a tree is drawn as nesting: a frame shown with the frame above it in the tree sits in
// that frame's box. The other relations are arrows, coloured by kind. Clicking a frame moves the middle to it and shows
// its panel on the right; the view zooms and pans with the wheel and the mouse.
(function () {
  var KINDS = {
    'is-a (tree)': { color: '#2b6cb0', label: 'is-a (the tree, drawn as a box)', style: 'solid', width: 2.5 },
    'is-a': { color: '#4299e1', label: 'is-a (a section said)', style: 'dashed', width: 1.8 },
    'part-of': { color: '#2f855a', label: 'part-of', style: 'solid', width: 1.8 },
    'feature': { color: '#b7791f', label: 'feature', style: 'solid', width: 1.8 },
    'operation': { color: '#805ad5', label: 'operation', style: 'solid', width: 1.8 },
    'other': { color: '#a0aec0', label: 'other', style: 'solid', width: 1.3 }
  };
  var MAX_NODES = 70;

  if (window.cytoscape && window.cytoscapeFcose) cytoscape.use(cytoscapeFcose);

  function cls(kind) { return 'k-' + kind.replace(/\W+/g, '-'); }

  function styles() {
    var s = [
      { selector: 'node', style: {
        'shape': 'round-rectangle', 'background-color': '#edf2f7', 'border-width': 1.2, 'border-color': '#4a5568',
        'label': 'data(label)', 'font-size': 11, 'color': '#1a202c', 'text-valign': 'center', 'text-halign': 'center',
        'text-wrap': 'wrap', 'text-max-width': 120, 'padding': 4, 'width': 'data(w)', 'height': 'data(h)'
      } },
      { selector: 'node:parent', style: {
        'background-color': '#ebf8ff', 'background-opacity': 0.55, 'border-color': '#2b6cb0', 'border-width': 1.5,
        'border-style': 'solid', 'text-valign': 'top', 'text-halign': 'center', 'font-weight': 'bold', 'color': '#2c5282',
        'padding': 14, 'shape': 'round-rectangle'
      } },
      { selector: 'node.centre', style: { 'background-color': '#2b6cb0', 'color': '#ffffff', 'border-color': '#1a365d', 'font-weight': 'bold', 'font-size': 13 } },
      { selector: 'node:parent.centre', style: { 'background-color': '#bee3f8', 'color': '#1a365d' } },
      { selector: 'node.faded, edge.faded', style: { 'opacity': 0.15 } },
      { selector: 'edge', style: {
        'width': 1.5, 'curve-style': 'bezier', 'target-arrow-shape': 'triangle', 'arrow-scale': 0.9, 'line-color': '#a0aec0',
        'target-arrow-color': '#a0aec0', 'font-size': 10, 'text-rotation': 'autorotate', 'text-background-color': '#ffffff',
        'text-background-opacity': 0.85, 'text-background-padding': 2, 'color': '#2d3748'
      } },
      { selector: 'edge.labelled', style: { 'label': 'data(kind)' } }
    ];
    Object.keys(KINDS).forEach(function (k) {
      s.push({ selector: 'edge.' + cls(k), style: { 'line-color': KINDS[k].color, 'target-arrow-color': KINDS[k].color, 'line-style': KINDS[k].style, 'width': KINDS[k].width } });
    });
    return s;
  }

  window.RelationsGraph = function ($pane, onFocus) {
    var graph = null, byId = {}, adj = {}, parentOf = {}, shown = {}, depth = 1, focusId = null;
    var cy = cytoscape({ container: $pane.find('.graph')[0], style: styles(), wheelSensitivity: 0.25, minZoom: 0.15, maxZoom: 3 });
    // fitting a few frames would blow them up; a fit stops at this zoom
    var FIT_ZOOM = 1.2;
    window.relationsCy = cy;

    function kindOn(k) { return shown[k] !== false; }

    function legend() {
      var $l = $pane.find('.graph-legend').empty();
      Object.keys(KINDS).forEach(function (k) {
        var $c = $('<label class="graph-kind"></label>');
        $('<input type="checkbox">').prop('checked', kindOn(k)).on('change', function () {
          shown[k] = this.checked; if (focusId) focus(focusId, true);
        }).appendTo($c);
        $('<span class="swatch"></span>').css({ background: KINDS[k].color, opacity: KINDS[k].style === 'dashed' ? 0.6 : 1 }).appendTo($c);
        $c.append(document.createTextNode(KINDS[k].label));
        $l.append($c);
      });
    }

    function load(done) {
      $.getJSON('registry-graph', function (g) {
        graph = g; byId = {}; adj = {}; parentOf = {};
        g.nodes.forEach(function (n) { byId[n.id] = n; adj[n.id] = []; });
        g.edges.forEach(function (e) {
          if (!adj[e.from] || !adj[e.to]) return;
          adj[e.from].push({ other: e.to, edge: e });
          adj[e.to].push({ other: e.from, edge: e });
          if (e.kind === 'is-a (tree)') parentOf[e.from] = e.to;
        });
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
      return order;
    }

    function elements(ids, centre) {
      var inSet = {}, els = [];
      ids.forEach(function (id) { inSet[id] = true; });
      // the is-a tree as nesting: a frame whose parent is shown sits in the parent's box
      var nest = kindOn('is-a (tree)');
      ids.forEach(function (id) {
        var n = byId[id], label = n.name + (n.size > 1 ? ' (' + n.size + ')' : '');
        // the box fits its name: about 6.5 pixels a letter at 11 pixels, wrapped at 120
        var lines = Math.ceil(label.length * 6.5 / 120), w = Math.min(128, 14 + label.length * 6.5), h = 10 + lines * 14;
        var data = { id: id, label: label, name: n.name, size: n.size, w: w, h: h };
        if (nest && parentOf[id] && inSet[parentOf[id]]) data.parent = parentOf[id];
        els.push({ group: 'nodes', data: data, classes: id === centre ? 'centre' : '' });
      });
      graph.edges.forEach(function (e, i) {
        if (!inSet[e.from] || !inSet[e.to] || !kindOn(e.kind) || e.from === e.to) return;
        if (nest && e.kind === 'is-a (tree)') return;
        els.push({ group: 'edges', data: { id: 'e' + i, source: e.from, target: e.to, kind: e.kind, count: e.count }, classes: cls(e.kind) });
      });
      return els;
    }

    function focus(id, keepPanel) {
      if (!byId[id]) return;
      focusId = id;
      var ids = neighbourhood(id);
      cy.elements().remove();
      cy.add(elements(ids, id));
      cy.layout({
        name: window.cytoscapeFcose ? 'fcose' : 'cose', animate: true, animationDuration: 500, randomize: true, fit: true,
        padding: 30, nodeRepulsion: 9000, idealEdgeLength: 110, nodeSeparation: 80, packComponents: true,
        nestingFactor: 0.6, gravity: 0.3, numIter: 2500,
        stop: function () { cy.fit(cy.elements(), 30); if (cy.zoom() > FIT_ZOOM) { cy.zoom(FIT_ZOOM); cy.center(); } }
      }).run();
      $pane.find('.graph-focus').removeClass('hint').text(byId[id].id + ' ' + byId[id].name + ' · ' + (ids.length - 1) + ' related frame(s)');
      if (!keepPanel && onFocus) onFocus(id);
    }

    cy.on('tap', 'node', function (ev) { focus(ev.target.id()); });
    // a frame under the pointer: its lines named, the rest faded
    cy.on('mouseover', 'node', function (ev) {
      var n = ev.target, near = n.closedNeighborhood().union(n.ancestors()).union(n.descendants());
      cy.elements().not(near).addClass('faded');
      n.connectedEdges().addClass('labelled');
    });
    cy.on('mouseout', 'node', function () { cy.elements().removeClass('faded').removeClass('labelled'); });
    cy.on('mouseover', 'edge', function (ev) { ev.target.addClass('labelled'); });
    cy.on('mouseout', 'edge', function (ev) { ev.target.removeClass('labelled'); });

    $pane.find('.graph-depth').on('change', function () { depth = parseInt(this.value, 10) || 1; if (focusId) focus(focusId, true); });
    legend();

    return {
      // the tab shown: the drawing takes its size; the graph is read once
      start: function () { cy.resize(); if (!graph) load(); },
      // a frame chosen elsewhere (the tree, a link): drawn in the middle once the graph is read
      focus: function (id, keepPanel) {
        cy.resize();
        if (graph) focus(id, keepPanel); else load(function () { focus(id, keepPanel); });
      }
    };
  };
})();
