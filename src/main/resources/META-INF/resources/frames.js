// The script of the Integrated frames pages: the drawing of the registry above the page's text, and the line between them.
// The drawing is read from registry-graph; on frames/<id> it is drawn around that frame, on frames it shows them all.
// A frame clicked in the drawing opens its page, as its link in the tree or the text does.
$(function () {
  var base = document.body.getAttribute('data-base') || '';
  var $col = $('.right-col'), $g = $col.find('.graph-pane'), drag = null;
  var focusId = $col.attr('data-focus');
  var relations = RelationsGraph($g, function (id) { location.href = base + 'frames/' + encodeURIComponent(id); });
  if (focusId) relations.focus(focusId, true); else relations.whole();

  // the line between the drawing and the text moves up and down; the height is kept for the next page
  try { var h = parseInt(localStorage.getItem('frame-viewer-graph-height'), 10); if (h > 0) $g.css('height', h + 'px'); } catch (e) { }
  relations.resize(true);
  $col.find('.splitter').on('mousedown', function (e) {
    e.preventDefault();
    drag = { y: e.clientY, h: $g.outerHeight() };
    $('body').addClass('dragging-split');
  });
  $(window).on('mousemove', function (e) {
    if (!drag) return;
    var max = $col.height() - 80, h = Math.max(120, Math.min(max, drag.h + e.clientY - drag.y));
    $g.css('height', h + 'px');
    relations.resize();
  }).on('mouseup', function () {
    if (!drag) return;
    drag = null;
    $('body').removeClass('dragging-split');
    try { localStorage.setItem('frame-viewer-graph-height', String($g.outerHeight())); } catch (e) { }
    relations.resize(true);
  });
});
