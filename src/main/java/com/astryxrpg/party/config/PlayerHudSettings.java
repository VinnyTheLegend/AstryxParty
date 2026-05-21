package com.astryxrpg.party.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class PlayerHudSettings {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private static final String CONFIG_PATH = "mods/AstryxParty/player_hud_settings.json";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Map<UUID, PlayerHudSettings.HudSettings> playerSettings = new ConcurrentHashMap<>();
   private static boolean loaded = false;

   public static void load() {
      if (!loaded) {
         Path path = Paths.get("mods/AstryxParty/player_hud_settings.json");
         if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
               Type type = (new TypeToken<Map<UUID, PlayerHudSettings.HudSettings>>() {}).getType();
               Map<UUID, PlayerHudSettings.HudSettings> loadedSettings = (Map<UUID, PlayerHudSettings.HudSettings>)GSON.fromJson(reader, type);
               if (loadedSettings != null) {
                  loadedSettings.values().forEach(PlayerHudSettings.HudSettings::validate);
                  playerSettings.putAll(loadedSettings);
                  ((Api)LOGGER.atInfo()).log("Loaded HUD settings for %d players", playerSettings.size());
               }
            } catch (Exception e) {
               ((Api)((Api)LOGGER.atWarning()).withCause(e)).log("Failed to load player HUD settings");
            }
         }

         loaded = true;
      }
   }

   public static void save() {
      try {
         Path path = Paths.get("mods/AstryxParty/player_hud_settings.json");
         Files.createDirectories(path.getParent());

         try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(playerSettings, writer);
         }

         ((Api)LOGGER.atInfo()).log("Saved HUD settings for %d players", playerSettings.size());
      } catch (IOException e) {
         ((Api)((Api)LOGGER.atWarning()).withCause(e)).log("Failed to save player HUD settings");
      }
   }

   @Nonnull
   public static PlayerHudSettings.HudSettings get(@Nonnull UUID playerUuid) {
      return playerSettings.computeIfAbsent(playerUuid, k -> new PlayerHudSettings.HudSettings());
   }

   public static void update(@Nonnull UUID playerUuid, @Nonnull PlayerHudSettings.HudSettings settings) {
      settings.validate();
      playerSettings.put(playerUuid, settings);
      save();
   }

   public static void setShowHud(@Nonnull UUID playerUuid, boolean showHud) {
      PlayerHudSettings.HudSettings settings = get(playerUuid);
      settings.showHud = showHud;
      save();
   }

   public static void setShowSelf(@Nonnull UUID playerUuid, boolean showSelf) {
      PlayerHudSettings.HudSettings settings = get(playerUuid);
      settings.showSelf = showSelf;
      save();
   }

   public static void setMaxDisplayedMembers(@Nonnull UUID playerUuid, int max) {
      PlayerHudSettings.HudSettings settings = get(playerUuid);
      settings.maxDisplayedMembers = Math.max(1, Math.min(8, max));
      save();
   }

   public static void setOrderMode(@Nonnull UUID playerUuid, PlayerHudSettings.OrderMode mode) {
      PlayerHudSettings.HudSettings settings = get(playerUuid);
      settings.orderMode = mode;
      save();
   }

   public static boolean shouldShowWarning(@Nonnull UUID playerUuid, int partyMemberCount) {
      PlayerHudSettings.HudSettings settings = get(playerUuid);
      return partyMemberCount > settings.maxDisplayedMembers && settings.orderMode != PlayerHudSettings.OrderMode.DISTANCE;
   }

   public static class HudSettings {
      public boolean showHud = true;
      public boolean showSelf = true;
      public int maxDisplayedMembers = 8;
      public PlayerHudSettings.OrderMode orderMode = PlayerHudSettings.OrderMode.FIXED;

      public HudSettings() {
      }

      public HudSettings(boolean showHud, boolean showSelf, int maxDisplayedMembers, PlayerHudSettings.OrderMode orderMode) {
         this.showHud = showHud;
         this.showSelf = showSelf;
         this.maxDisplayedMembers = Math.max(1, Math.min(8, maxDisplayedMembers));
         this.orderMode = orderMode;
      }

      public void validate() {
         this.maxDisplayedMembers = Math.max(1, Math.min(8, this.maxDisplayedMembers));
         if (this.orderMode == null) {
            this.orderMode = PlayerHudSettings.OrderMode.FIXED;
         }
      }
   }

   public enum OrderMode {
      FIXED,
      DISTANCE;
   }
}
