package dev.perplexity.client.module;

/**
 * Base for visual features. It fixes the category and inherits the shared frame/world hooks,
 * so render modules do not need their own renderer callback registration.
 */
public abstract class RenderModule extends Module {
    protected RenderModule() {
        super(Category.RENDER);
    }
}
