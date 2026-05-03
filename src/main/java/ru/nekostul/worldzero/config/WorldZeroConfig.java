package ru.nekostul.worldzero.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WorldZeroConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue WORLDZERO_HOUSE_EVENT_ENABLED;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_ACTIVE_START_MINUTES;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_ACTIVE_END_MINUTES;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_REAL_FIRE_MIN_MINUTES;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_REAL_FIRE_MAX_MINUTES;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_INITIAL_DELAY_MIN_SECONDS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_INITIAL_DELAY_MAX_SECONDS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_REPEAT_MIN_MINUTES;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_REPEAT_MAX_MINUTES;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_REPEAT_DELAY_MIN_SECONDS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_REPEAT_DELAY_MAX_SECONDS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_LIFETIME_MIN_SECONDS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_LIFETIME_MAX_SECONDS;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_TRIGGER_DISTANCE_MIN_BLOCKS;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_TRIGGER_DISTANCE_MAX_BLOCKS;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_RESTORE_DISTANCE_MIN_BLOCKS;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_RESTORE_DISTANCE_MAX_BLOCKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_BEDROCK_MAX_BLOCKS;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_DISAPPEAR_DISTANCE_BLOCKS;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_FIRE_CLEAR_DISTANCE_BLOCKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_SCAN_INTERVAL_SECONDS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_SEARCH_RADIUS_BLOCKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_SEARCH_VERTICAL_RANGE_BLOCKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_ROOM_MAX_HORIZONTAL_RADIUS_BLOCKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_ROOM_MAX_HEIGHT_BLOCKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_SCORE_THRESHOLD;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_FEATURE_CATEGORY_THRESHOLD;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_MIN_WALL_COVERAGE;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_MIN_ROOF_COVERAGE;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_ACTION_MIN_TICKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_ACTION_MAX_TICKS;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_PLAYER_STARE_CHANCE;
    private static final ForgeConfigSpec.DoubleValue WORLDZERO_HOUSE_FREEZE_VANISH_CHANCE;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_FREEZE_VANISH_MIN_TICKS;
    private static final ForgeConfigSpec.IntValue WORLDZERO_HOUSE_FREEZE_VANISH_MAX_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("house_event");
        WORLDZERO_HOUSE_EVENT_ENABLED = builder
                .comment("Enables the horror house scene.")
                .define("enabled", true);
        WORLDZERO_HOUSE_ACTIVE_START_MINUTES = builder
                .comment("In-game minute when the repeating house event window starts.")
                .defineInRange("active_start_minutes", 90, 1, 600);
        WORLDZERO_HOUSE_ACTIVE_END_MINUTES = builder
                .comment("In-game minute when the repeating house event window ends.")
                .defineInRange("active_end_minutes", 150, 1, 600);
        WORLDZERO_HOUSE_REAL_FIRE_MIN_MINUTES = builder
                .comment("Earliest in-game minute for the final real house fire.")
                .defineInRange("real_fire_min_minutes", 140, 1, 600);
        WORLDZERO_HOUSE_REAL_FIRE_MAX_MINUTES = builder
                .comment("Latest in-game minute for the final real house fire.")
                .defineInRange("real_fire_max_minutes", 150, 1, 600);
        WORLDZERO_HOUSE_INITIAL_DELAY_MIN_SECONDS = builder
                .comment("First trigger delay minimum in seconds.")
                .defineInRange("initial_delay_min_seconds", 1, 0, 60);
        WORLDZERO_HOUSE_INITIAL_DELAY_MAX_SECONDS = builder
                .comment("First trigger delay maximum in seconds.")
                .defineInRange("initial_delay_max_seconds", 2, 0, 60);
        WORLDZERO_HOUSE_REPEAT_MIN_MINUTES = builder
                .comment("Repeat cooldown minimum in minutes.")
                .defineInRange("repeat_min_minutes", 10, 1, 180);
        WORLDZERO_HOUSE_REPEAT_MAX_MINUTES = builder
                .comment("Repeat cooldown maximum in minutes.")
                .defineInRange("repeat_max_minutes", 15, 1, 180);
        WORLDZERO_HOUSE_REPEAT_DELAY_MIN_SECONDS = builder
                .comment("Delay before repeated appearances in seconds.")
                .defineInRange("repeat_delay_min_seconds", 3, 0, 60);
        WORLDZERO_HOUSE_REPEAT_DELAY_MAX_SECONDS = builder
                .comment("Maximum delay before repeated appearances in seconds.")
                .defineInRange("repeat_delay_max_seconds", 3, 0, 60);
        WORLDZERO_HOUSE_LIFETIME_MIN_SECONDS = builder
                .comment("Scene lifetime minimum in seconds.")
                .defineInRange("lifetime_min_seconds", 10, 1, 120);
        WORLDZERO_HOUSE_LIFETIME_MAX_SECONDS = builder
                .comment("Scene lifetime maximum in seconds.")
                .defineInRange("lifetime_max_seconds", 20, 1, 120);
        WORLDZERO_HOUSE_TRIGGER_DISTANCE_MIN_BLOCKS = builder
                .comment("Minimum horizontal distance to trigger bedrock house illusion.")
                .defineInRange("trigger_distance_min_blocks", 65.0D, 1.0D, 256.0D);
        WORLDZERO_HOUSE_TRIGGER_DISTANCE_MAX_BLOCKS = builder
                .comment("Maximum horizontal distance to trigger bedrock house illusion.")
                .defineInRange("trigger_distance_max_blocks", 70.0D, 1.0D, 256.0D);
        WORLDZERO_HOUSE_RESTORE_DISTANCE_MIN_BLOCKS = builder
                .comment("Minimum distance at which bedrock illusion restores the original house view.")
                .defineInRange("restore_distance_min_blocks", 15.0D, 1.0D, 128.0D);
        WORLDZERO_HOUSE_RESTORE_DISTANCE_MAX_BLOCKS = builder
                .comment("Maximum distance at which bedrock illusion restores the original house view.")
                .defineInRange("restore_distance_max_blocks", 20.0D, 1.0D, 128.0D);
        WORLDZERO_HOUSE_BEDROCK_MAX_BLOCKS = builder
                .comment("Maximum number of visual fire blocks for the house illusion.")
                .defineInRange("bedrock_max_blocks", 2500, 64, 12000);
        WORLDZERO_HOUSE_DISAPPEAR_DISTANCE_BLOCKS = builder
                .comment("Distance at which the house black echo instantly disappears.")
                .defineInRange("disappear_distance_blocks", 15.0D, 2.0D, 64.0D);
        WORLDZERO_HOUSE_FIRE_CLEAR_DISTANCE_BLOCKS = builder
                .comment("Distance at which the house fire illusion clears.")
                .defineInRange("fire_clear_distance_blocks", 4.0D, 1.0D, 32.0D);
        WORLDZERO_HOUSE_SCAN_INTERVAL_SECONDS = builder
                .comment("Detector scan interval in seconds.")
                .defineInRange("scan_interval_seconds", 5, 1, 60);
        WORLDZERO_HOUSE_SEARCH_RADIUS_BLOCKS = builder
                .comment("Horizontal detector search radius.")
                .defineInRange("search_radius_blocks", 20, 6, 64);
        WORLDZERO_HOUSE_SEARCH_VERTICAL_RANGE_BLOCKS = builder
                .comment("Vertical detector search range above and below the player.")
                .defineInRange("search_vertical_range_blocks", 32, 2, 256);
        WORLDZERO_HOUSE_ROOM_MAX_HORIZONTAL_RADIUS_BLOCKS = builder
                .comment("Maximum horizontal flood-fill reach from detected room center.")
                .defineInRange("room_max_horizontal_radius_blocks", 12, 4, 24);
        WORLDZERO_HOUSE_ROOM_MAX_HEIGHT_BLOCKS = builder
                .comment("Maximum flood-fill room height for detection.")
                .defineInRange("room_max_height_blocks", 10, 3, 16);
        WORLDZERO_HOUSE_SCORE_THRESHOLD = builder
                .comment("Minimum score required for a structure to count as a house.")
                .defineInRange("score_threshold", 9, 1, 32);
        WORLDZERO_HOUSE_FEATURE_CATEGORY_THRESHOLD = builder
                .comment("Minimum number of secondary house feature categories.")
                .defineInRange("feature_category_threshold", 2, 0, 8);
        WORLDZERO_HOUSE_MIN_WALL_COVERAGE = builder
                .comment("Minimum closed wall coverage ratio.")
                .defineInRange("min_wall_coverage", 0.72D, 0.0D, 1.0D);
        WORLDZERO_HOUSE_MIN_ROOF_COVERAGE = builder
                .comment("Minimum roof coverage ratio.")
                .defineInRange("min_roof_coverage", 0.7D, 0.0D, 1.0D);
        WORLDZERO_HOUSE_ACTION_MIN_TICKS = builder
                .comment("Minimum delay between fake building actions in ticks.")
                .defineInRange("action_min_ticks", 8, 1, 200);
        WORLDZERO_HOUSE_ACTION_MAX_TICKS = builder
                .comment("Maximum delay between fake building actions in ticks.")
                .defineInRange("action_max_ticks", 18, 1, 200);
        WORLDZERO_HOUSE_PLAYER_STARE_CHANCE = builder
                .comment("Chance per action cycle to stare directly at the player.")
                .defineInRange("player_stare_chance", 0.15D, 0.0D, 1.0D);
        WORLDZERO_HOUSE_FREEZE_VANISH_CHANCE = builder
                .comment("Rare chance for the fake builder to freeze and then vanish.")
                .defineInRange("freeze_vanish_chance", 0.08D, 0.0D, 1.0D);
        WORLDZERO_HOUSE_FREEZE_VANISH_MIN_TICKS = builder
                .comment("Minimum freeze duration before rare vanish.")
                .defineInRange("freeze_vanish_min_ticks", 8, 1, 200);
        WORLDZERO_HOUSE_FREEZE_VANISH_MAX_TICKS = builder
                .comment("Maximum freeze duration before rare vanish.")
                .defineInRange("freeze_vanish_max_ticks", 18, 1, 200);
        builder.pop();

        SPEC = builder.build();
    }

    private WorldZeroConfig() {
    }

    public static boolean worldzero$isHouseEventEnabled() {
        return WORLDZERO_HOUSE_EVENT_ENABLED.get();
    }

    public static int worldzero$houseInitialDelayMinTicks() {
        return WORLDZERO_HOUSE_INITIAL_DELAY_MIN_SECONDS.get() * 20;
    }

    public static int worldzero$houseInitialDelayMaxTicks() {
        return WORLDZERO_HOUSE_INITIAL_DELAY_MAX_SECONDS.get() * 20;
    }

    public static int worldzero$houseRepeatMinTicks() {
        return WORLDZERO_HOUSE_REPEAT_MIN_MINUTES.get() * 60 * 20;
    }

    public static int worldzero$houseRepeatMaxTicks() {
        return WORLDZERO_HOUSE_REPEAT_MAX_MINUTES.get() * 60 * 20;
    }

    public static long worldzero$houseActiveStartTick() {
        return (long) WORLDZERO_HOUSE_ACTIVE_START_MINUTES.get() * 60L * 20L;
    }

    public static long worldzero$houseActiveEndTick() {
        return Math.max(
                worldzero$houseActiveStartTick(),
                (long) WORLDZERO_HOUSE_ACTIVE_END_MINUTES.get() * 60L * 20L
        );
    }

    public static long worldzero$houseRealFireMinTick() {
        long minTick = (long) WORLDZERO_HOUSE_REAL_FIRE_MIN_MINUTES.get() * 60L * 20L;
        return Math.max(worldzero$houseActiveStartTick(), Math.min(minTick, worldzero$houseActiveEndTick()));
    }

    public static long worldzero$houseRealFireMaxTick() {
        long maxTick = (long) WORLDZERO_HOUSE_REAL_FIRE_MAX_MINUTES.get() * 60L * 20L;
        return Math.max(worldzero$houseRealFireMinTick(), Math.min(maxTick, worldzero$houseActiveEndTick()));
    }

    public static int worldzero$houseRepeatDelayMinTicks() {
        return WORLDZERO_HOUSE_REPEAT_DELAY_MIN_SECONDS.get() * 20;
    }

    public static int worldzero$houseRepeatDelayMaxTicks() {
        return WORLDZERO_HOUSE_REPEAT_DELAY_MAX_SECONDS.get() * 20;
    }

    public static int worldzero$houseLifetimeMinTicks() {
        return WORLDZERO_HOUSE_LIFETIME_MIN_SECONDS.get() * 20;
    }

    public static int worldzero$houseLifetimeMaxTicks() {
        return WORLDZERO_HOUSE_LIFETIME_MAX_SECONDS.get() * 20;
    }

    public static double worldzero$houseTriggerDistanceMinBlocks() {
        return Math.max(1.0D, WORLDZERO_HOUSE_TRIGGER_DISTANCE_MIN_BLOCKS.get());
    }

    public static double worldzero$houseTriggerDistanceMaxBlocks() {
        return Math.max(
                worldzero$houseTriggerDistanceMinBlocks(),
                WORLDZERO_HOUSE_TRIGGER_DISTANCE_MAX_BLOCKS.get()
        );
    }

    public static double worldzero$houseRestoreDistanceMinBlocks() {
        return Math.max(1.0D, WORLDZERO_HOUSE_RESTORE_DISTANCE_MIN_BLOCKS.get());
    }

    public static double worldzero$houseRestoreDistanceMaxBlocks() {
        return Math.max(
                worldzero$houseRestoreDistanceMinBlocks(),
                WORLDZERO_HOUSE_RESTORE_DISTANCE_MAX_BLOCKS.get()
        );
    }

    public static int worldzero$houseBedrockMaxBlocks() {
        return WORLDZERO_HOUSE_BEDROCK_MAX_BLOCKS.get();
    }

    public static double worldzero$houseDisappearDistanceBlocks() {
        return WORLDZERO_HOUSE_DISAPPEAR_DISTANCE_BLOCKS.get();
    }

    public static double worldzero$houseFireClearDistanceBlocks() {
        return WORLDZERO_HOUSE_FIRE_CLEAR_DISTANCE_BLOCKS.get();
    }

    public static int worldzero$houseScanIntervalTicks() {
        return WORLDZERO_HOUSE_SCAN_INTERVAL_SECONDS.get() * 20;
    }

    public static int worldzero$houseSearchRadiusBlocks() {
        return WORLDZERO_HOUSE_SEARCH_RADIUS_BLOCKS.get();
    }

    public static int worldzero$houseSearchVerticalRangeBlocks() {
        return Math.max(32, WORLDZERO_HOUSE_SEARCH_VERTICAL_RANGE_BLOCKS.get());
    }

    public static int worldzero$houseRoomMaxHorizontalRadiusBlocks() {
        return WORLDZERO_HOUSE_ROOM_MAX_HORIZONTAL_RADIUS_BLOCKS.get();
    }

    public static int worldzero$houseRoomMaxHeightBlocks() {
        return WORLDZERO_HOUSE_ROOM_MAX_HEIGHT_BLOCKS.get();
    }

    public static int worldzero$houseScoreThreshold() {
        return WORLDZERO_HOUSE_SCORE_THRESHOLD.get();
    }

    public static int worldzero$houseFeatureCategoryThreshold() {
        return WORLDZERO_HOUSE_FEATURE_CATEGORY_THRESHOLD.get();
    }

    public static double worldzero$houseMinWallCoverage() {
        return WORLDZERO_HOUSE_MIN_WALL_COVERAGE.get();
    }

    public static double worldzero$houseMinRoofCoverage() {
        return WORLDZERO_HOUSE_MIN_ROOF_COVERAGE.get();
    }

    public static int worldzero$houseActionMinTicks() {
        return WORLDZERO_HOUSE_ACTION_MIN_TICKS.get();
    }

    public static int worldzero$houseActionMaxTicks() {
        return WORLDZERO_HOUSE_ACTION_MAX_TICKS.get();
    }

    public static double worldzero$housePlayerStareChance() {
        return WORLDZERO_HOUSE_PLAYER_STARE_CHANCE.get();
    }

    public static double worldzero$houseFreezeVanishChance() {
        return WORLDZERO_HOUSE_FREEZE_VANISH_CHANCE.get();
    }

    public static int worldzero$houseFreezeVanishMinTicks() {
        return WORLDZERO_HOUSE_FREEZE_VANISH_MIN_TICKS.get();
    }

    public static int worldzero$houseFreezeVanishMaxTicks() {
        return WORLDZERO_HOUSE_FREEZE_VANISH_MAX_TICKS.get();
    }
}
