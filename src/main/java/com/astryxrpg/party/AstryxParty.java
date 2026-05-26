package com.astryxrpg.party;

import com.astryxrpg.party.commands.PartyCommand;
import com.astryxrpg.party.config.PartySettingsComponent;
import com.astryxrpg.party.events.PartyEventBus;
import com.astryxrpg.party.markers.PartyMarkerTicker;
import com.astryxrpg.party.party.PartyManager;
import com.astryxrpg.party.party.PartyPlayerListHud;
import com.astryxrpg.party.party.PartyStorage;
import com.astryxrpg.party.systems.PlayerJoinSystem;
import com.astryxrpg.party.systems.PartyDamageProtectionSystem;
import com.astryxrpg.party.systems.PartyStatChangeListener;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public class AstryxParty extends JavaPlugin {
   private static AstryxParty instance;
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   public static final String PARTY_MARKER_ICON = "PartyMember.png";
   private PartyManager partyManager;

   public AstryxParty(@Nonnull JavaPluginInit init) {
      super(init);
      instance = this;
      ((Api)LOGGER.atInfo()).log("AstryxParty loaded");
   }

   protected void setup() {
      PartyStorage.init();
      ((Api)LOGGER.atInfo()).log("PartyStorage initialized");

      ComponentType<EntityStore, PartySettingsComponent> hudSettingsComponentType =
          this.getEntityStoreRegistry()
              .registerComponent(PartySettingsComponent.class, "PartySettings", PartySettingsComponent.CODEC);
      PartySettingsComponent.setComponentType(hudSettingsComponentType);

      this.registerPartyMemberIcon();
      this.partyManager = new PartyManager();
      this.getCommandRegistry().registerCommand(new PartyCommand(this));
      this.registerTicker();
      ((Api)LOGGER.atInfo()).log("AstryxParty setup complete");
   }

private void registerTicker() {
        this.getEntityStoreRegistry().registerSystem(new PlayerJoinSystem());
        ((Api)LOGGER.atInfo()).log("Registered PlayerJoinSystem as RefSystem");
        this.getEntityStoreRegistry().registerSystem(PartyMarkerTicker.getInstance());
        ((Api)LOGGER.atInfo()).log("Registered PartyMarkerTicker as TickingSystem");
        PartyPlayerListHud.getInstance().init();
        this.getEntityStoreRegistry().registerSystem(PartyPlayerListHud.getInstance());
        ((Api)LOGGER.atInfo()).log("Registered PartyPlayerListHud as TickingSystem");
        
        this.getEntityStoreRegistry().registerSystem(new PartyStatChangeListener(EntityStatsModule.get().getEntityStatMapComponentType()));
        ((Api)LOGGER.atInfo()).log("Registered PartyStatChangeListener as EntityTickingSystem");
        
        this.getEntityStoreRegistry().registerSystem(new PartyDamageProtectionSystem());
        ((Api)LOGGER.atInfo()).log("Registered PartyDamageProtectionSystem as DamageEventSystem");
     }

   private void registerPartyMemberIcon() {
      try {
         Path iconFile;
         Path iconsDir = this.getDataDirectory().resolve("icons");
         Files.createDirectories(iconsDir);
         iconFile = iconsDir.resolve("PartyMember.png");
         label38:
         if (!Files.exists(iconFile)) {
            try (InputStream is = this.getClass().getResourceAsStream("/Common/UI/WorldMap/MapMarkers/PartyMember.png")) {
               if (is != null) {
                  Files.copy(is, iconFile);
                  ((Api)LOGGER.atInfo()).log("Copied PartyMember.png to %s", iconFile);
                  break label38;
               }

               ((Api)LOGGER.atWarning()).log("PartyMember.png not found in resources");
            }

            return;
         }

         byte[] iconBytes = Files.readAllBytes(iconFile);
         FileCommonAsset asset = new FileCommonAsset(iconFile, "PartyMember.png", iconBytes);
         CommonAssetModule.get().addCommonAsset("PartyMember.png", asset);
         ((Api)LOGGER.atInfo()).log("Registered party member icon: %s", "PartyMember.png");
      } catch (IOException e) {
         ((Api)((Api)LOGGER.atSevere()).withCause(e)).log("Failed to register party member icon");
      }
   }

   protected void shutdown() {
      PartyPlayerListHud.getInstance().shutdown();
      PartyEventBus.clearListeners();
      PartyStorage.close();
      ((Api)LOGGER.atInfo()).log("AstryxParty shutdown complete");
   }

   @Nonnull
   public static AstryxParty getInstance() {
      return instance;
   }

   @Nonnull
   public PartyManager getPartyManager() {
      return this.partyManager;
   }
}