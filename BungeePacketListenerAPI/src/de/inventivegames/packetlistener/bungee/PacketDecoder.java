/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.inventivegames.packetlistener.bungee;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.protocol.BadPacketException;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.MinecraftDecoder;
import net.md_5.bungee.protocol.PacketWrapper;
import net.md_5.bungee.protocol.Protocol;

/**
 *
 * @author Rik Schaaf aka CC007 (http://coolcat007.nl/)
 */
public class PacketDecoder {

    private MinecraftDecoder decoder;
    private Method decode;

    private boolean server;
    private Protocol protocol;
    private int protocolVersion;

    private boolean useBungeeDecoder = false;

    public PacketDecoder(Channel ch) {
        decoder = ch.pipeline().get(MinecraftDecoder.class);
        try {
            Field serverField = decoder.getClass().getDeclaredField("server");
            Field protocolField = decoder.getClass().getDeclaredField("protocol");
            Field protocolVersionField = decoder.getClass().getDeclaredField("protocolVersion");
            AccessUtil.setAccessible(serverField);
            AccessUtil.setAccessible(protocolField);
            AccessUtil.setAccessible(protocolVersionField);
            server = (boolean) serverField.get(decoder);
            protocol = (Protocol) protocolField.get(decoder);
            protocolVersion = (int) protocolVersionField.get(decoder);

            decode = decoder.getClass().getDeclaredMethod("decode", ChannelHandlerContext.class, ByteBuf.class, List.class);
            AccessUtil.setAccessible(decode);
        } catch (NoSuchMethodException | SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException ex) {
            Logger.getLogger(PacketDecoder.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Object decode(ChannelHandlerContext ctx, ByteBuf pckt) {
        if (useBungeeDecoder) {
            //broken because TO_SERVER and TO_CLIENT are inverted in bungee
            List<Object> out = new ArrayList<>();
            try {
                decode.invoke(decoder, ctx, pckt, out);
                return out.get(0);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(PacketDecoder.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvocationTargetException ex) {
                if (!(ex.getCause() instanceof BadPacketException)) {
                    
                    Logger.getLogger(PacketDecoder.class.getName()).log(Level.SEVERE, "Error in " + DefinedPacket.readVarInt((ByteBuf) pckt), ex);
                }
            }
        } else {
            Object result = null;
            Protocol.DirectionData prot = (!server) ? protocol.TO_SERVER : protocol.TO_CLIENT;
            ByteBuf slice = pckt.copy(); // Can't slice this one due to EntityMap :(

            try {
                int packetId = DefinedPacket.readVarInt(pckt);
                DefinedPacket packet = prot.createPacket(packetId, protocolVersion);
                if (packet != null) {
                    packet.read(pckt, prot.getDirection(), protocolVersion);
                    if (pckt.isReadable()) {
                        throw new BadPacketException("Did not read all bytes from packet " + packet.getClass() + " " + packetId + " Protocol " + protocol + " Direction " + prot);
                    }
                } else {
                    pckt.skipBytes(pckt.readableBytes());
                }
                result = new PacketWrapper(packet, slice);
                slice = null;
            } finally {
                if (slice != null) {
                    slice.release();
                }
            }
            return result;
        }
        return null;
    }

    public boolean isServer() {
        return server;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

}
