package dev.perplexity.client.module.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting {
    private final String name;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** Controls whether this setting is currently drawn in the ClickGUI. */
    public void setVisibleWhen(BooleanSupplier visibility) {
        this.visibility = visibility == null ? () -> true : visibility;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }
}
