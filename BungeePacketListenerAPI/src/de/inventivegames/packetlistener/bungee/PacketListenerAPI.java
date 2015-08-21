/*
 * Copyright 2015 Marvin Schäfer (inventivetalent). All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */

package de.inventivegames.packetlistener.bungee;

import de.inventivegames.packetlistener.handler.PacketHandler;
import de.inventivegames.packetlistener.handler.ReceivedPacket;
import de.inventivegames.packetlistener.handler.SentPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.PacketWrapper;
import org.mcstats.MetricsLite;

/**
 * © Copyright 2015 inventivetalent
 *
 * @author inventivetalent
 */
public class PacketListenerAPI extends Plugin implements Listener {

	@Override
	public void onEnable() {
		ProxyServer.getInstance().getPluginManager().registerListener(this, this);

		ProxyServer.getInstance().getScheduler().runAsync(this, new Runnable() {

			@Override
			public void run() {
				try {
					MetricsLite metrics = new MetricsLite(PacketListenerAPI.this);
					if (metrics.start()) {
						System.out.println("[BungeePacketListenerAPI] Metrics started.");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	@EventHandler
	public void onConnect(PlayerHandshakeEvent e) {
		addConnectionChannel(e.getConnection());
	}

	@EventHandler
	public void onJoin(PostLoginEvent e) {
		addChannel(e.getPlayer());

	}

	@EventHandler
	public void onQuit(PlayerDisconnectEvent e) {
		removeChannel(e.getPlayer());
	}

	public static ChannelWrapper getChannel(ProxiedPlayer player) throws Exception {
		ChannelWrapper channel = (ChannelWrapper) AccessUtil.setAccessible(UserConnection.class.getDeclaredField("ch")).get((UserConnection) player);
		return channel;
	}

	public static ChannelWrapper getChannel(PendingConnection conn) throws Exception {
		ChannelWrapper channel = null;
		if (conn instanceof InitialHandler) {
			channel = (ChannelWrapper) AccessUtil.setAccessible(InitialHandler.class.getDeclaredField("ch")).get((InitialHandler) conn);
		}
		return channel;
	}

	void addConnectionChannel(final PendingConnection connection) {
		try {
			if (connection instanceof InitialHandler) {
				ChannelWrapper channel = getChannel(connection);
				channel.getHandle().pipeline().addBefore("inbound-boss", "packet_listener_connection", new ChannelDuplexHandler() {

					@Override
					public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
						Cancellable cancellable = new Cancellable();
						Object pckt = msg;

						if (ByteBuf.class.isAssignableFrom(msg.getClass())) {
							ByteBuf copy = ((ByteBuf) pckt).copy();
							int packetId = DefinedPacket.readVarInt(copy);
							if (packetId != 0) {
								onPacketSend(connection, packetId, cancellable);
							}
						}

						if (DefinedPacket.class.isAssignableFrom(msg.getClass())) {
							pckt = (DefinedPacket) onPacketSend(connection, (DefinedPacket) msg, cancellable);
						}
						if (PacketWrapper.class.isAssignableFrom(msg.getClass())) {
							pckt = (PacketWrapper) onPacketSend(connection, msg, cancellable);
						}
						if (cancellable.isCancelled()) { return; }
						super.write(ctx, pckt, promise);
					}

					@Override
					public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
						Cancellable cancellable = new Cancellable();
						Object pckt = msg;

						if (ByteBuf.class.isAssignableFrom(msg.getClass())) {
							ByteBuf copy = ((ByteBuf) pckt).copy();
							int packetId = DefinedPacket.readVarInt(copy);
							if (packetId != 0) {
								onPacketReceive(connection, packetId, cancellable);
							}
						}

						if (DefinedPacket.class.isAssignableFrom(msg.getClass())) {
							pckt = (DefinedPacket) onPacketReceive(connection, (DefinedPacket) msg, cancellable);
						}
						if (PacketWrapper.class.isAssignableFrom(msg.getClass())) {
							pckt = (PacketWrapper) onPacketReceive(connection, msg, cancellable);
						}
						if (cancellable.isCancelled()) { return; }
						super.channelRead(ctx, pckt);
					}

				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void addChannel(final ProxiedPlayer player) {
		try {
			ChannelWrapper channel = getChannel(player);
			if (channel.getHandle().pipeline().get("packet_listener_connection") != null) {
				channel.getHandle().pipeline().remove("packet_listener_connection");// Remove the connection listener
			}
			channel.getHandle().pipeline().addBefore("inbound-boss", "packet_listener_player", new ChannelDuplexHandler() {

				@Override
				public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
					Cancellable cancellable = new Cancellable();
					Object pckt = msg;

					if (ByteBuf.class.isAssignableFrom(msg.getClass())) {
						ByteBuf copy = ((ByteBuf) pckt).copy();
						int packetId = DefinedPacket.readVarInt(copy);
						if (packetId != 0) {
							onPacketSend(player, packetId, cancellable);
						}
					}

					if (DefinedPacket.class.isAssignableFrom(msg.getClass())) {
						pckt = (DefinedPacket) onPacketSend(player, (DefinedPacket) msg, cancellable);
					}
					if (PacketWrapper.class.isAssignableFrom(msg.getClass())) {
						pckt = (PacketWrapper) onPacketSend(player, msg, cancellable);
					}
					if (cancellable.isCancelled()) { return; }
					super.write(ctx, pckt, promise);
				}

				@Override
				public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
					Cancellable cancellable = new Cancellable();
					Object pckt = msg;

					if (ByteBuf.class.isAssignableFrom(msg.getClass())) {
						ByteBuf copy = ((ByteBuf) pckt).copy();
						int packetId = DefinedPacket.readVarInt(copy);
						if (packetId != 0) {
							onPacketReceive(player, packetId, cancellable);
						}
					}

					if (DefinedPacket.class.isAssignableFrom(msg.getClass())) {
						pckt = (DefinedPacket) onPacketReceive(player, (DefinedPacket) msg, cancellable);
					}
					if (PacketWrapper.class.isAssignableFrom(msg.getClass())) {
						pckt = (PacketWrapper) onPacketReceive(player, msg, cancellable);
					}
					if (cancellable.isCancelled()) { return; }
					super.channelRead(ctx, pckt);
				}

			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void removeChannel(ProxiedPlayer player) {
		try {
			ChannelWrapper channel = getChannel(player);
			channel.getHandle().pipeline().remove("packet_listener_player");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Object onPacketReceive(Object p, Object packet, Cancellable cancellable) {
		if (packet == null) { return packet; }
		ReceivedPacket pckt = null;
		if (p instanceof ProxiedPlayer) {
			pckt = new ReceivedPacket(packet, cancellable, (ProxiedPlayer) p);
		}
		if (p instanceof PendingConnection) {
			pckt = new ReceivedPacket(packet, cancellable, (PendingConnection) p);
		}
		if (pckt == null) {
			return packet;
		}
		PacketHandler.notifyHandlers(pckt);
		return pckt.getSourcePacket();
	}

	public Object onPacketSend(Object p, Object packet, Cancellable cancellable) {
		if (packet == null) { return packet; }
		SentPacket pckt = null;
		if (p instanceof ProxiedPlayer) {
			pckt = new SentPacket(packet, cancellable, (ProxiedPlayer) p);
		}
		if (p instanceof PendingConnection) {
			pckt = new SentPacket(packet, cancellable, (PendingConnection) p);
		}
		if (pckt == null) { return packet; }
		PacketHandler.notifyHandlers(pckt);
		return pckt.getSourcePacket();
	}

}
