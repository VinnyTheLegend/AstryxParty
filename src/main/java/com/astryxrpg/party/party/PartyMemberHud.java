package com.astryxrpg.party.party;

import com.astryxrpg.party.config.PlayerHudSettings;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

public class PartyMemberHud extends CustomUIHud {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private static final int MAX_DISPLAYED_MEMBERS = 8;
   private static final String[] NAME_SELECTORS = new String[8];
   private static final String[] DISTANCE_SELECTORS = new String[8];
   private static final String[] HEALTH_SELECTORS = new String[8];
   private static final String[] STAMINA_SELECTORS = new String[8];
   private static final String[] VISIBLE_SELECTORS = new String[8];
   private static final String[] DISTANCE_STRINGS;
   private volatile boolean hudInitialized = false;
   private final UUID viewerUuid;
   private final Map<UUID, PartyMemberHud.MemberDisplayData> memberData = new ConcurrentHashMap<>();
   private final Set<UUID> memberOrder = Collections.synchronizedSet(new LinkedHashSet<>());
   private volatile boolean pendingUpdate = false;
   private final List<UUID> reusableMembersToShow = new ArrayList<>(8);
   private final List<UUID> reusableOnlineMembers = new ArrayList<>(8);
   private final Comparator<UUID> distanceComparator = Comparator.comparingInt(uuid -> {
      PartyMemberHud.MemberDisplayData data = this.memberData.get(uuid);
      return data != null ? data.distance : Integer.MAX_VALUE;
   });

   public PartyMemberHud(@Nonnull PlayerRef playerRef) {
      super(playerRef);
      this.viewerUuid = playerRef.getUuid();
      ((Api)LOGGER.atInfo()).log("PartyMemberHud created for player %s", playerRef.getUsername());
   }

   protected void build(@Nonnull UICommandBuilder builder) {
      ((Api)LOGGER.atInfo()).log("PartyMemberHud build() called, memberData size=%d", this.memberData.size());
      builder.append("Hud/Party/PartyHud.ui");
      this.hudInitialized = true;
      if (this.pendingUpdate || !this.memberData.isEmpty()) {
         this.pendingUpdate = false;
         this.pushUpdate();
      }

      ((Api)LOGGER.atInfo()).log("PartyMemberHud build() complete");
   }

   public void updateMemberData(
      @Nonnull UUID memberUuid, @Nonnull String name, float health, float maxHealth, float stamina, float maxStamina, int distance, boolean online
   ) {
      this.memberOrder.add(memberUuid);
      PartyMemberHud.MemberDisplayData data = this.memberData.computeIfAbsent(memberUuid, k -> new PartyMemberHud.MemberDisplayData(name));
      data.name = name;
      data.health = health;
      data.maxHealth = maxHealth;
      data.stamina = stamina;
      data.maxStamina = maxStamina;
      data.distance = distance;
      data.online = online;
   }

   public void removeMember(@Nonnull UUID memberUuid) {
      this.memberOrder.remove(memberUuid);
      if (this.memberData.remove(memberUuid) != null) {
         this.pushUpdate();
      }
   }

   public void clearMembers() {
      this.memberOrder.clear();
      this.memberData.clear();
      this.pushUpdate();
   }

   public void setHudVisible(boolean visible) {
      if (!this.hudInitialized) {
         this.pendingUpdate = true;
      } else {
         UICommandBuilder freshBuilder = new UICommandBuilder();
         freshBuilder.set("#PartyHudRoot.Visible", visible);
         freshBuilder.set("#MemberListContainer.Visible", visible);
         this.update(false, freshBuilder);
      }
   }

   public int getMemberCount() {
      return this.memberData.size();
   }

   public void pushBarsOnly() {
      if (this.hudInitialized) {
         UICommandBuilder freshBuilder = new UICommandBuilder();
         List<UUID> snapshot;
         synchronized (this.memberOrder) {
            snapshot = new ArrayList<>(this.memberOrder);
         }

         int visibleCount = Math.min(snapshot.size(), 8);

         for (int i = 0; i < visibleCount; i++) {
            UUID memberUuid = snapshot.get(i);
            PartyMemberHud.MemberDisplayData data = this.memberData.get(memberUuid);
            if (data != null) {
               freshBuilder.set(HEALTH_SELECTORS[i], data.getHealthPercent());
               freshBuilder.set(STAMINA_SELECTORS[i], data.getStaminaPercent());
            }
         }

         this.update(false, freshBuilder);
      }
   }

