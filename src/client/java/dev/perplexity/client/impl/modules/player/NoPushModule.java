package dev.perplexity.client.impl.modules.player;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.utils.Obf;

/** Prevents local collision and liquid-current movement without affecting other entities. */
public final class NoPushModule extends Module {
    private static NoPushModule instance;

    private final BooleanSetting entities = add(new BooleanSetting("Entities", true));
    private final BooleanSetting liquids = add(new BooleanSetting("Liquids", true));
    private final BooleanSetting windCharges = add(new BooleanSetting("Wind Charges", false));

    public NoPushModule() {
        super(Category.PLAYER);
        instance = this;
    }

    public static boolean blocksEntityPush() {
        return instance != null && instance.isEnabled() && instance.entities.get();
    }

    public static boolean blocksLiquidPush() {
        return instance != null && instance.isEnabled() && instance.liquids.get();
    }

    public static boolean blocksWindCharges() {
        return instance != null && instance.isEnabled() && instance.windCharges.get();
    }

    @Override
    public String getDisplayName() {
        return Obf.s("EFVXQThxAw==");
    }
}
