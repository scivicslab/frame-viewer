package com.scivicslab.frameviewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pure tests: the workflows numbered in the order they run, and one workflow's purpose, params and transitions. */
class WorkflowPanelTest {

    private static final String YAML = """
            name: book-to-rules
            description: |
              Makes every markdown file of a book into a rule file.
              Files that already have one are skipped.
            params:
              book:
                description: "The book's directory"
                required: true
              maxQuestions:
                description: "How many questions at most"
                default: 100
            steps:
              - states: ["0", "1"]
                label: add-writer
                note: The writer has no history.
                actions:
                  - actor: this
                    method: addWorker
                    arguments: ["writer", 'jexl:state.getString("writer")']
              - states: ["1", "pick"]
                label: find-the-next-file
                actions:
                  - actor: this
                    method: putJson
                    arguments: {path: asked, value: "0"}
            """;

    private static WorkflowPanel panel() {
        return new WorkflowPanel(List.of("book-to-rules", "concept-tree"), Map.of("book-to-rules", YAML), "wf");
    }

    @Test
    void theWorkflowsAreNumberedInTheOrderTheyRunAndAMissingOneSaysSo() {
        List<Library.TreeNode> t = panel().tree();
        assertEquals("1. book-to-rules", t.get(0).text());
        assertEquals("Makes every markdown file of a book into a rule file.", t.get(0).li_attr().get("title"));
        assertEquals("2. concept-tree (missing)", t.get(1).text());
    }

    @Test
    void aWorkflowShowsWhatItIsForWhatItTakesAndItsTransitions() {
        String html = panel().render("book-to-rules");
        assertTrue(html.contains("<h2 class=\"name\">book-to-rules</h2>"), html);
        assertTrue(html.contains("data-edit-works=\"wf/book-to-rules.yaml\""), html);
        assertTrue(html.contains("Files that already have one are skipped."), html);
        assertTrue(html.contains("<td class=\"name\">book</td><td>The book&#39;s directory</td><td class=\"name\">(required)</td>")
                || html.contains("<td class=\"name\">book</td><td>The book's directory</td><td class=\"name\">(required)</td>"), html);
        assertTrue(html.contains("<td class=\"name\">maxQuestions</td><td>How many questions at most</td><td class=\"name\">100</td>"), html);
        assertTrue(html.contains("<td class=\"name\">0</td><td class=\"name\">1</td><td><span class=\"name\">add-writer</span>"
                + "<div class=\"hint\">The writer has no history.</div>"), html);
        assertTrue(html.contains("this.addWorker(&quot;writer&quot;, &quot;jexl:state.getString(&quot;writer&quot;)&quot;)"), html);
        assertTrue(html.contains("this.putJson(path: asked, value: 0)"), html);
        assertTrue(html.contains("<h3>As written</h3><pre class=\"raw\">name: book-to-rules"), html);
    }
}
