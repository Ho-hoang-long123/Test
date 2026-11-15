package com.example.rewind;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RewindMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Rewind");

    public static int cycleTicks = 30 * 20; // 30 giây
    public static boolean restoring = false;
    private static int ticksLeft = cycleTicks;
    private static int lastShownSec = -1;

    private static final ServerBossEvent BAR = new ServerBossEvent(
            Component.literal("Thế giới reset sau: 30s"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS);

    public record Saved(BlockState state, CompoundTag be) {}

    // dimension -> (vị trí -> trạng thái gốc ở đầu chu kỳ)
    public static final Map<ResourceKey<Level>, Map<Long, Saved>> CHANGES = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("[Rewind] Mod da duoc nap! Chu ky reset: {} giay", cycleTicks / 20);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("rewind")
                    .requires(src -> src.hasPermission(0))
                    // /rewind  -> reset ngay lap tuc
                    .executes(ctx -> {
                        rewindNow(ctx.getSource().getServer());
                        return 1;
                    })
                    // /rewind time <giay> -> doi thoi gian dem nguoc
                    .then(Commands.literal("time")
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600))
                                    .executes(ctx -> {
                                        int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                        cycleTicks = s * 20;
                                        ticksLeft = cycleTicks;
                                        lastShownSec = -1;
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("Da doi thoi gian dem nguoc thanh " + s + " giay"), true);
                                        return 1;
                                    }))));
        });
    }

    private void tick(MinecraftServer server) {
        ticksLeft--;

        // Gan thanh dem nguoc cho moi nguoi choi dang online
        var online = server.getPlayerList().getPlayers();
        for (ServerPlayer p : online) BAR.addPlayer(p);
        for (ServerPlayer p : new ArrayList<>(BAR.getPlayers())) {
            if (!online.contains(p)) BAR.removePlayer(p);
        }

        BAR.setProgress(Math.max(0f, Math.min(1f, (float) ticksLeft / cycleTicks)));
        int sec = Math.max(0, (ticksLeft + 19) / 20);
        if (sec != lastShownSec) {
            lastShownSec = sec;
            BAR.setName(Component.literal("Thế giới reset sau: " + sec + "s"));
            BAR.setColor(sec <= 5 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
            Component msg = Component.literal("§eThế giới reset sau: §c" + sec + "s");
            for (ServerPlayer p : online) p.displayClientMessage(msg, true); // action bar
        }

        if (ticksLeft <= 0) {
            rewindNow(server);
        }
    }

    public static void rewindNow(MinecraftServer server) {
        restoring = true;
        int count = 0;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                Map<Long, Saved> map = CHANGES.remove(level.dimension());
                if (map == null) continue;
                for (Map.Entry<Long, Saved> e : map.entrySet()) {
                    BlockPos pos = BlockPos.of(e.getKey());
                    Saved saved = e.getValue();
                    level.setBlock(pos, saved.state(), 18);
                    if (saved.be() != null) {
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be != null) be.loadWithComponents(saved.be(), level.registryAccess());
                    }
                    count++;
                }
            }
        } finally {
            restoring = false;
            CHANGES.clear();
        }
        // Tui do nguoi choi khong bi dung toi.
        ticksLeft = cycleTicks;
        lastShownSec = -1;
        LOGGER.info("[Rewind] Da khoi phuc {} block", count);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§aThế giới đã được khôi phục! (" + count + " block)"), false);
    }

    /** Goi tu mixin truoc khi block bi doi. */
    public static void record(ServerLevel level, BlockPos pos) {
        if (restoring) return;
        Map<Long, Saved> map = CHANGES.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long key = pos.asLong();
        if (map.containsKey(key)) return; // chi giu trang thai goc dau tien
        BlockState old = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag tag = be != null ? be.saveWithFullMetadata(level.registryAccess()) : null;
        map.put(key, new Saved(old, tag));
    }
}
