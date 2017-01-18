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

import de.inventivegames.packetlistener.bungee.Cancellable;
import io.netty.buffer.ByteBuf;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.PacketWrapper;

import java.lang.reflect.Field;

/**
 * Base class for sent or received packets
 *
 * @see SentPacket
 * @see ReceivedPacket
 */
public abstract class Packet {

    private ProxiedPlayer player;
    private PendingConnection connection;

    private int packetId = -1;
    private Object packet;
    private Cancellable cancel;
    private ByteBuf raw;

    public Packet(Object packet, ByteBuf raw, Cancellable cancel, ProxiedPlayer player) {
        this.player = player;
        this.raw = raw;
        if (packet instanceof Integer) {
            this.packetId = (Integer) packet;
        } else {
            this.packet = packet;
        }
        if (packet instanceof PacketWrapper) {
            this.packetId = DefinedPacket.readVarInt(((PacketWrapper) packet).buf.copy());
        }
        this.cancel = cancel;
    }

    public Packet(Object packet, ByteBuf raw, Cancellable cancel, PendingConnection connection) {
        this.connection = connection;
        this.raw = raw;
        if (packet instanceof Integer) {
            this.packetId = (Integer) packet;
        } else {
            this.packet = packet;
        }
        if (packet instanceof PacketWrapper) {
            this.packetId = DefinedPacket.readVarInt(((PacketWrapper) packet).buf.copy());
        }
        this.cancel = cancel;
    }

    // next two constructors provided for backwards compatibility of the api for 1.3.0
    public Packet(Object packet, Cancellable cancel, ProxiedPlayer player) {
        this(packet, null, cancel, player);
    }

    public Packet(Object packet, Cancellable cancel, PendingConnection connection) {
        this(packet, null, cancel, connection);
    }

    /**
     * Modify a value of the packet
     *
     * @param field Name of the field to modify
     * @param value Value to be assigned to the field
     */
    public void setPacketValue(String field, Object value) {
        try {
            Field f = this.packet.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(this.packet, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get a value of the packet
     *
     * @param field Name of the field
     * @return current value of the field
     */
    public Object getPacketValue(String field) {
        Object value = null;
        try {
            Field f = this.packet.getClass().getDeclaredField(field);
            f.setAccessible(true);
            value = f.get(this.packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    /**
     * @param b if set to <code>true</code> the packet will be cancelled
     */
    public void setCancelled(boolean b) {
        this.cancel.setCancelled(b);
    }

    /**
     * @return <code>true</code> if the packet has been cancelled
     */
    public boolean isCancelled() {
        return this.cancel.isCancelled();
    }

    /**
     * @return The receiving or sending player of the packet
     * @see #hasPlayer()
     */
    public ProxiedPlayer getPlayer() {
        return this.player;
    }

    public PendingConnection getConection() {
        return this.connection;
    }

    /**
     * @return <code>true</code> if the packet has a player
     */
    public boolean hasPlayer() {
        return this.player != null;
    }

    /**
     * @return The name of the receiving or sending player
     * @see #hasPlayer()
     * @see #getPlayer()
     */
    public String getPlayername() {
        if (!this.hasPlayer()) {
            return null;
        }
        return this.player.getName();
    }

    /**
     * Change the packet that is sent
     *
     * @param packet new packet
     */
    public void setPacket(DefinedPacket packet) {
        if (this.packet instanceof DefinedPacket) {
            this.packet = packet;
        }
        if (this.packet instanceof PacketWrapper) {
            this.packet = new PacketWrapper(packet, ((PacketWrapper) this.packet).buf);
        }
    }

    /**
     * @return the sent or received packet as a DefinedPacket
     */
    public DefinedPacket getPacket() {
        if (packet instanceof DefinedPacket) {
            return (DefinedPacket) packet;
        }
        if (packet instanceof PacketWrapper) {
            return ((PacketWrapper) packet).packet;
        }
        return null;
    }

    /**
     * @return the sent or received packet as an Object
     */
    public Object getSourcePacket() {
        return this.packet;
    }

    /**
     * Change the packet
     *
     * @param packet the sent or received packet as an Object
     */
    public void setSourcePacket(Object packet) {
        this.packet = packet;
    }

    /**
     * @return the class name of the sent or received packet
     */
    public String getPacketName() {
        return this.getPacket() == null ? String.format("0x%02X", packetId) : this.getPacket().getClass().getSimpleName();
    }

    /**
     * @return the packet id of the sent or received packet
     */
    public int getPacketId() {
        return packetId;
    }

    /**
     * @return if the sent or received packet is a raw packet
     */
    public boolean isRaw() {
        return raw != null;
    }

    /**
     * @return the raw data of sent or received packet
     */
    public ByteBuf getRaw() {
        return raw;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.cancel == null ? 0 : this.cancel.hashCode());
        result = prime * result + (this.packet == null ? 0 : this.packet.hashCode());
        result = prime * result + (this.player == null ? 0 : this.player.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        Packet other = (Packet) obj;
        if (this.cancel == null) {
            if (other.cancel != null) {
                return false;
            }
        } else if (!this.cancel.equals(other.cancel)) {
            return false;
        }
        if (this.packet == null) {
            if (other.packet != null) {
                return false;
            }
        } else if (!this.packet.equals(other.packet)) {
            return false;
        }
        if (this.player == null) {
            if (other.player != null) {
                return false;
            }
        } else if (!this.player.equals(other.player)) {
            return false;
        }
        if (this.raw == null) {
            if (other.raw != null) {
                return false;
            }
        } else if (!this.raw.equals(other.raw)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Packet{ " + (this.getClass().equals(SentPacket.class) ? "[> OUT >]" : "[< IN <]") + " " + this.getPacketName() + " " + (this.hasPlayer() ? this.getPlayername() : "#server#") + " }";
    }
}
