// AutoLoopHandler.java

package com.keycommand2.keycommand2mod;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;

public class AutoLoopHandler {

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        KeyCommandMod.LOGGER.info("KeyCommandMod: Connected to server, preparing to auto-loop...");

        // 启动新线程，反复检测直到player加载完成
        new Thread(() -> {
            int waited = 0;
            while (Minecraft.getMinecraft().player == null && waited < 15) {
                KeyCommandMod.LOGGER.info("AutoLoopHandler: player==null, waited=" + waited + "秒");
                try { Thread.sleep(1000); } catch (Exception ignored) {}
                waited++;
            }
            if(Minecraft.getMinecraft().player == null) {
                KeyCommandMod.LOGGER.error("等待超时，player仍然为null，自动循环取消");
                return;
            }
            Minecraft.getMinecraft().addScheduledTask(() -> {
                KeyCommandMod.LOGGER.info("AutoLoopHandler: player已加载，执行 tryAutoStartLoop");
                KeyCommandMod.tryAutoStartLoop();
            });
        }).start();
    }
}