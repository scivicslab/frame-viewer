# frame-viewer

A viewer for the frame library of [rst-on-lisp](https://github.com/scivicslab/rst-on-lisp).

It shows, side by side:

- the subject-tag trees built from each book's table of contents (`rst-on-lisp/subjects/`),
  with the nodes that have a frame marked;
- the integrated explanation frames (`rst-on-lisp/frames/explanation/`);
- for the selected frame, its members with their instructions, samples, relations to the
  nucleus and `:rank`, its sub-frames, the paragraphs of the source text it was made from
  (and those sent elsewhere or left out), and the frames it was integrated into or from.

The design documents are in the `Frame-Viewer` section of the documentation portal.