   public void updateMemberBars(@Nonnull UUID memberUuid, float health, float maxHealth, float stamina, float maxStamina) {
      PartyMemberHud.MemberDisplayData data = this.memberData.get(memberUuid);
      if (data != null) {
         data.health = health;
         data.maxHealth = maxHealth;
         data.stamina = stamina;
         data.maxStamina = maxStamina;
      }
   }

   public void pushUpdate() {
      if (!this.hudInitialized) {
         this.pendingUpdate = true;
      } else {
         PlayerHudSettings.HudSettings settings = PlayerHudSettings.get(this.viewerUuid);
         synchronized (this) {
            this.reusableMembersToShow.clear();
            synchronized (this.memberOrder) {
               this.reusableMembersToShow.addAll(this.memberOrder);
            }

            if (!settings.showSelf) {
               this.reusableMembersToShow.remove(this.viewerUuid);
            }

            if (settings.orderMode == PlayerHudSettings.OrderMode.DISTANCE) {
               this.reusableMembersToShow.sort(this.distanceComparator);
            }

            int limit = Math.min(settings.maxDisplayedMembers, 8);
            this.reusableOnlineMembers.clear();

            for (UUID uuid : this.reusableMembersToShow) {
               PartyMemberHud.MemberDisplayData data = this.memberData.get(uuid);
               if (data != null && data.online) {
                  this.reusableOnlineMembers.add(uuid);
               }
            }

            UICommandBuilder freshBuilder = new UICommandBuilder();

            for (int i = 0; i < 8; i++) {
               if (i < this.reusableOnlineMembers.size() && i < limit) {
                  UUID memberUuid = this.reusableOnlineMembers.get(i);
                  PartyMemberHud.MemberDisplayData data = this.memberData.get(memberUuid);
                  if (data != null) {
                     freshBuilder.set(NAME_SELECTORS[i], data.name);
                     String distanceStr = data.distance >= 0 && data.distance < DISTANCE_STRINGS.length
                        ? DISTANCE_STRINGS[data.distance]
                        : "(" + data.distance + "m)";
                     freshBuilder.set(DISTANCE_SELECTORS[i], distanceStr);
                     freshBuilder.set(HEALTH_SELECTORS[i], data.getHealthPercent());
                     freshBuilder.set(STAMINA_SELECTORS[i], data.getStaminaPercent());
                     freshBuilder.set(VISIBLE_SELECTORS[i], true);
                  } else {
                     freshBuilder.set(VISIBLE_SELECTORS[i], false);
                  }
               } else {
                  freshBuilder.set(VISIBLE_SELECTORS[i], false);
               }
            }

            this.update(false, freshBuilder);
         }
      }
   }

   static {
      for (int i = 0; i < 8; i++) {
         NAME_SELECTORS[i] = "#Member" + i + "Name.Text";
         DISTANCE_SELECTORS[i] = "#Member" + i + "Distance.Text";
         HEALTH_SELECTORS[i] = "#Member" + i + "Health.Value";
         STAMINA_SELECTORS[i] = "#Member" + i + "Stamina.Value";
         VISIBLE_SELECTORS[i] = "#Member" + i + ".Visible";
      }

      DISTANCE_STRINGS = new String[1000];

      for (int i = 0; i < 1000; i++) {
         DISTANCE_STRINGS[i] = "(" + i + "m)";
      }
   }

   public static class MemberDisplayData {
      public volatile String name;
      public volatile float health;
      public volatile float maxHealth;
      public volatile float stamina;
      public volatile float maxStamina;
      public volatile int distance;
      public volatile boolean online;

      public MemberDisplayData(String name) {
         this.name = name;
         this.health = 100.0F;
         this.maxHealth = 100.0F;
         this.stamina = 100.0F;
         this.maxStamina = 100.0F;
         this.distance = 0;
         this.online = true;
      }

      public float getHealthPercent() {
         float max = this.maxHealth;
         return max > 0.0F ? this.health / max : 0.0F;
      }

      public float getStaminaPercent() {
         float max = this.maxStamina;
         return max > 0.0F ? this.stamina / max : 0.0F;
      }
   }
}
