package com.hev.bwhelper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.NetworkPlayerInfo;
import com.hev.bwhelper.listeners.ScannerListener;
import com.hev.bwhelper.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FastScanner {

    // is the scanner running?
    boolean running = false;

    // how many seconds the hud is displayed for, must be above 2
    int display_time;
    // kinda scary stats (inclusive)
    int max_fkdr, max_wlr, max_ws, max_stars;

    // lobby player count tracker
    int tracker;
    // list of threats in the lobby
    List<String> threatList;
    // listener for this scanner
    ScannerListener listener;

    public FastScanner(int display_time, int max_fkdr, int max_wlr, int max_ws, int max_stars) {
        if (display_time < 3) {
            display_time = 10;
        }
        this.display_time = display_time;
        this.max_fkdr = max_fkdr;
        this.max_wlr = max_wlr;
        this.max_ws = max_ws;
        this.max_stars = max_stars;
        tracker = 0;
        threatList = new ArrayList<>();
    }

    // called when command is ran
    public void inspectLobby() {
        // check for api key
        if (BWHelper.api_key.isEmpty()) {
            Utils.sendMessage("&eError, your API key is invalid.");
            return;
        }
        // scanner is now running
        setRunning(true);
        // check if party members are synced (to ignore when checking lobby)
        if (BWHelper.party_members.isEmpty()) {
            Utils.sendMessage("&eAlert! Party list is not synced!");
        }
        // list of players
        List<NetworkPlayerInfo> playerList = new ArrayList<>(Utils.mc.getNetHandler().getPlayerInfoMap());
        // remove duplicates network players from the list (minecraft bug)
        Utils.removeDuplicates((ArrayList) playerList);
        // remove client player from list (getting player info by uuid is 4x faster than by name)
        playerList.remove(Utils.mc.getNetHandler().getPlayerInfo(Utils.mc.thePlayer.getUniqueID()));
        // initialize render class
        (listener = new ScannerListener(this, display_time)).init();
        // iterate through the list
        for (NetworkPlayerInfo networkPlayer : playerList) {
            GameProfile profile = networkPlayer.getGameProfile();
            // get players name and uuid
            String name = profile.getName(), uuid = profile.getId().toString();
            // skip player is in same party, checking if list is empty before contain check is more efficient
            if (!BWHelper.party_members.isEmpty() && BWHelper.party_members.contains(name)) {
                continue;
            }
            // skip if checked player is a npc
            if (isBot(name)) {
                continue;
            }
            // multithreading (ExecutorService executor = Executors.newCachedThreadPool())
            BWHelper.getExecutor().execute(() -> {
                // increment value
                tracker++;
                // get player stats from uuid
                int[] stats = getBedwarsStats(uuid);
                if (stats == null) {
                    Utils.sendMessage("&e[&cERROR&e] &3" + name);
                } else if (stats[0] == -1) {
                    // player is nicked
                    Utils.sendMessage("&e[???] &3" + name + " &eis nicked!");
                } else {
                    double FKDR = stats[2] == 0 ? stats[1] : round2d(stats[1] / (double) stats[2]);
                    double WLR = stats[4] == 0 ? stats[3]  : round2d(stats[3] / (double) stats[4]);
                    // check if player exceeds any stat threshold to mark as threat
                    if (FKDR >= max_fkdr || WLR >= max_wlr || stats[5] >= max_ws || stats[0] >= max_stars) {
                        threatList.add(name);
                    }
                    Utils.sendMessage("&e[" + stats[0] + "] &3" + name + " &e- &a" + FKDR + " &eFKDR - &a" + WLR + " &eWLR - &a" + stats[5] + " &eWS");
                }
                // decrement value since stats have been retrieved
                tracker--;
                // analysis of all players has finished
                if (tracker == 0) {
                    // update render listener
                    listener.registerList(threatList);
                }
            });
        }
    }

    private int[] getBedwarsStats(String uuid) {
        // Stars, FK, FD, Wins, Losses, Winstreak
        final int[] stats = new int[6];
        // open connection to api
        String connection = newConnection("https://api.hypixel.net/player?key=" + BWHelper.api_key + "&uuid=" + uuid);
        // error getting contents of link
        if (connection.isEmpty()) {
            return null;
        }
        // faster than contains
        if (connection.equals("{\"success\":true,\"player\":null}")) {
            // player is nicked
            stats[0] = -1;
            return stats;
        }
        // parse the text from the api
        JsonObject profile, bw, ach;
        try {
            profile = getStringAsJson(connection).getAsJsonObject("player");
            bw = profile.getAsJsonObject("stats").getAsJsonObject("Bedwars");
            ach = profile.getAsJsonObject("achievements");
        } catch (NullPointerException er) {
            // never played bedwars or joined lobby
            return stats;
        }
        // get stats from parsed objects (check for null)
        stats[0] = getValue(ach, "bedwars_level");
        stats[1] = getValue(bw, "final_kills_bedwars");
        stats[2] = getValue(bw, "final_deaths_bedwars");
        stats[3] = getValue(bw, "wins_bedwars");
        stats[4] = getValue(bw, "losses_bedwars");
        stats[5] = getValue(bw, "winstreak");
        return stats;
    }

    // gson likes being a bitch... here's the only way to prevent it
    private int getValue(JsonObject type, String member) {
        try {
            return type.get(member).getAsInt();
        } catch (NullPointerException er) {
            return 0;
        }
    }

    // return a jsonobject of json text
    private JsonObject getStringAsJson(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    // open a new connection to given link
    private String newConnection(String link) {
        URL url;
        String result = "";
        HttpURLConnection con = null;
        try {
            url = new URL(link);
            con = (HttpURLConnection) url.openConnection();
            result = getContents(con);
        } catch (IOException e) { }
        finally {
            if (con != null) con.disconnect();
        }
        return result;
    }

    // get contents from an open connection
    private String getContents(HttpURLConnection con) {
        if (con != null) {
            // since BufferedReader is defined within try catch, close is called regardless of completion
            try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String input;
                StringBuilder sb = new StringBuilder();
                while ((input = br.readLine()) != null) {
                    sb.append(input);
                }
                return sb.toString();
            } catch (IOException e) { }
        }
        return "";
    }

    // filter npcs in the lobby
    private boolean isBot(String name) {
        // all bot names are 10 characters in length
        if (name.length() != 10) return false;
        int num = 0, let = 0;
        for (char c : name.toCharArray()) {
            if (Character.isLetter(c)) {
                if (Character.isUpperCase(c)) {
                    // npc does not have upper case letter
                    return false;
                }
                let++;
            } else if (Character.isDigit(c)) {
                num++;
            } else {
                // npc name is alphanumerical
                return false;
            }
        }
        if (num >= 2 && let >= 2) {
            return true;
        }
        return false;
    }

    private double round2d(double num) {
        return Math.round(num * 10d) / 10d;
    }

    // getter and setters
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public ScannerListener getListener() { return listener; }

}
