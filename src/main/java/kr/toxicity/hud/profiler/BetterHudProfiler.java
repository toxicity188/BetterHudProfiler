package kr.toxicity.hud.profiler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class BetterHudProfiler extends JavaPlugin {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,###");

    private final Map<UUID, Handler> handlerMap = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void join(PlayerJoinEvent event) {
                var player = event.getPlayer();
                handlerMap.computeIfAbsent(player.getUniqueId(), uuid -> new Handler(player));
            }
            @EventHandler
            public void quit(PlayerQuitEvent event) {
                var handler = handlerMap.remove(event.getPlayer().getUniqueId());
                if (handler != null) handler.remove();
            }
        }, this);
    }

    private class Handler extends ChannelDuplexHandler {
        private final ServerGamePacketListenerImpl packetListener;
        private final Queue<Supplier<Integer>> byteSupplier = new ConcurrentLinkedQueue<>();
        private final Queue<Integer> byteQueue = new ConcurrentLinkedQueue<>();
        private final ScheduledTask actionbar;

        private Handler(@NotNull Player player) {
            actionbar = Bukkit.getAsyncScheduler().runAtFixedRate(BetterHudProfiler.this, task -> {
                Supplier<Integer> getter;
                while ((getter = byteSupplier.poll()) != null) {
                    byteQueue.add(getter.get());
                    if (byteQueue.size() >= 600) byteQueue.poll();
                }
                if (!byteQueue.isEmpty()) player.sendActionBar(Component.text("Current traffic usage: " + FORMAT.format(byteQueue.stream()
                        .mapToInt(i -> i)
                        .sum() / byteQueue.size()) + " byte"));
            }, 20, 20, TimeUnit.MILLISECONDS);
            packetListener = ((CraftPlayer) player).getHandle().connection;
            var pipeLine = packetListener.connection.channel.pipeline();
            for (Map.Entry<String, ChannelHandler> entry : pipeLine) {
                if (entry.getValue() instanceof Connection) {
                    pipeLine.addBefore(entry.getKey(), "profiler_handler", this);
                }
            }
        }

        private void remove() {
            actionbar.cancel();
            var channel = packetListener.connection.channel;
            channel.eventLoop().submit(() -> channel.pipeline().remove("profiler_handler"));
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ClientboundBossEventPacket packet) {
                byteSupplier.add(() -> {
                    var byteSize = new FriendlyByteBuf(Unpooled.buffer());
                    packet.write(byteSize);
                    return byteSize.readableBytes();
                });
            }
            super.write(ctx, msg, promise);
        }
    }
}
