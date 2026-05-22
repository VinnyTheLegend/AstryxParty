package com.astryxrpg.party.systems;

import com.astryxrpg.party.AstryxParty;
import com.astryxrpg.party.party.Party;
import com.astryxrpg.party.party.PartyManager;
import com.astryxrpg.party.party.PartyPlayerListHud;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.EntityStatUpdate;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import javax.annotation.Nonnull;

public class PartyStatChangeListener extends EntityTickingSystem<EntityStore> {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private final ComponentType<EntityStore, EntityStatMap> componentType;

   public PartyStatChangeListener(ComponentType<EntityStore, EntityStatMap> componentType) {
      this.componentType = componentType;
   }

   @Nonnull
   @Override
   public Query<EntityStore> getQuery() {
      return this.componentType;
   }

   @Override
   public void tick(
      float dt,
      int index,
      @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
      @Nonnull Store<EntityStore> store,
      @Nonnull CommandBuffer<EntityStore> commandBuffer
   ) {
      Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
      EntityStatMap statMap = archetypeChunk.getComponent(index, this.componentType);

      if (statMap == null) {
         return;
      }

      Int2ObjectMap<List<EntityStatUpdate>> statChanges = statMap.getSelfUpdates();
      Int2ObjectMap<FloatList> statValues = statMap.getSelfStatValues();

      if (statChanges.isEmpty()) {
         return;
      }

      int healthIndex = DefaultEntityStatTypes.getHealth();
      int staminaIndex = DefaultEntityStatTypes.getStamina();

      boolean healthChanged = false;
      boolean staminaChanged = false;
      float newHealth = 0.0F;
      float newMaxHealth = 0.0F;
      float newStamina = 0.0F;
      float newMaxStamina = 0.0F;

      for (int statIndex = 0; statIndex < statMap.size(); statIndex++) {
         List<EntityStatUpdate> updates = statChanges.get(statIndex);
         if (updates == null || updates.isEmpty()) {
            continue;
         }

         FloatList statChangeList = statValues.get(statIndex);
         if (statChangeList == null) {
            continue;
         }

         for (int i = 0; i < updates.size(); i++) {
            int prevIdx = i * 2;
            int currIdx = i * 2 + 1;

            if (prevIdx >= statChangeList.size() || currIdx >= statChangeList.size()) {
               continue;
            }

            float previous = statChangeList.getFloat(prevIdx);
            float current = statChangeList.getFloat(currIdx);

            if (statIndex == healthIndex) {
               healthChanged = true;
               newHealth = current;
               EntityStatValue healthStat = statMap.get(healthIndex);
               if (healthStat != null) {
                  newMaxHealth = healthStat.getMax();
               }
            } else if (statIndex == staminaIndex) {
               staminaChanged = true;
               newStamina = current;
               EntityStatValue staminaStat = statMap.get(staminaIndex);
               if (staminaStat != null) {
                  newMaxStamina = staminaStat.getMax();
               }
            }
         }
      }

      if (healthChanged || staminaChanged) {
         PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
         if (playerRef != null) {
            PartyManager partyManager = AstryxParty.getInstance().getPartyManager();
            Party party = partyManager.getPartyByPlayer(playerRef.getUuid());

            if (party != null) {
               PartyPlayerListHud hud = PartyPlayerListHud.getInstance();
               hud.notifyStatChangeToParty(party, playerRef.getUuid(), newHealth, newMaxHealth, newStamina, newMaxStamina);
            }
         }
      }
   }
}