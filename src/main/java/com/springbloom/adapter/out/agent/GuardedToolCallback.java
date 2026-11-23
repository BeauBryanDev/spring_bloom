package com.springbloom.adapter.out.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** Wraps a tool so every call is counted, limited and its numbers remembered. */
final class GuardedToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(GuardedToolCallback.class);

    private final ToolCallback delegate;
    private final TurnToolGuard guard;

    GuardedToolCallback(ToolCallback delegate, TurnToolGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return run(toolInput, () -> delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return run(toolInput, () -> delegate.call(toolInput, toolContext));
    }

    private String run(String input, java.util.function.Supplier<String> action) {

        String name = delegate.getToolDefinition().name();
        String refusal = guard.admit(name, input);

        if (refusal != null) {
            log.warn("Tool {} refused by the turn guard", name);
            return refusal;
        }
        String output = action.get();
        guard.observe(output);
        return output;
    }
}
