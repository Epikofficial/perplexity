package dev.perplexity.client.impl.modules.render;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.RenderModule;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.utils.NightVision;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Classic XRay: everything but the enabled groups stops rendering, with fullbright while on.
 * Each ore family, storage and the extras pool toggle separately. Chunks rebuild on toggle;
 * gamma is restored on disable.
 */
public final class OreViewModule extends RenderModule {
    private static final Minecraft mc = Minecraft.getInstance();
    private static OreViewModule instance;

    private static final Set<Block> COAL = Set.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
    private static final Set<Block> IRON = Set.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
    private static final Set<Block> COPPER = Set.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
    private static final Set<Block> GOLD = Set.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
    private static final Set<Block> REDSTONE = Set.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
    private static final Set<Block> LAPIS = Set.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
    private static final Set<Block> DIAMOND = Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
    private static final Set<Block> EMERALD = Set.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
    private static final Set<Block> NETHER = Set.of(Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE);

    private static final Set<Block> STORAGE = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL,
            Blocks.SHULKER_BOX,
            Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX,
            Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX,
            Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX,
            Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
            Blocks.BLACK_SHULKER_BOX);

    private static final Set<Block> EXTRAS = Set.of(
            Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT,
            Blocks.END_PORTAL_FRAME, Blocks.BUDDING_AMETHYST,
            Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);

    private final BooleanSetting coal = add(new BooleanSetting("Coal", true));
    private final BooleanSetting iron = add(new BooleanSetting("Iron", true));
    private final BooleanSetting copper = add(new BooleanSetting("Copper", true));
    private final BooleanSetting gold = add(new BooleanSetting("Gold", true));
    private final BooleanSetting redstone = add(new BooleanSetting("Redstone", true));
    private final BooleanSetting lapis = add(new BooleanSetting("Lapis", true));
    private final BooleanSetting diamond = add(new BooleanSetting("Diamond", true));
    private final BooleanSetting emerald = add(new BooleanSetting("Emerald", true));
    private final BooleanSetting netherOres = add(new BooleanSetting("Nether Ores", true));
    private final BooleanSetting debris = add(new BooleanSetting("Ancient Debris", true));
    private final BooleanSetting storage = add(new BooleanSetting("Storage", true));
    private final BooleanSetting extras = add(new BooleanSetting("Extras", true));

    private double prevGamma = 1.0;
    private final NightVision nightVision = new NightVision();

    public OreViewModule() {
        super();
        instance = this;
    }

    @Override
    protected void onEnable() {
        prevGamma = mc.options.gamma().get();
        mc.options.gamma().set(10.0);
        nightVision.enable();
        reloadChunks();
    }

    @Override
    protected void onDisable() {
        mc.options.gamma().set(prevGamma);
        nightVision.disable();
        reloadChunks();
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.AFTER) {
            return;
        }
        nightVision.tick();
    }

    public static boolean active() {
        return instance != null && instance.isEnabled();
    }

    /** True when this state should not render at all under XRay. */
    public static boolean isHidden(BlockState state) {
        if (!active()) {
            return false;
        }
        Block block = state.getBlock();
        return !instance.oreShown(block)
                && !(instance.storage.get() && STORAGE.contains(block))
                && !(instance.extras.get() && EXTRAS.contains(block));
    }

    private boolean oreShown(Block block) {
        if (COAL.contains(block)) {
            return coal.get();
        }
        if (IRON.contains(block)) {
            return iron.get();
        }
        if (COPPER.contains(block)) {
            return copper.get();
        }
        if (GOLD.contains(block)) {
            return gold.get();
        }
        if (REDSTONE.contains(block)) {
            return redstone.get();
        }
        if (LAPIS.contains(block)) {
            return lapis.get();
        }
        if (DIAMOND.contains(block)) {
            return diamond.get();
        }
        if (EMERALD.contains(block)) {
            return emerald.get();
        }
        if (NETHER.contains(block)) {
            return netherOres.get();
        }
        return block == Blocks.ANCIENT_DEBRIS && debris.get();
    }

    private static void reloadChunks() {
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }
    @Override
    public String getDisplayName() {
        return Obf.s("BmgWaA==");
    }

}
