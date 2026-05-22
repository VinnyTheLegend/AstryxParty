package com.astryxrpg.party.party;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.astryxrpg.party.AstryxParty;
import com.astryxrpg.party.compat.MultipleHudCompat;
import com.astryxrpg.party.config.PartySettingsComponent;
import com.astryxrpg.party.events.PartyCreateEvent;
import com.astryxrpg.party.events.PartyDisbandEvent;
import com.astryxrpg.party.events.PartyEvent;
import com.astryxrpg.party.events.PartyEventBus;
import com.astryxrpg.party.events.PartyEventListener;
import com.astryxrpg.party.events.PartyJoinEvent;
import com.astryxrpg.party.events.PartyLeaveEvent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class PartyPlayerListHud extends TickingSystem<EntityStore> implements PartyEventListener {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private static final PartyPlayerListHud INSTANCE = new PartyPlayerListHud();
   private float accumulator = 0.0F;
   private static final float STAT_UPDATE_INTERVAL = 1.0F;
   private float cleanupAccumulator = 0.0F;
   private static final float CLEANUP_INTERVAL = 3.0F;
   private static int minMembersForHud = 1;
   private final Map<UUID, PartyPlayerListHud.ViewerHudState> viewerStates = new ConcurrentHashMap<>();

   private PartyPlayerListHud() {
   }

   public static PartyPlayerListHud getInstance() {
      return INSTANCE;
   }

   public void init() {
      PartyEventBus.register(this);
      ((Api) LOGGER.atInfo()).log("PartyPlayerListHud initialized and registered with event bus");
   }

   public void shutdown() {
      PartyEventBus.unregister(this);
      this.viewerStates.clear();
      ((Api) LOGGER.atInfo()).log("PartyPlayerListHud shutdown");
   }

   public void removeFakeMembersFromHud(@Nonnull Party party) {
      Set<UUID> fakeMemberUuids = party.getFakeMembers().keySet();
      if (!fakeMemberUuids.isEmpty()) {
         ((Api) LOGGER.atInfo()).log("[DEBUG] removeFakeMembersFromHud: Removing %d fake members from HUD",
               fakeMemberUuids.size());

         for (UUID memberUuid : party.getMemberUuids()) {
            if (memberUuid == null) {
               continue;
            }
            PartyPlayerListHud.ViewerHudState viewerState = this.viewerStates.get(memberUuid);
            if (viewerState != null) {
               for (UUID fakeUuid : fakeMemberUuids) {
                  if (fakeUuid == null) {
                     continue;
                  }
                  viewerState.memberStates.remove(fakeUuid);
                  if (viewerState.hudInstance != null) {
                     try {
                        viewerState.hudInstance.removeMember(fakeUuid);
                     } catch (Exception e) {
                        ((Api) ((Api) LOGGER.atWarning()).withCause(e))
                              .log("Error removing fake member from HUD display");
                     }
                  }
               }

               this.updateHudVisibility(memberUuid, party);
            }
         }

         ((Api) LOGGER.atInfo()).log("[DEBUG] removeFakeMembersFromHud: Completed");
      }
   }

   public static void setMinMembersForHud(int min) {
      minMembersForHud = Math.max(1, min);
      ((Api) LOGGER.atInfo()).log("HUD minimum members set to %d", minMembersForHud);
   }

   public static int getMinMembersForHud() {
      return minMembersForHud;
   }

public void refreshHudForPlayer(@Nonnull UUID playerUuid) {
        Party party = AstryxParty.getInstance().getPartyManager().getPartyByPlayer(playerUuid);
        PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
        if (state == null) {
           state = new PartyPlayerListHud.ViewerHudState(playerUuid);
           state.currentPartyId = party != null ? party.getId() : null;
           this.viewerStates.put(playerUuid, state);
        }

        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null) {
           return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
           return;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
           return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
           return;
        }

        world.execute(() -> {
           try {
              PartyPlayerListHud.ViewerHudState stateInWorld = this.viewerStates.get(playerUuid);
              if (stateInWorld == null) {
                 return;
              }

              PartySettingsComponent settings = store.getComponent(playerRef.getReference(),
                    PartySettingsComponent.getComponentType());
              boolean showHud = settings != null && settings.getShowHud();

              if (showHud && party != null) {
                 if (!stateInWorld.hudVisible) {
                    this.showHudForPlayer(playerUuid, party);
                 } else {
                    for (PartyPlayerListHud.MemberHudState memberState : stateInWorld.memberStates.values()) {
                       memberState.lastHealth = -1.0F;
                       memberState.lastDistance = -1;
                    }
                    if (stateInWorld.hudInstance != null) {
                       stateInWorld.hudInstance.pushUpdate();
                    }
                 }
              } else if (!showHud && stateInWorld.hudVisible) {
                 this.hideHudForPlayer(playerUuid);
              }
           } catch (Exception e) {
              ((Api) LOGGER.atWarning()).withCause(e).log("Error in refreshHudForPlayer for %s", playerUuid);
           }
        });
     }

   @Override
   public void onPartyEvent(PartyEvent event) {
      ((Api) LOGGER.atInfo()).log("[DEBUG] onPartyEvent received: %s", event.getClass().getSimpleName());
      if (event instanceof PartyCreateEvent createEvent) {
         this.handlePartyCreate(createEvent);
      } else if (event instanceof PartyJoinEvent joinEvent) {
         this.handlePartyJoin(joinEvent);
      } else if (event instanceof PartyLeaveEvent leaveEvent) {
         this.handlePartyLeave(leaveEvent);
      } else if (event instanceof PartyDisbandEvent disbandEvent) {
         this.handlePartyDisband(disbandEvent);
      }
   }

   private void handlePartyCreate(@Nonnull PartyCreateEvent event) {
      Party party = event.getParty();
      UUID leaderUuid = event.getLeaderUuid();
      ((Api) LOGGER.atInfo())
            .log("[DEBUG] handlePartyCreate for leader %s, party members=%d, minForHud=%d", leaderUuid,
                  party.getMemberCount(), minMembersForHud);
      PartyPlayerListHud.ViewerHudState state = this.viewerStates.computeIfAbsent(leaderUuid,
            PartyPlayerListHud.ViewerHudState::new);
      state.currentPartyId = party.getId();
      this.updateHudVisibility(leaderUuid, party);
   }

   private void handlePartyJoin(@Nonnull PartyJoinEvent event) {
      Party party = event.getParty();
      UUID joiningPlayerUuid = event.getJoiningPlayerUuid();
      ((Api) LOGGER.atInfo())
            .log("[DEBUG] handlePartyJoin for player %s, party members=%d, minForHud=%d", joiningPlayerUuid,
                  party.getMemberCount(), minMembersForHud);
      PartyPlayerListHud.ViewerHudState joinerState = this.viewerStates.computeIfAbsent(joiningPlayerUuid,
            PartyPlayerListHud.ViewerHudState::new);
      joinerState.currentPartyId = party.getId();

      for (UUID memberUuid : party.getMemberUuids()) {
         this.updateHudVisibility(memberUuid, party);
         if (!memberUuid.equals(joiningPlayerUuid)) {
            PartyPlayerListHud.ViewerHudState memberState = this.viewerStates.get(memberUuid);
            if (memberState != null) {
               memberState.memberStates.computeIfAbsent(joiningPlayerUuid, PartyPlayerListHud.MemberHudState::new);
            }
         }

         if (!memberUuid.equals(joiningPlayerUuid)) {
            joinerState.memberStates.computeIfAbsent(memberUuid, PartyPlayerListHud.MemberHudState::new);
         }
      }

      this.forceUpdateForParty(party);
   }

   private void handlePartyLeave(@Nonnull PartyLeaveEvent event) {
      UUID leavingPlayerUuid = event.getLeavingPlayerUuid();
      Party party = event.getParty();
      ((Api) LOGGER.atInfo()).log("Handling party leave for player %s", leavingPlayerUuid);
      PartyPlayerListHud.ViewerHudState leaverState = this.viewerStates.get(leavingPlayerUuid);
      if (leaverState != null) {
         if (leaverState.hudVisible) {
            try {
               this.hideHudForPlayer(leavingPlayerUuid);
            } catch (Exception e) {
               ((Api) ((Api) LOGGER.atWarning()).withCause(e)).log("Error hiding HUD for leaving player %s",
                     leavingPlayerUuid);
            }
         }

         leaverState.memberStates.clear();
         if (leaverState.hudInstance != null) {
            leaverState.hudInstance.clearMembers();
         }
      }

      this.viewerStates.remove(leavingPlayerUuid);
      if (party != null) {
         for (UUID memberUuid : party.getMemberUuids()) {
            if (memberUuid == null) {
               continue;
            }
            PartyPlayerListHud.ViewerHudState memberState = this.viewerStates.get(memberUuid);
            if (memberState != null) {
               memberState.memberStates.remove(leavingPlayerUuid);
               if (memberState.hudInstance != null) {
                  try {
                     memberState.hudInstance.removeMember(leavingPlayerUuid);
                  } catch (Exception e) {
                     ((Api) ((Api) LOGGER.atWarning()).withCause(e)).log("Error removing member from HUD display");
                  }
               }
            }

            this.updateHudVisibility(memberUuid, party);
         }
      }
   }

   private void handlePartyDisband(@Nonnull PartyDisbandEvent event) {
      ((Api) LOGGER.atInfo()).log("Handling party disband for party %s", event.getPartyId());

      for (UUID memberUuid : event.getFormerMemberUuids()) {
         PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(memberUuid);
         if (state != null) {
            if (state.hudVisible) {
               this.hideHudForPlayer(memberUuid);
            }

            state.memberStates.clear();
            if (state.hudInstance != null) {
               state.hudInstance.clearMembers();
            }
         }

         this.viewerStates.remove(memberUuid);
      }
   }

