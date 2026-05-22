package com.astryxrpg.party.markers;

import com.astryxrpg.party.AstryxParty;
import com.astryxrpg.party.party.FakeMember;
import com.astryxrpg.party.party.Party;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PositionUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PartyMarkerTicker extends TickingSystem<EntityStore> {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private static final PartyMarkerTicker INSTANCE = new PartyMarkerTicker();
   private float accumulator = 0.0F;
   private static final float UPDATE_INTERVAL = 0.1F;
   private static final double POSITION_THRESHOLD = 5.0;
   private static final double POSITION_THRESHOLD_SQ = 25.0;
   private static final float YAW_THRESHOLD = 0.05F;
   private final Map<UUID, Map<String, PartyMarkerTicker.MarkerState>> displayedMarkers = new ConcurrentHashMap<>();

   public static PartyMarkerTicker getInstance() {
      return INSTANCE;
   }

   public void tick(float dt, int index, Store<EntityStore> store) {
      this.accumulator += dt;
      if (this.accumulator >= 0.1F) {
         this.accumulator = 0.0F;
         this.updateAllPartyMarkers(store);
      }
   }

   private void updateAllPartyMarkers(Store<EntityStore> store) {
      World currentWorld = ((EntityStore)store.getExternalData()).getWorld();
      if (currentWorld != null) {
         for (Player viewer : currentWorld.getPlayers()) {
            try {
               PlayerRef viewerRef = Universe.get().getPlayer(viewer.getUuid());
               if (viewerRef != null) {
                  this.updateMarkersForPlayer(viewerRef, viewer, store);
               }
            } catch (Exception var6) {
            }
         }
      }
   }

   private void updateMarkersForPlayer(PlayerRef viewerRef, Player viewer, Store<EntityStore> store) {
      UUID viewerUuid = viewerRef.getUuid();
      World viewerWorld = viewer.getWorld();
      if (viewerWorld != null) {
         Party party = AstryxParty.getInstance().getPartyManager().getPartyByPlayer(viewerUuid);
         if (party == null) {
            viewer.getWorldMapTracker().setPlayerMapFilter(null);
            this.removeAllMarkersForPlayer(viewerUuid, viewerRef);
         } else {
viewer.getWorldMapTracker().setPlayerMapFilter(otherPlayer -> party.isMember(otherPlayer.getUuid()));
             TransformComponent viewerTransform = (TransformComponent) store.getComponent(viewerRef.getReference(), TransformComponent.getComponentType());
            if (viewerTransform != null) {
               double viewerX = viewerTransform.getTransform().getPosition().getX();
               double viewerY = viewerTransform.getTransform().getPosition().getY();
               double viewerZ = viewerTransform.getTransform().getPosition().getZ();
               Map<String, PartyMarkerTicker.MarkerState> viewerMarkerStates = this.displayedMarkers
                  .computeIfAbsent(viewerUuid, k -> new ConcurrentHashMap<>());
               Set<String> currentMarkerIds = new HashSet<>();
               List<MapMarker> markersToSend = new ArrayList<>();

for (UUID memberUuid : party.getMembersExcept(viewerUuid)) {
                   PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
                   if (memberRef != null) {
Player memberPlayer = this.getMemberPlayerSafe(memberRef, viewerWorld, store);
                      if (memberPlayer != null) {
                         TransformComponent transform = (TransformComponent) store.getComponent(memberRef.getReference(), TransformComponent.getComponentType());
                        if (transform != null) {
                           double memberX = transform.getTransform().getPosition().getX();
                           double memberY = transform.getTransform().getPosition().getY();
                           double memberZ = transform.getTransform().getPosition().getZ();
                           float memberYaw = transform.getTransform().getRotation().getYaw();
                           double dx = memberX - viewerX;
                           double dy = memberY - viewerY;
                           double dz = memberZ - viewerZ;
                           int distance = (int)Math.sqrt(dx * dx + dy * dy + dz * dz);
                           String markerId = "party-member-" + memberUuid.toString();
                           currentMarkerIds.add(markerId);
                           String markerName = memberRef.getUsername() + " (" + distance + "m)";
                           PartyMarkerTicker.MarkerState existingState = viewerMarkerStates.get(markerId);
                           boolean needsUpdate = existingState == null || existingState.needsUpdate(markerName, memberX, memberY, memberZ, memberYaw);
                           if (needsUpdate) {
                              if (existingState == null) {
                                 viewerMarkerStates.put(markerId, new PartyMarkerTicker.MarkerState(markerId, markerName, memberX, memberY, memberZ, memberYaw));
                              } else {
                                 existingState.update(markerName, memberX, memberY, memberZ, memberYaw);
                              }

                              // MapMarker marker = new MapMarker(
                              //    markerId, markerName, "PartyMember.png", PositionUtil.toTransformPacket(transform.getTransform()), null
                              // );
                              // markersToSend.add(marker);
                           }
                        }
                     }
                  }
               }

               for (FakeMember fakeMember : party.getFakeMembers().values()) {
                  boolean moved = fakeMember.updateMovement();
                  double memberX = fakeMember.getX();
                  double memberY = fakeMember.getY();
                  double memberZ = fakeMember.getZ();
                  float memberYaw = fakeMember.getYaw();
                  if (moved && fakeMember.hasEntity()) {
                     try {
                        Ref<EntityStore> entityRef = fakeMember.getEntityRef();
                        if (entityRef != null && entityRef.isValid()) {
                           TransformComponent npcTransform = (TransformComponent)store.getComponent(entityRef, TransformComponent.getComponentType());
                           if (npcTransform != null) {
                              Vector3d npcPosition = npcTransform.getPosition();
                              npcPosition.x = memberX;
                              npcPosition.y = memberY;
                              npcPosition.z = memberZ;
                           }
                        }
                     } catch (Exception var41) {
                     }
                  }

                  double dx = memberX - viewerX;
                  double dy = memberY - viewerY;
                  double dz = memberZ - viewerZ;
                  int distance = (int)Math.sqrt(dx * dx + dy * dy + dz * dz);
                  String markerId = "party-fake-" + fakeMember.getUuid().toString();
                  currentMarkerIds.add(markerId);
                  String markerName = fakeMember.getName() + " (" + distance + "m)";
                  PartyMarkerTicker.MarkerState existingState = viewerMarkerStates.get(markerId);
                  boolean needsUpdate = existingState == null || existingState.needsUpdate(markerName, memberX, memberY, memberZ, memberYaw);
                  if (needsUpdate) {
                     if (existingState == null) {
                        viewerMarkerStates.put(markerId, new PartyMarkerTicker.MarkerState(markerId, markerName, memberX, memberY, memberZ, memberYaw));
                     } else {
                        existingState.update(markerName, memberX, memberY, memberZ, memberYaw);
                     }

                     Transform fakeTransform = new Transform(memberX, memberY, memberZ);
                     // MapMarker marker = new MapMarker(markerId, markerName, "PartyMember.png", PositionUtil.toTransformPacket(fakeTransform), null);
                     // markersToSend.add(marker);
                  }
               }

               List<String> markersToRemove = new ArrayList<>();

               for (String existingMarkerId : viewerMarkerStates.keySet()) {
                  if (!currentMarkerIds.contains(existingMarkerId)) {
                     markersToRemove.add(existingMarkerId);
                  }
               }

               for (String removedId : markersToRemove) {
                  viewerMarkerStates.remove(removedId);
               }

               if (viewerMarkerStates.isEmpty()) {
                  this.displayedMarkers.remove(viewerUuid);
               }

               if (!markersToSend.isEmpty() || !markersToRemove.isEmpty()) {
                  this.sendUpdateWorldMapPacket(viewerRef, markersToSend, markersToRemove);
               }
            }
         }
      }
   }

private Player getMemberPlayerSafe(PlayerRef memberRef, World viewerWorld, Store<EntityStore> store) {
       Player memberPlayer = (Player)store.getComponent(memberRef.getReference(), Player.getComponentType());
       if (memberPlayer == null) {
          return null;
       }

       World memberWorld = memberPlayer.getWorld();
       return memberWorld != null && viewerWorld.equals(memberWorld) ? memberPlayer : null;
    }

   private void removeAllMarkersForPlayer(UUID playerUuid, PlayerRef playerRef) {
      Map<String, PartyMarkerTicker.MarkerState> existingMarkerStates = this.displayedMarkers.remove(playerUuid);
      if (existingMarkerStates != null && !existingMarkerStates.isEmpty() && playerRef != null) {
         this.sendUpdateWorldMapPacket(playerRef, Collections.emptyList(), new ArrayList<>(existingMarkerStates.keySet()));
      }
   }

   private void sendUpdateWorldMapPacket(PlayerRef playerRef, List<MapMarker> addedMarkers, List<String> removedMarkerIds) {
      try {
         PacketHandler handler = playerRef.getPacketHandler();
         if (handler == null) {
            return;
         }

         UpdateWorldMap packet = new UpdateWorldMap(null, addedMarkers.toArray(new MapMarker[0]), removedMarkerIds.toArray(new String[0]));
         handler.writePacket(packet, false);
      } catch (Exception var6) {
      }
   }

   public void onPlayerLeaveParty(UUID playerUuid, Party party) {
      if (party != null) {
         String markerId = "party-member-" + playerUuid.toString();

         for (UUID memberUuid : party.getMemberUuids()) {
            if (!memberUuid.equals(playerUuid)) {
               Map<String, PartyMarkerTicker.MarkerState> markerStates = this.displayedMarkers.get(memberUuid);
               if (markerStates != null) {
                  markerStates.remove(markerId);
               }

               PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
               if (memberRef != null) {
                  this.sendUpdateWorldMapPacket(memberRef, Collections.emptyList(), Collections.singletonList(markerId));
               }
            }
         }

         this.displayedMarkers.remove(playerUuid);
      }
   }

   private static class MarkerState {
      final String id;
      String name;
      double x;
      double y;
      double z;
      float yaw;

      MarkerState(String id, String name, double x, double y, double z, float yaw) {
         this.id = id;
         this.name = name;
         this.x = x;
         this.y = y;
         this.z = z;
         this.yaw = yaw;
      }

      boolean needsUpdate(String newName, double newX, double newY, double newZ, float newYaw) {
         if (!this.name.equals(newName)) {
            return true;
         }

         double dx = newX - this.x;
         double dy = newY - this.y;
         double dz = newZ - this.z;
         return dx * dx + dy * dy + dz * dz >= 25.0 ? true : Math.abs(newYaw - this.yaw) >= 0.05F;
      }

      void update(String newName, double newX, double newY, double newZ, float newYaw) {
         this.name = newName;
         this.x = newX;
         this.y = newY;
         this.z = newZ;
         this.yaw = newYaw;
      }
   }
}
