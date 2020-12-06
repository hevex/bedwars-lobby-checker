package com.hev.bwhelper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import com.hev.bwhelper.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FastScanner {

    // minecraft instance
    private static final Minecraft mc = Minecraft.getMinecraft();

    // called when command is ran
    public static void checkLobby() {
        // check for api key
        if (BWHelper.api_key.isEmpty()) {
            Utils.sendMessage("&eError, your API key is invalid.");
            return;
        }
        // check if party members are synced (to ignore when checking lobby)
        if (BWHelper.party_members.isEmpty()) {
            Utils.sendMessage("&eAlert! Party list is not synced!");
        }
        // list of players
        List<NetworkPlayerInfo> playerList = new ArrayList<>(mc.getNetHandler().getPlayerInfoMap());
        // remove client player from list (getting player info by uuid is 4x faster than by name)
        playerList.remove(mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()));
        // iterate through the list
        for (NetworkPlayerInfo networkPlayer : playerList) {
            GameProfile profile = networkPlayer.getGameProfile();
            // get players name and uuid
            String name = profile.getName(), uuid = profile.getId().toString();
            // skip player is in same party, checking if list is empty before contain check is more efficient
            if (!BWHelper.party_members.isEmpty() && BWHelper.party_members.contains(name)) {
                continue;
            }
            // skip if checked player is a watchdog bot
            if (isBot(name)) {
                continue;
            }
            // multithreading (ExecutorService executor = Executors.newCachedThreadPool())
            BWHelper.getExecutor().execute(() -> {
                int[] stats = getBedwarsStats(uuid);
                if (stats == null) {
                    Utils.sendMessage("&e[&cERROR&e] &3" + name);
                } else if (stats[0] == -1) {
                    // player is nicked
                    Utils.sendMessage("&e[???] &3" + name + " &eis nicked!");
                } else {
                    double FKDR = stats[2] == 0 ? stats[1] : round2d(stats[1] / (double) stats[2]);
                    double WLR = stats[4] == 0 ? stats[3]  : round2d(stats[3] / (double) stats[4]);
                    Utils.sendMessage("&e[" + stats[0] + "] &3" + name + " &e- &a" + FKDR + " &eFKDR - &a" + WLR + " &eWLR - &a" + stats[5] + " &eWS");
                }
            });
        }
    }

    private static int[] getBedwarsStats(String uuid) {
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
        JsonObject profile = getStringAsJson(connection).getAsJsonObject("player"),
                   bw = profile.getAsJsonObject("stats").getAsJsonObject("Bedwars"),
                   ach = profile.getAsJsonObject("achievements");
        // get players bedwars stats from json object
        JsonElement element = ach.get("bedwars_level");
        if (element == null) {
            // player has never played bedwars before, returning default stats
            return stats;
        }
        stats[0] = element.getAsInt();
        stats[1] = bw.get("final_kills_bedwars").getAsInt();
        stats[2] = bw.get("final_deaths_bedwars").getAsInt();
        stats[3] = bw.get("wins_bedwars").getAsInt();
        stats[4] = bw.get("losses_bedwars").getAsInt();
        stats[5] = bw.get("winstreak").getAsInt();
        return stats;
    }

    // return a jsonobject of json text
    private static JsonObject getStringAsJson(String text) {
        return new JsonParser().parse(text).getAsJsonObject();
    }

    // open a new connection to given link
    private static String newConnection(String link) {
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
    private static String getContents(HttpURLConnection con) {
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

    // filter watchdog bots in the lobby
    private static boolean isBot(String name) {
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
                // npc does not have upper case letter
                return false;
            }
        }
        if (num >= 2 && let >= 2) {
            // player npc (bedwars merchant, skyblock npc, etc.)
            return true;
        }
        return false;
    }

    private static double round2d(double num) {
        return Math.round(num * 10d) / 10d;
    }

}