private void updateHudVisibility(@Nonnull UUID playerUuid, @Nullable Party party) {
       PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
       if (state != null) {
          if (party != null) {
             ((Api) LOGGER.atInfo()).log("[DEBUG] Will show HUD for player %s", playerUuid);
             this.showHudForPlayer(playerUuid, party);
          } else if (state.hudVisible) {
             ((Api) LOGGER.atInfo()).log("[DEBUG] Will hide HUD for player %s", playerUuid);
             this.hideHudForPlayer(playerUuid);
          }
       }
    }

private void showHudForPlayer(@Nonnull UUID playerUuid, @Nonnull Party party) {
       ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer START for %s", playerUuid);
       PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
       if (playerRef == null) {
          ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: playerRef is null!");
       } else {
          Ref<EntityStore> ref = playerRef.getReference();
          Store<EntityStore> store = ref.getStore();
          if (store == null) {
             ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: store is null!");
             return;
          }
          World world = store.getExternalData().getWorld();
          if (world == null) {
             ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: world is null!");
             return;
          }
          world.execute(() -> {
             Store<EntityStore> worldStore = ref.getStore();
             Player player = (Player) worldStore.getComponent(playerRef.getReference(), Player.getComponentType());
             if (player == null) {
                ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: Player component is null!");
             } else {
                PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
                if (state == null) {
                   ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: ViewerHudState is null!");
} else {
                    if (state.hudInstance != null) {
                       ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: Reusing existing HUD instance for %s",
                             playerRef.getUsername());
                       state.hudInstance.setHudVisible(true);
                       state.hudVisible = true;
                       if (MultipleHudCompat.isAvailable()) {
                          MultipleHudCompat.setCustomHud(player, playerRef, "PartyHud", state.hudInstance);
                       } else {
                          player.getHudManager().setCustomHud(playerRef, state.hudInstance);
                       }
                       PartyPlayerListHud.this.populateMemberDataForViewer(state, playerRef, player, party, worldStore);
                    } else {
                      ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: Creating new PartyMemberHud for %s",
                            playerRef.getUsername());
                      PartyMemberHud hud = new PartyMemberHud(playerRef);
                      state.hudInstance = hud;
                      state.hudVisible = true;
                      if (MultipleHudCompat.isAvailable()) {
                         ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: Using MultipleHUD.setCustomHud...");
                         MultipleHudCompat.setCustomHud(player, playerRef, "PartyHud", hud);
                      } else {
                         ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: Using native setCustomHud...");
                         player.getHudManager().setCustomHud(playerRef, hud);
                      }

                      ((Api) LOGGER.atInfo()).log("[DEBUG] showHudForPlayer: setCustomHud DONE, hudVisible=true");
                      PartyPlayerListHud.this.populateMemberDataForViewer(state, playerRef, player, party, worldStore);
                   }
                }
             }
          });
       }
    }

   private void hideHudForPlayer(@Nonnull UUID playerUuid) {
      PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
      if (playerRef == null) {
         PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
         if (state != null) {
            state.hudInstance = null;
            state.hudVisible = false;
         }
      } else {
         Ref<EntityStore> ref = playerRef.getReference();
         Store<EntityStore> store = ref.getStore();
         if (store == null) {
            PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
            if (state != null) {
               state.hudInstance = null;
               state.hudVisible = false;
            }
            return;
         }
         Player player = (Player) store.getComponent(playerRef.getReference(), Player.getComponentType());
         if (player == null) {
            PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
            if (state != null) {
               state.hudInstance = null;
               state.hudVisible = false;
            }
} else if (player.getWorld() == null) {
             PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
             if (state != null) {
                state.hudInstance = null;
                state.hudVisible = false;
             }
          } else {
             player.getWorld().execute(() -> {
                try {
                   PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);
                   if (state != null) {
                      state.hudVisible = false;
                      if (state.hudInstance != null) {
                         state.hudInstance.setHudVisible(false);
                         state.hudInstance = null;
                         ((Api) LOGGER.atInfo()).log("[DEBUG] hideHudForPlayer: Called setHudVisible(false) on HUD for %s",
                               playerUuid);
                      }
                   }
                   if (MultipleHudCompat.isAvailable()) {
                      ((Api) LOGGER.atInfo()).log("[DEBUG] hideHudForPlayer: Calling MultipleHUD.hideCustomHud for %s",
                            playerUuid);
                      MultipleHudCompat.hideCustomHud(player, playerRef, "PartyHud");
                   }
                } catch (Exception e) {
                   ((Api) ((Api) LOGGER.atWarning()).withCause(e))
                         .log("[DEBUG] hideHudForPlayer: Error hiding HUD for %s", playerUuid);
                }
             });
          }
       }
   }

