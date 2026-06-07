package com.dify.workflow.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for VariableResolver.
 */
class VariableResolverTest {

    private final VariableResolver resolver = new VariableResolver();

    @Test
    void testContainsVariable() {
        assertTrue(resolver.containsVariable("{{#start_node.query#}}"));
        assertTrue(resolver.containsVariable("Hello {{#name#}}!"));
        assertFalse(resolver.containsVariable("Hello World!"));
        assertFalse(resolver.containsVariable(null));
        assertFalse(resolver.containsVariable(""));
    }

    @Test
    void testExtractReferences() {
        String template = "Hello {{#start_node.query#}}, your result is {{#llm_node.text#}}";
        List<VariableResolver.VariableRef> refs = resolver.extractReferences(template);

        assertEquals(2, refs.size());
        assertEquals("start_node", refs.get(0).nodeId());
        assertEquals("query", refs.get(0).field());
        assertEquals("llm_node", refs.get(1).nodeId());
        assertEquals("text", refs.get(1).field());
    }

    @Test
    void testExtractSystemVariable() {
        String template = "System query: {{#sys.query#}}";
        List<VariableResolver.VariableRef> refs = resolver.extractReferences(template);

        assertEquals(1, refs.size());
        assertEquals(VariableResolver.VariableRef.Type.SYSTEM, refs.get(0).type());
        assertEquals("query", refs.get(0).field());
    }

    @Test
    void testExtractEnvironmentVariable() {
        String template = "API Key: {{#env.API_KEY#}}";
        List<VariableResolver.VariableRef> refs = resolver.extractReferences(template);

        assertEquals(1, refs.size());
        assertEquals(VariableResolver.VariableRef.Type.ENVIRONMENT, refs.get(0).type());
        assertEquals("API_KEY", refs.get(0).field());
    }

    @Test
    void testResolve() {
        String template = "Hello {{#name#}}, your score is {{#score#}}";

        java.util.Map<String, String> variables = new java.util.HashMap<>();
        variables.put("name", "Alice");
        variables.put("score", "95");

        String resolved = resolver.resolve(template, ref -> {
            if (ref.type() == VariableResolver.VariableRef.Type.NODE) {
                return variables.get(ref.field());
            }
            return null;
        });

        assertEquals("Hello Alice, your score is 95", resolved);
    }
}
