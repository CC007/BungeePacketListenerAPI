/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.inventivegames.packetlistener.bungee;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.protocol.BadPacketException;
import net.md_5.bungee.protocol.DefinedPacket;
import net.md_5.bungee.protocol.MinecraftEncoder;
import net.md_5.bungee.protocol.Protocol;

/**
 *
 * @author Rik Schaaf aka CC007 (http://coolcat007.nl/)
 */
public class PacketEncoder {

    private MinecraftEncoder encoder;
    private Method encode;

    private boolean server;
    private Protocol protocol;
    private int protocolVersion;

    private boolean useBungeeEncoder = false;

    public PacketEncoder(Channel ch) {
        encoder = ch.pipeline().get(MinecraftEncoder.class);
        try {
            Field serverField = encoder.getClass().getDeclaredField("server");
            Field protocolField = encoder.getClass().getDeclaredField("protocol");
            Field protocolVersionField = encoder.getClass().getDeclaredField("protocolVersion");
            AccessUtil.setAccessible(serverField);
            AccessUtil.setAccessible(protocolField);
            AccessUtil.setAccessible(protocolVersionField);
            server = (boolean) serverField.get(encoder);
            protocol = (Protocol) protocolField.get(encoder);
            protocolVersion = (int) protocolVersionField.get(encoder);

            encode = encoder.getClass().getDeclaredMethod("encode", ChannelHandlerContext.class, DefinedPacket.class, ByteBuf.class);
            AccessUtil.setAccessible(encode);
        } catch (NoSuchMethodException | SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException ex) {
            Logger.getLogger(PacketDecoder.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public ByteBuf encode(ChannelHandlerContext ctx, DefinedPacket pckt, int id) {
        ByteBuf out = Unpooled.buffer();
        if (useBungeeEncoder) {
            //broken because TO_SERVER and TO_CLIENT are inverted in bungee
            try {
                encode.invoke(encoder, ctx, pckt, out);
                return out;
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                Logger.getLogger(PacketDecoder.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvocationTargetException ex) {
                if (!(ex.getCause() instanceof BadPacketException)) {
                    Logger.getLogger(PacketDecoder.class.getName()).log(Level.SEVERE, "Error in " + pckt.getClass().getName(), ex);
                }
            }
        } else {
            Protocol.DirectionData prot = (server) ? protocol.TO_CLIENT : protocol.TO_SERVER;
            DefinedPacket.writeVarInt(id, out);
            pckt.write(out, prot.getDirection(), protocolVersion);
        }
        return out;
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
