package com.hev.bwhelper.listeners;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import com.hev.bwhelper.FastScanner;
import com.hev.bwhelper.utils.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ScannerListener {

    // passed scanner instance
    FastScanner scanner;
    // how many seconds the hud is displayed for, value has already been fixed
    int display_time;
    // boolean to move the hud to the left or right
    boolean reverse;
    // determine how far the animation has to be displaced
    int lineType;
    // boolean to render esp
    boolean renderESP;

    // timer instance
    Timer timer;
    // animator instance to displace hud
    RenderUtils.Animator animateHUD;
    // threat list carried over from the scanner
    List<String> threatList;

    public ScannerListener(FastScanner scanner, int display_time) {
        this.scanner = scanner;
        this.display_time = display_time;
        reverse = false;
        lineType = 0;
        renderESP = true;
    }

    public void init() {
        // begin the animator task (hud slide-out/in)
        (animateHUD = new RenderUtils.Animator(500)).start();
        // register this class for events
        FMLCommonHandler.instance().bus().register(this);
        // begin a timer for when to close the hud
        (timer = new Timer()).schedule(new TimerTask() {
            int passed = 0;
            public void run() {
                if (passed == display_time - 1) {
                    // begin to slide-in the hud
                    reverse = true;
                    animateHUD.start();
                    // stop rendering
                    renderESP = false;
                } else if (passed == display_time) {
                    // end scanner listener
                    finish();
                    return;
                }
                passed++;
            }
        }, 0, 1000);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (!Utils.nullCheck() || Utils.mc.gameSettings.showDebugInfo || Utils.mc.currentScreen != null) return;
        // last value for animator
        int end = 100;
        // account for the different string length
        if (reverse && lineType != 0) {
            // no threats message
            if (lineType == 1) end = 200;
                // threats found message
            else if (lineType == 2) end = 150;
        }
        // get the animator value (from 0 to end integer)
        int val = animateHUD.getValueInt(0, end);
        // coordinates for the hud (5, 55)
        int x = reverse ? 5 - val : val - 95, y = 55;
        String first_line;
        int color = -1;
        if (threatList == null) {
            first_line = "Loading...";
            RenderUtils.displayRect(first_line, x, y, 5, Utils.mc.fontRendererObj);
        } else if (threatList.isEmpty()) {
            first_line = "There are no threats in this lobby.";
            lineType = 1;
            RenderUtils.displayRect(first_line, x, y, 5, Utils.mc.fontRendererObj);
            color = Color.green.getRGB();
        } else {
            first_line = threatList.size() == 1 ? "A threat was found!" : threatList.size() + " threats were found!";
            lineType = 2;
            color = Color.red.getRGB();
            RenderUtils.displayListRect(first_line, threatList, x, y, 5, Utils.mc.fontRendererObj);
            int y_displacement = y;
            // create a duplicate arraylist to avoid CM exception
            List<String> tempThreatList = new ArrayList<>(threatList);
            for (String name : tempThreatList) {
                y_displacement += Utils.mc.fontRendererObj.FONT_HEIGHT + 5;
                Utils.mc.fontRendererObj.drawStringWithShadow(name, x, y_displacement, Color.ORANGE.getRGB());
            }
        }
        Utils.mc.fontRendererObj.drawStringWithShadow(first_line, x, y, color);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!renderESP || threatList == null || threatList.isEmpty() || !Utils.nullCheck()) return;
        for (EntityPlayer player : Utils.mc.theWorld.playerEntities) {
            if (player == Utils.mc.thePlayer) continue;
            if (threatList.contains(player.getName())) {
                // render orange circle under scary players
                RenderUtils.drawHollowShadow(player, Color.orange.getRGB(), e.partialTicks);
            }
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) { if (e.entity == Utils.mc.thePlayer) finish(); }

    public void finish() {
        // terminate the timer task
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
        // unregister class events
        FMLCommonHandler.instance().bus().unregister(this);
        // disable the scanner
        scanner.setRunning(false);
    }

    public void registerList(List<String> threatList) { this.threatList = threatList; }

}
