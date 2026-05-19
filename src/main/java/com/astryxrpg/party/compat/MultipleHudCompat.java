package com.astryxrpg.party.compat;

import com.buuz135.mhud.MultipleHUD;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public class MultipleHudCompat {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private static Boolean available = null;
   public static final String PARTY_HUD_ID = "PartyHud";

   public static boolean isAvailable() {
      if (available == null) {
         try {
            Class.forName("com.buuz135.mhud.MultipleHUD");
            available = true;
            ((Api)LOGGER.atInfo()).log("[PartyMod] MultipleHUD detected - using multi-HUD compatibility mode");
         } catch (ClassNotFoundException e) {
            available = false;
            ((Api)LOGGER.atInfo()).log("[PartyMod] MultipleHUD not found - using native HUD mode");
         }
      }

      return available;
   }

   public static void setCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull String hudId, @Nonnull CustomUIHud hud) {
      MultipleHUD.getInstance().setCustomHud(player, playerRef, hudId, hud);
   }

   public static void hideCustomHud(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull String hudId) {
      MultipleHUD.getInstance().hideCustomHud(player, playerRef, hudId);
   }
}
