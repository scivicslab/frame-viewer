package com.scivicslab.frameviewer;

import java.util.Map;

/** One node of a tab's tree, as jsTree reads it: its id, its parent's id ({@code #} for a root), its text, its attributes. */
public record TreeNode(String id, String parent, String text, Map<String, String> li_attr) {
}