public void tick(float dt, int index, Store<EntityStore> store) {
       float cappedDt = Math.min(dt, STAT_UPDATE_INTERVAL);
       this.accumulator += cappedDt;
       this.cleanupAccumulator += cappedDt;
       if (this.cleanupAccumulator >= CLEANUP_INTERVAL) {
          this.cleanupAccumulator = 0.0F;
          this.cleanupOfflinePlayerStates();
       }

       if (this.accumulator >= STAT_UPDATE_INTERVAL) {
          this.accumulator = 0.0F;
          World currentWorld = ((EntityStore) store.getExternalData()).getWorld();
          if (currentWorld != null) {
             this.checkForMissingHuds(currentWorld, store);
          }
          this.updateAllMemberStats(store);
       }
    }

    private void checkForMissingHuds(@Nonnull World world, @Nonnull Store<EntityStore> store) {
       PartyManager partyManager = AstryxParty.getInstance().getPartyManager();

       for (Player viewer : world.getPlayers()) {
          UUID playerUuid = viewer.getUuid();
          Party party = partyManager.getPartyByPlayer(playerUuid);
          PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(playerUuid);

          if (state == null && party != null) {
             ((Api) LOGGER.atInfo()).log(
                   "[DEBUG] checkForMissingHuds: Player %s is in party but has no state - initializing",
                   playerUuid);
             state = new PartyPlayerListHud.ViewerHudState(playerUuid);
             state.currentPartyId = party.getId();
             this.viewerStates.put(playerUuid, state);
          }

          if (state == null) {
             continue;
          }

          boolean shouldShow = this.shouldShowHud(party, playerUuid, store);
          if (state.hudVisible && !shouldShow) {
             ((Api) LOGGER.atInfo()).log("[DEBUG] checkForMissingHuds: Hiding HUD for player %s (ShowHud=false)",
                   playerUuid);
             this.hideHudForPlayer(playerUuid);
          } else if (!state.hudVisible && shouldShow) {
             ((Api) LOGGER.atInfo()).log("[DEBUG] checkForMissingHuds: Attempting HUD for player %s",
                   playerUuid);
             this.showHudForPlayer(playerUuid, party);
          }
       }
    }

    private boolean shouldShowHud(@Nullable Party party, @Nonnull UUID viewerUuid, @Nonnull Store<EntityStore> store) {
        if (party == null) {
           return false;
        }

        PlayerRef viewerRef = Universe.get().getPlayer(viewerUuid);
        if (viewerRef == null) {
           return false;
        }

        PartySettingsComponent settings = store.getComponent(viewerRef.getReference(),
              PartySettingsComponent.getComponentType());
        if (settings == null || !settings.getShowHud()) {
           return false;
        }

        int totalCount = party.getMemberCount() + party.getFakeMembers().size();
        return totalCount >= minMembersForHud;
     }

   private void cleanupOfflinePlayerStates() {
      Iterator<Entry<UUID, PartyPlayerListHud.ViewerHudState>> iterator = this.viewerStates.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, PartyPlayerListHud.ViewerHudState> entry = iterator.next();
         UUID playerUuid = entry.getKey();
         PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
         if (playerRef == null) {
            iterator.remove();
         }
      }
   }

   private void updateAllMemberStats(Store<EntityStore> store) {
      World currentWorld = ((EntityStore) store.getExternalData()).getWorld();
      if (currentWorld != null) {
         for (Player viewer : currentWorld.getPlayers()) {
            UUID viewerUuid = viewer.getUuid();
            PartyPlayerListHud.ViewerHudState viewerState = this.viewerStates.get(viewerUuid);
            if (viewerState != null && viewerState.hudVisible) {
               try {
                  PlayerRef viewerRef = Universe.get().getPlayer(viewerUuid);
                  if (viewerRef != null) {
                     this.updateMemberStatsForViewer(viewerState, viewerRef, viewer, currentWorld, store);
                  }
               } catch (Exception var8) {
               }
            }
         }
      }
   }

   private void updateMemberStatsForViewer(
         @Nonnull PartyPlayerListHud.ViewerHudState viewerState, @Nonnull PlayerRef viewerRef, @Nonnull Player viewer,
         @Nonnull World currentWorld, @Nonnull Store<EntityStore> store) {
      UUID viewerUuid = viewerState.viewerUuid;
      TransformComponent viewerTransform = viewer.getTransformComponent();
      if (viewerTransform != null) {
         double viewerX = viewerTransform.getTransform().getPosition().getX();
         double viewerY = viewerTransform.getTransform().getPosition().getY();
         double viewerZ = viewerTransform.getTransform().getPosition().getZ();
         Party party = AstryxParty.getInstance().getPartyManager().getPartyByPlayer(viewerUuid);
         if (party != null) {
            PartyPlayerListHud.MemberHudState viewerMemberState = viewerState.memberStates.computeIfAbsent(viewerUuid,
                  PartyPlayerListHud.MemberHudState::new);
            this.updateSingleMemberStats(viewerRef, viewerMemberState, viewerX, viewerY, viewerZ, currentWorld, store);

            for (UUID memberUuid : party.getMembersExcept(viewerUuid)) {
               PartyPlayerListHud.MemberHudState memberState = viewerState.memberStates.computeIfAbsent(memberUuid,
                     PartyPlayerListHud.MemberHudState::new);
               this.updateSingleMemberStats(viewerRef, memberState, viewerX, viewerY, viewerZ, currentWorld, store);
            }

            for (FakeMember fakeMember : party.getFakeMembers().values()) {
               PartyPlayerListHud.MemberHudState memberState = viewerState.memberStates
                     .computeIfAbsent(fakeMember.getUuid(), PartyPlayerListHud.MemberHudState::new);
               this.updateFakeMemberStats(viewerRef, memberState, fakeMember, viewerX, viewerY, viewerZ);
            }

            if (viewerState.hudInstance != null) {
               viewerState.hudInstance.pushUpdate();
            }
         }
      }
   }

   private void updateSingleMemberStats(
         @Nonnull PlayerRef viewerRef,
         @Nonnull PartyPlayerListHud.MemberHudState memberState,
         double viewerX,
         double viewerY,
         double viewerZ,
         @Nonnull World currentWorld,
         @Nonnull Store<EntityStore> store) {
      UUID memberUuid = memberState.memberUuid;
      PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
      boolean online = memberRef != null;
      String name = online ? memberRef.getUsername() : memberState.lastName;
      float health = 0.0F;
      float maxHealth = 0.0F;
      float stamina = 0.0F;
      float maxStamina = 0.0F;
      int distance = 0;
      if (online) {
         Player memberPlayer = this.getMemberPlayerSafe(memberRef, currentWorld, store);
         if (memberPlayer != null) {
            EntityStatMap stats = this.getEntityStatMapSafe(memberRef, currentWorld, store);
            if (stats != null) {
               int healthIndex = DefaultEntityStatTypes.getHealth();
               int staminaIndex = DefaultEntityStatTypes.getStamina();
               EntityStatValue healthStat = stats.get(healthIndex);
               EntityStatValue staminaStat = stats.get(staminaIndex);
               if (healthStat != null) {
                  health = healthStat.get();
                  maxHealth = healthStat.getMax();
               }

               if (staminaStat != null) {
                  stamina = staminaStat.get();
                  maxStamina = staminaStat.getMax();
               }
            }

            TransformComponent transform = memberPlayer.getTransformComponent();
            if (transform != null) {
               double memberX = transform.getTransform().getPosition().getX();
               double memberY = transform.getTransform().getPosition().getY();
               double memberZ = transform.getTransform().getPosition().getZ();
               double dx = memberX - viewerX;
               double dy = memberY - viewerY;
               double dz = memberZ - viewerZ;
               distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
         }
      }

      if (memberState.hasChanged(health, maxHealth, stamina, maxStamina, distance, online, name)) {
         memberState.update(health, maxHealth, stamina, maxStamina, distance, online, name);
         this.sendMemberStatUpdate(viewerRef, memberUuid, name, health, maxHealth, stamina, maxStamina, distance,
               online);
      }
   }

   private boolean updateSingleMemberBars(
         @Nonnull PlayerRef viewerRef, @Nonnull PartyPlayerListHud.MemberHudState memberState, double viewerX,
         double viewerY, double viewerZ, @Nonnull Store<EntityStore> store) {
      UUID memberUuid = memberState.memberUuid;
      PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
      if (memberRef == null) {
         return false;
      }

      float health = 0.0F;
      float maxHealth = 0.0F;
      float stamina = 0.0F;
      float maxStamina = 0.0F;
      Player memberPlayer = (Player) store.getComponent(memberRef.getReference(), Player.getComponentType());
      if (memberPlayer != null) {
         EntityStatMap stats = (EntityStatMap) store.getComponent(memberRef.getReference(),
               EntityStatsModule.get().getEntityStatMapComponentType());
         if (stats != null) {
            int healthIndex = DefaultEntityStatTypes.getHealth();
            int staminaIndex = DefaultEntityStatTypes.getStamina();
            EntityStatValue healthStat = stats.get(healthIndex);
            EntityStatValue staminaStat = stats.get(staminaIndex);
            if (healthStat != null) {
               health = healthStat.get();
               maxHealth = healthStat.getMax();
            }

            if (staminaStat != null) {
               stamina = staminaStat.get();
               maxStamina = staminaStat.getMax();
            }
         }
      }

      boolean barsChanged = memberState.lastHealth != health
            || memberState.lastMaxHealth != maxHealth
            || memberState.lastStamina != stamina
            || memberState.lastMaxStamina != maxStamina;
      if (barsChanged) {
         memberState.lastHealth = health;
         memberState.lastMaxHealth = maxHealth;
         memberState.lastStamina = stamina;
         memberState.lastMaxStamina = maxStamina;
         PartyPlayerListHud.ViewerHudState viewerState = this.viewerStates.get(viewerRef.getUuid());
         if (viewerState != null && viewerState.hudInstance != null) {
            viewerState.hudInstance.updateMemberBars(memberUuid, health, maxHealth, stamina, maxStamina);
         }
      }

      return barsChanged;
   }

   private boolean updateFakeMemberBars(
         @Nonnull PlayerRef viewerRef,
         @Nonnull PartyPlayerListHud.MemberHudState memberState,
         @Nonnull FakeMember fakeMember,
         double viewerX,
         double viewerY,
         double viewerZ) {
      float health = 100.0F;
      float maxHealth = 100.0F;
      float stamina = 100.0F;
      float maxStamina = 100.0F;
      boolean barsChanged = memberState.lastHealth != health
            || memberState.lastMaxHealth != maxHealth
            || memberState.lastStamina != stamina
            || memberState.lastMaxStamina != maxStamina;
      if (barsChanged) {
         memberState.lastHealth = health;
         memberState.lastMaxHealth = maxHealth;
         memberState.lastStamina = stamina;
         memberState.lastMaxStamina = maxStamina;
         PartyPlayerListHud.ViewerHudState viewerState = this.viewerStates.get(viewerRef.getUuid());
         if (viewerState != null && viewerState.hudInstance != null) {
            viewerState.hudInstance.updateMemberBars(fakeMember.getUuid(), health, maxHealth, stamina, maxStamina);
         }
      }

      return barsChanged;
   }

   private void updateFakeMemberStats(
         @Nonnull PlayerRef viewerRef,
         @Nonnull PartyPlayerListHud.MemberHudState memberState,
         @Nonnull FakeMember fakeMember,
         double viewerX,
         double viewerY,
         double viewerZ) {
      String name = fakeMember.getName();
      float health = 100.0F;
      float maxHealth = 100.0F;
      float stamina = 100.0F;
      float maxStamina = 100.0F;
      double dx = fakeMember.getX() - viewerX;
      double dy = fakeMember.getY() - viewerY;
      double dz = fakeMember.getZ() - viewerZ;
      int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (memberState.hasChanged(health, maxHealth, stamina, maxStamina, distance, true, name)) {
         memberState.update(health, maxHealth, stamina, maxStamina, distance, true, name);
         this.sendMemberStatUpdate(viewerRef, fakeMember.getUuid(), name, health, maxHealth, stamina, maxStamina,
               distance, true);
      }
   }

   private void sendMemberStatUpdate(
         @Nonnull PlayerRef viewerRef,
         @Nonnull UUID memberUuid,
         @Nonnull String name,
         float health,
         float maxHealth,
         float stamina,
         float maxStamina,
         int distance,
         boolean online) {
      UUID viewerUuid = viewerRef.getUuid();
      PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(viewerUuid);
      if (state != null && state.hudInstance != null) {
         state.hudInstance.updateMemberData(memberUuid, name, health, maxHealth, stamina, maxStamina, distance, online);
      }
   }

   private void populateMemberDataForViewer(
         @Nonnull PartyPlayerListHud.ViewerHudState viewerState, @Nonnull PlayerRef viewerRef, @Nonnull Player viewer,
         @Nonnull Party party, @Nonnull Store<EntityStore> store) {
      if (viewerState.hudInstance == null) {
         ((Api) LOGGER.atInfo()).log("[DEBUG] populateMemberDataForViewer: hudInstance is null, skipping");
      } else {
         UUID viewerUuid = viewerState.viewerUuid;
         World currentWorld = viewer.getWorld();
         if (currentWorld == null) {
            ((Api) LOGGER.atInfo()).log("[DEBUG] populateMemberDataForViewer: world is null, skipping");
         } else {
            TransformComponent viewerTransform = viewer.getTransformComponent();
            if (viewerTransform == null) {
               ((Api) LOGGER.atInfo()).log("[DEBUG] populateMemberDataForViewer: transform is null, skipping");
            } else {
               double viewerX = viewerTransform.getTransform().getPosition().getX();
               double viewerY = viewerTransform.getTransform().getPosition().getY();
               double viewerZ = viewerTransform.getTransform().getPosition().getZ();
               ((Api) LOGGER.atInfo())
                     .log("[DEBUG] populateMemberDataForViewer: Populating %d members + %d fake members",
                           party.getMemberCount(), party.getFakeMembers().size());
               PartyPlayerListHud.MemberHudState viewerMemberState = viewerState.memberStates
                     .computeIfAbsent(viewerUuid, PartyPlayerListHud.MemberHudState::new);
               this.populateSingleMemberData(viewerRef, viewerMemberState, viewerX, viewerY, viewerZ, currentWorld,
                     store);

               for (UUID memberUuid : party.getMembersExcept(viewerUuid)) {
                  PartyPlayerListHud.MemberHudState memberState = viewerState.memberStates.computeIfAbsent(memberUuid,
                        PartyPlayerListHud.MemberHudState::new);
                  this.populateSingleMemberData(viewerRef, memberState, viewerX, viewerY, viewerZ, currentWorld, store);
               }

               for (FakeMember fakeMember : party.getFakeMembers().values()) {
                  PartyPlayerListHud.MemberHudState memberState = viewerState.memberStates
                        .computeIfAbsent(fakeMember.getUuid(), PartyPlayerListHud.MemberHudState::new);
                  this.populateFakeMemberData(viewerRef, memberState, fakeMember, viewerX, viewerY, viewerZ);
               }

               viewerState.hudInstance.pushUpdate();
               ((Api) LOGGER.atInfo()).log("[DEBUG] populateMemberDataForViewer: Completed immediate HUD population");
            }
         }
      }
   }

   private void populateSingleMemberData(
         @Nonnull PlayerRef viewerRef,
         @Nonnull PartyPlayerListHud.MemberHudState memberState,
         double viewerX,
         double viewerY,
         double viewerZ,
         @Nonnull World currentWorld,
         @Nonnull Store<EntityStore> store) {
      UUID memberUuid = memberState.memberUuid;
      PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
      boolean online = memberRef != null;
      String name = online ? memberRef.getUsername()
            : (memberState.lastName.isEmpty() ? "Offline" : memberState.lastName);
      float health = 0.0F;
      float maxHealth = 0.0F;
      float stamina = 0.0F;
      float maxStamina = 0.0F;
      int distance = 0;
      if (online) {
         Player memberPlayer = this.getMemberPlayerSafe(memberRef, currentWorld, store);
         if (memberPlayer != null) {
            EntityStatMap stats = this.getEntityStatMapSafe(memberRef, currentWorld, store);
            if (stats != null) {
               int healthIndex = DefaultEntityStatTypes.getHealth();
               int staminaIndex = DefaultEntityStatTypes.getStamina();
               EntityStatValue healthStat = stats.get(healthIndex);
               EntityStatValue staminaStat = stats.get(staminaIndex);
               if (healthStat != null) {
                  health = healthStat.get();
                  maxHealth = healthStat.getMax();
               }

               if (staminaStat != null) {
                  stamina = staminaStat.get();
                  maxStamina = staminaStat.getMax();
               }
            }

            TransformComponent transform = memberPlayer.getTransformComponent();
            if (transform != null) {
               double memberX = transform.getTransform().getPosition().getX();
               double memberY = transform.getTransform().getPosition().getY();
               double memberZ = transform.getTransform().getPosition().getZ();
               double dx = memberX - viewerX;
               double dy = memberY - viewerY;
               double dz = memberZ - viewerZ;
               distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
         }
      }

      memberState.update(health, maxHealth, stamina, maxStamina, distance, online, name);
      this.sendMemberStatUpdate(viewerRef, memberUuid, name, health, maxHealth, stamina, maxStamina, distance, online);
   }

   private void populateFakeMemberData(
         @Nonnull PlayerRef viewerRef,
         @Nonnull PartyPlayerListHud.MemberHudState memberState,
         @Nonnull FakeMember fakeMember,
         double viewerX,
         double viewerY,
         double viewerZ) {
      String name = fakeMember.getName();
      float health = 100.0F;
      float maxHealth = 100.0F;
      float stamina = 100.0F;
      float maxStamina = 100.0F;
      double dx = fakeMember.getX() - viewerX;
      double dy = fakeMember.getY() - viewerY;
      double dz = fakeMember.getZ() - viewerZ;
      int distance = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
      memberState.update(health, maxHealth, stamina, maxStamina, distance, true, name);
      this.sendMemberStatUpdate(viewerRef, fakeMember.getUuid(), name, health, maxHealth, stamina, maxStamina, distance,
            true);
   }

   private void forceUpdateForParty(@Nonnull Party party) {
      for (UUID memberUuid : party.getMemberUuids()) {
         PartyPlayerListHud.ViewerHudState state = this.viewerStates.get(memberUuid);
         if (state != null) {
            for (PartyPlayerListHud.MemberHudState memberState : state.memberStates.values()) {
               memberState.lastHealth = -1.0F;
               memberState.lastMaxHealth = -1.0F;
               memberState.lastStamina = -1.0F;
               memberState.lastMaxStamina = -1.0F;
               memberState.lastDistance = -1;
            }
         }
      }
   }

   private Player getMemberPlayerSafe(@Nonnull PlayerRef memberRef, @Nonnull World viewerWorld,
         @Nonnull Store<EntityStore> store) {
      Player memberPlayer = (Player) store.getComponent(memberRef.getReference(), Player.getComponentType());
      if (memberPlayer == null) {
         return null;
      }

      World memberWorld = memberPlayer.getWorld();
      return memberWorld != null && viewerWorld.equals(memberWorld) ? memberPlayer : null;
   }

   private @Nullable EntityStatMap getEntityStatMapSafe(@Nonnull PlayerRef memberRef, @Nonnull World viewerWorld,
         @Nonnull Store<EntityStore> store) {
      Player memberPlayer = (Player) store.getComponent(memberRef.getReference(), Player.getComponentType());
      if (memberPlayer == null) {
         return null;
      }

      World memberWorld = memberPlayer.getWorld();
      return memberWorld != null && viewerWorld.equals(memberWorld)
            ? (EntityStatMap) store.getComponent(memberRef.getReference(),
                  EntityStatsModule.get().getEntityStatMapComponentType())
            : null;
   }

   private static class MemberHudState {
      final UUID memberUuid;
      float lastHealth;
      float lastMaxHealth;
      float lastStamina;
      float lastMaxStamina;
      int lastDistance;
      boolean lastOnline;
      String lastName;

      MemberHudState(@Nonnull UUID memberUuid) {
         this.memberUuid = memberUuid;
         this.lastHealth = -1.0F;
         this.lastMaxHealth = -1.0F;
         this.lastStamina = -1.0F;
         this.lastMaxStamina = -1.0F;
         this.lastDistance = -1;
         this.lastOnline = false;
         this.lastName = "";
      }

      boolean hasChanged(float health, float maxHealth, float stamina, float maxStamina, int distance, boolean online,
            String name) {
         return Math.abs(health - this.lastHealth) > 0.1F
               || Math.abs(maxHealth - this.lastMaxHealth) > 0.1F
               || Math.abs(stamina - this.lastStamina) > 0.1F
               || Math.abs(maxStamina - this.lastMaxStamina) > 0.1F
               || Math.abs(distance - this.lastDistance) > 5
               || online != this.lastOnline
               || !name.equals(this.lastName);
      }

      void update(float health, float maxHealth, float stamina, float maxStamina, int distance, boolean online,
            String name) {
         this.lastHealth = health;
         this.lastMaxHealth = maxHealth;
         this.lastStamina = stamina;
         this.lastMaxStamina = maxStamina;
         this.lastDistance = distance;
         this.lastOnline = online;
         this.lastName = name;
      }
   }

   private static class ViewerHudState {
      final UUID viewerUuid;
      String currentPartyId;
      boolean hudVisible = false;
      PartyMemberHud hudInstance;
      final Map<UUID, PartyPlayerListHud.MemberHudState> memberStates = new ConcurrentHashMap<>();

      ViewerHudState(@Nonnull UUID viewerUuid) {
         this.viewerUuid = viewerUuid;
      }
   }
}
