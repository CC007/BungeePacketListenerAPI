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

package de.inventivegames.packetlistener.handler;

import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.protocol.DefinedPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * © Copyright 2015 inventivetalent
 *
 * @author inventivetalent
 */
public abstract class PacketHandler {

	private static final List<PacketHandler> handlers = new ArrayList<PacketHandler>();

	public static boolean addHandler(PacketHandler handler) {
		boolean b = handlers.contains(handler);
		handlers.add(handler);
		return !b;
	}

	public static boolean removeHandler(PacketHandler handler) {
		return handlers.remove(handler);
	}

	public static void notifyHandlers(SentPacket packet) {
		for (PacketHandler handler : getHandlers()) {
			try {
				PacketOptions options = handler.getClass().getMethod("onSend", SentPacket.class).getAnnotation(PacketOptions.class);
				if (options != null) {
					if (options.forcePlayer() && options.forceServer()) { throw new IllegalArgumentException("Cannot force player and server packets at the same time!"); }
					if (options.forcePlayer()) {
						if (!packet.hasPlayer()) {
							continue;
						}
					} else if (options.forceServer()) {
						if (packet.hasPlayer()) {
							continue;
						}
					}
					if (options.ignoreRaw()) {
						if (packet.isRaw()) {
							continue;
						}
					}
				}
				handler.onSend(packet);
			} catch (Exception e) {
				System.err.println("[PacketListenerAPI] An exception occured while trying to execute 'onSend'" + (handler.plugin != null ? " in plugin " + handler.plugin.getDescription().getName() : "") + ": " + e.getMessage());
				e.printStackTrace(System.err);
			}
		}
	}

	public static void notifyHandlers(ReceivedPacket packet) {
		for (PacketHandler handler : getHandlers()) {
			try {
				PacketOptions options = handler.getClass().getMethod("onReceive", ReceivedPacket.class).getAnnotation(PacketOptions.class);
				if (options != null) {
					if (options.forcePlayer() && options.forceServer()) { throw new IllegalArgumentException("Cannot force player and server packets at the same time!"); }
					if (options.forcePlayer()) {
						if (!packet.hasPlayer()) {
							continue;
						}
					} else if (options.forceServer()) {
						if (packet.hasPlayer()) {
							continue;
						}
					}
					if (options.ignoreRaw()) {
						if (packet.isRaw()) {
							continue;
						}
					}
				}
				handler.onReceive(packet);
			} catch (Exception e) {
				System.err.println("[PacketListenerAPI] An exception occured while trying to execute 'onReceive'" + (handler.plugin != null ? " in plugin " + handler.plugin.getDescription().getName() : "") + ": " + e.getMessage());
				e.printStackTrace(System.err);
			}
		}
	}

	public static List<PacketHandler> getHandlers() {
		return new ArrayList<>(handlers);
	}

	public static List<PacketHandler> getForPlugin(Plugin plugin) {
		List<PacketHandler> handlers = new ArrayList<>();
		if (plugin == null) { return handlers; }
		for (PacketHandler h : getHandlers())
			if (plugin.equals(h.getPlugin())) {
				handlers.add(h);
			}
		return handlers;
	}

	// Sending methods
	public void sendPacket(ProxiedPlayer p, DefinedPacket packet) {
		if (p == null || packet == null) { throw new NullPointerException(); }
		p.unsafe().sendPacket(packet);
	}

	public void sendPacket(PendingConnection conn, DefinedPacket packet) {
		if (conn == null || packet == null) { throw new NullPointerException(); }
		if (conn instanceof InitialHandler) {
			InitialHandler handler = (InitialHandler) conn;
			handler.unsafe().sendPacket(packet);
		}
	}

	// //////////////////////////////////////////////////

	private Plugin plugin;

	@Deprecated
	public PacketHandler() {

	}

	public PacketHandler(Plugin plugin) {
		this.plugin = plugin;
	}

	public Plugin getPlugin() {
		return this.plugin;
	}

	public abstract void onSend(SentPacket packet);

	public abstract void onReceive(ReceivedPacket packet);

}
