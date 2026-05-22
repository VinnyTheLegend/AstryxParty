package com.astryxrpg.party.config;

import javax.annotation.Nonnull;

public class PartySettings {
   public enum OrderMode {
      FIXED,
      DISTANCE
   }

   @Nonnull
   public static PartySettingsComponent get(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef) {
      PartySettingsComponent component = playerRef.getComponent(PartySettingsComponent.getComponentType());
      if (component != null) {
         return component;
      }
      return new PartySettingsComponent();
   }

   public static void update(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef) {
      // Component is already on the entity, no action needed since we modify it directly
   }

   public static void setShowHud(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef, boolean showHud) {
      PartySettingsComponent settings = get(playerRef);
      settings.setShowHud(showHud);
      settings.validate();
   }

   public static void setShowSelf(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef, boolean showSelf) {
      PartySettingsComponent settings = get(playerRef);
      settings.setShowSelf(showSelf);
      settings.validate();
   }

   public static void setMaxDisplayedMembers(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef, int max) {
      PartySettingsComponent settings = get(playerRef);
      settings.setMaxDisplayedMembers(Math.max(1, Math.min(8, max)));
      settings.validate();
   }

   public static void setOrderMode(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef, PartySettings.OrderMode mode) {
      PartySettingsComponent settings = get(playerRef);
      settings.setOrderMode(mode);
      settings.validate();
   }

   public static boolean shouldShowWarning(@Nonnull com.hypixel.hytale.server.core.universe.PlayerRef playerRef, int partyMemberCount) {
      PartySettingsComponent settings = playerRef.getComponent(PartySettingsComponent.getComponentType());
      if (settings == null) {
         return partyMemberCount > 8;
      }
      return partyMemberCount > settings.getMaxDisplayedMembers() &&
             settings.getOrderMode() != PartySettings.OrderMode.DISTANCE;
   }
}