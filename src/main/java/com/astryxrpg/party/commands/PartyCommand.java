package com.astryxrpg.party.commands;

import com.astryxrpg.party.AstryxParty;
import com.astryxrpg.party.pages.PartyMenuPage;
import com.astryxrpg.party.party.FakeMember;
import com.astryxrpg.party.party.Party;
import com.astryxrpg.party.party.PartyInvite;
import com.astryxrpg.party.party.PartyManager;
import com.astryxrpg.party.party.PartyPlayerListHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;
import java.util.UUID;
import javax.annotation.Nonnull;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class PartyCommand extends AbstractPlayerCommand {
   private final AstryxParty plugin;
   private final PartyManager partyManager;
   private static int fakeCounter = 0;

   public PartyCommand(@Nonnull AstryxParty plugin) {
      super("party", "Party management commands");
      this.plugin = plugin;
      this.partyManager = plugin.getPartyManager();
      this.setAllowsExtraArguments(true);
   }

   protected boolean canGeneratePermission() {
      return false;
   }

   protected void execute(
      @Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world
   ) {
      UUID playerUuid = playerRef.getUuid();
      String input = context.getInputString().trim();
      String[] parts = input.split("\\s+");
      String subcommand = parts.length > 1 ? parts[1] : null;
      if (subcommand == null) {
         this.openPartyUI(store, ref, playerRef);
      } else {
         switch (subcommand.toLowerCase()) {
            case "create":
               this.handleCreate(context, store, ref, playerRef, playerUuid, parts);
               break;
            case "invite":
               this.handleInvite(context, playerRef, playerUuid, parts);
               break;
            case "accept":
               this.handleAccept(context, playerRef, playerUuid);
               break;
            case "decline":
               this.handleDecline(context, playerRef, playerUuid);
               break;
            case "leave":
               this.handleLeave(context, playerRef, playerUuid);
               break;
            case "kick":
               this.handleKick(context, playerRef, playerUuid, parts);
               break;
            case "disband":
               this.handleDisband(context, playerRef, playerUuid);
               break;
            case "list":
               this.handleList(context, playerRef, playerUuid);
               break;
            case "debug":
               this.handleDebug(context, store, ref, playerRef, playerUuid, parts);
               break;
            default:
               this.showUsage(context);
         }
      }
   }

   private void showUsage(@Nonnull CommandContext context) {
      context.sendMessage(
         Message.raw(
            "Usage:\n/party - Show party info\n/party invite <player> - Invite a player\n/party accept - Accept pending invite\n/party decline - Decline pending invite\n/party leave - Leave current party\n/party kick <player> - Kick a player (leader only)\n/party disband - Disband the party (leader only)\n/party list - List party members"
         )
      );
   }

   private void showPartyInfo(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid) {
      Party party = this.partyManager.getPartyByPlayer(playerUuid);
      PartyInvite pendingInvite = this.partyManager.getPendingInvite(playerUuid);
      StringBuilder sb = new StringBuilder();
      sb.append("=== Party Menu ===\n");
      if (pendingInvite != null) {
         PlayerRef inviterRef = Universe.get().getPlayer(pendingInvite.getInviterUuid());
         String inviterName = inviterRef != null ? inviterRef.getUsername() : "Unknown";
         sb.append("Pending invite from: ").append(inviterName).append("\n");
         sb.append("  /party accept - Accept invite\n");
         sb.append("  /party decline - Decline invite\n\n");
      }

      if (party != null) {
         sb.append("Your Party:\n");

         for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            String memberName = memberRef != null ? memberRef.getUsername() : memberUuid.toString().substring(0, 8);
            String leaderTag = party.isLeader(memberUuid) ? " [Leader]" : "";
            String onlineTag = memberRef != null ? "" : " (Offline)";
            sb.append("  - ").append(memberName).append(leaderTag).append(onlineTag).append("\n");
         }

         sb.append("\nCommands:\n");
         sb.append("  /party leave - Leave party\n");
         if (party.isLeader(playerUuid)) {
            sb.append("  /party kick <player> - Kick player\n");
            sb.append("  /party disband - Disband party\n");
         }
      } else if (pendingInvite == null) {
         sb.append("You are not in a party.\n\n");
         sb.append("Commands:\n");
         sb.append("  /party invite <player> - Invite a player\n");
      }

      context.sendMessage(Message.raw(sb.toString()));
   }

   private void handleInvite(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid, @Nonnull String[] parts) {
      if (parts.length < 3) {
         context.sendMessage(Message.raw("Usage: /party invite <player>"));
      } else {
         String targetName = parts[2];
         PlayerRef targetRef = this.findPlayerByName(targetName);
         if (targetRef == null) {
            context.sendMessage(Message.raw("Player not found: " + targetName));
         } else {
            UUID targetUuid = targetRef.getUuid();
            if (this.partyManager.isInParty(targetUuid)) {
               context.sendMessage(Message.raw("That player is already in a party."));
            } else {
               boolean sent = this.partyManager.sendInvite(playerUuid, targetUuid);
               if (sent) {
                  context.sendMessage(Message.raw("Invitation sent to " + targetRef.getUsername()));
                  targetRef.sendMessage(Message.raw(playerRef.getUsername() + " has invited you to their party. Use /party accept to join."));
               } else {
                  context.sendMessage(Message.raw("Failed to send invite."));
               }
            }
         }
      }
   }

   private void handleAccept(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid) {
      PartyInvite invite = this.partyManager.getPendingInvite(playerUuid);
      if (invite == null) {
         context.sendMessage(Message.raw("You have no pending party invitation."));
      } else {
         Party party = this.partyManager.acceptInvite(playerUuid);
         if (party != null) {
            PlayerRef inviterRef = Universe.get().getPlayer(invite.getInviterUuid());
            String inviterName = inviterRef != null ? inviterRef.getUsername() : "Unknown";
            context.sendMessage(Message.raw("You joined " + inviterName + "'s party!"));
         } else {
            context.sendMessage(Message.raw("The party invitation has expired."));
         }
      }
   }

   private void handleDecline(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid) {
      boolean declined = this.partyManager.declineInvite(playerUuid);
      if (declined) {
         context.sendMessage(Message.raw("You declined the party invitation."));
      } else {
         context.sendMessage(Message.raw("You have no pending party invitation."));
      }
   }

   private void handleLeave(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid) {
      if (!this.partyManager.isInParty(playerUuid)) {
         context.sendMessage(Message.raw("You are not in a party."));
      } else {
         this.partyManager.leaveParty(playerUuid, false);
         context.sendMessage(Message.raw("You left the party."));
      }
   }

   private void handleKick(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid, @Nonnull String[] parts) {
      if (parts.length < 3) {
         context.sendMessage(Message.raw("Usage: /party kick <player>"));
      } else {
         String targetName = parts[2];
         Party party = this.partyManager.getPartyByPlayer(playerUuid);
         if (party == null) {
            context.sendMessage(Message.raw("You are not in a party."));
         } else if (!party.isLeader(playerUuid)) {
            context.sendMessage(Message.raw("Only the party leader can kick players."));
         } else {
            PlayerRef targetRef = this.findPlayerByName(targetName);
            if (targetRef == null) {
               context.sendMessage(Message.raw("Player not found: " + targetName));
            } else {
               boolean kicked = this.partyManager.kickPlayer(playerUuid, targetRef.getUuid());
               if (!kicked) {
                  context.sendMessage(Message.raw("Could not kick that player."));
               }
            }
         }
      }
   }

   private void handleDisband(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid) {
      Party party = this.partyManager.getPartyByPlayer(playerUuid);
      if (party == null) {
         context.sendMessage(Message.raw("You are not in a party."));
      } else if (!party.isLeader(playerUuid)) {
         context.sendMessage(Message.raw("Only the party leader can disband the party."));
      } else {
         this.partyManager.disbandParty(party.getId());
         context.sendMessage(Message.raw("Party disbanded."));
      }
   }

   private void handleList(@Nonnull CommandContext context, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid) {
      Party party = this.partyManager.getPartyByPlayer(playerUuid);
      if (party == null) {
         context.sendMessage(Message.raw("You are not in a party."));
      } else {
         StringBuilder sb = new StringBuilder("Party Members:\n");

         for (UUID memberUuid : party.getMemberUuids()) {
            PlayerRef memberRef = Universe.get().getPlayer(memberUuid);
            String name = memberRef != null ? memberRef.getUsername() : memberUuid.toString();
            String leaderTag = party.isLeader(memberUuid) ? " [Leader]" : "";
            String onlineTag = memberRef != null ? "" : " (Offline)";
            sb.append("- ").append(name).append(leaderTag).append(onlineTag).append("\n");
         }

         context.sendMessage(Message.raw(sb.toString()));
      }
   }

   private PlayerRef findPlayerByName(@Nonnull String name) {
      for (PlayerRef player : Universe.get().getPlayers()) {
         if (player.getUsername().equalsIgnoreCase(name)) {
            return player;
         }
      }

      return null;
   }

   private void openPartyUI(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
      Player playerComponent = (Player)store.getComponent(ref, Player.getComponentType());
      if (playerComponent != null) {
         PartyMenuPage menuUI = new PartyMenuPage(playerRef, this.plugin);
         playerComponent.getPageManager().openCustomPage(ref, store, menuUI);
      }
   }

   private void handleCreate(
      @Nonnull CommandContext context,
      @Nonnull Store<EntityStore> store,
      @Nonnull Ref<EntityStore> ref,
      @Nonnull PlayerRef playerRef,
      @Nonnull UUID playerUuid,
      @Nonnull String[] parts
   ) {
      if (this.partyManager.isInParty(playerUuid)) {
         context.sendMessage(Message.raw("You are already in a party."));
      } else {
         Party party = this.partyManager.createParty(playerUuid);
         if (party != null) {
            context.sendMessage(Message.raw("Party created!"));
            this.openPartyUI(store, ref, playerRef);
         } else {
            context.sendMessage(Message.raw("Failed to create party."));
         }
      }
   }

   private void handleDebug(
      @Nonnull CommandContext context,
      @Nonnull Store<EntityStore> store,
      @Nonnull Ref<EntityStore> ref,
      @Nonnull PlayerRef playerRef,
      @Nonnull UUID playerUuid,
      @Nonnull String[] parts
   ) {
      if (!context.sender().hasPermission("*")) {
         context.sendMessage(Message.raw("You must be an operator to use debug commands."));
      } else {
         String debugAction = parts.length > 2 ? parts[2].toLowerCase() : "help";
         switch (debugAction) {
            case "addbot":
               this.handleDebugAddBot(context, store, ref, playerRef, playerUuid);
               break;
            case "removebot":
            case "removebots":
               this.handleDebugRemoveBots(context, store, playerUuid);
               break;
            case "hud":
               this.handleDebugHud(context, parts);
               break;
            default:
               this.showDebugUsage(context);
         }
      }
   }

   private void showDebugUsage(@Nonnull CommandContext context) {
      context.sendMessage(
         Message.raw(
            "Debug Commands:\n/party debug addbot - Add a fake party member (for testing compass markers)\n/party debug removebot - Remove all fake party members\n/party debug hud min <1|2> - Set minimum members for HUD visibility"
         )
      );
   }

   private void handleDebugHud(@Nonnull CommandContext context, @Nonnull String[] parts) {
      if (parts.length < 4) {
         context.sendMessage(Message.raw("Usage: /party debug hud min <1|2>"));
         context.sendMessage(Message.raw("Current minimum: " + PartyPlayerListHud.getMinMembersForHud()));
      } else {
         String subCommand = parts[3].toLowerCase();
         if ("min".equals(subCommand)) {
            if (parts.length < 5) {
               context.sendMessage(Message.raw("Current HUD minimum members: " + PartyPlayerListHud.getMinMembersForHud()));
               return;
            }

            try {
               int minMembers = Integer.parseInt(parts[4]);
               if (minMembers < 1) {
                  context.sendMessage(Message.raw("Minimum must be at least 1."));
                  return;
               }

               PartyPlayerListHud.setMinMembersForHud(minMembers);
               context.sendMessage(Message.raw("HUD minimum members set to " + minMembers + "."));
            } catch (NumberFormatException e) {
               context.sendMessage(Message.raw("Invalid number. Usage: /party debug hud min <1|2>"));
            }
         } else {
            context.sendMessage(Message.raw("Usage: /party debug hud min <1|2>"));
         }
      }
   }

   private void handleDebugAddBot(
      @Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull UUID playerUuid
   ) {
      Party party = this.partyManager.getPartyByPlayer(playerUuid);
      if (party == null) {
         party = this.partyManager.createParty(playerUuid);
         if (party == null) {
            context.sendMessage(Message.raw("Failed to create party."));
            return;
         }

         context.sendMessage(Message.raw("Party created automatically."));
      }

Player playerComponent = (Player)store.getComponent(ref, Player.getComponentType());
       if (playerComponent == null) {
          context.sendMessage(Message.raw("Could not get player position."));
       } else {
          TransformComponent transformComponent = (TransformComponent)store.getComponent(ref, TransformComponent.getComponentType());
         if (transformComponent == null) {
            context.sendMessage(Message.raw("Could not get player transform."));
         } else {
            double px = transformComponent.getTransform().getPosition().x();
            double py = transformComponent.getTransform().getPosition().y();
            double pz = transformComponent.getTransform().getPosition().z();
            String fakeName = "FakePartyMember_" + fakeCounter++;
            double spawnX = px + 10.0;
            double spawnY = py;
            double spawnZ = pz + 10.0;
            FakeMember fakeMember = new FakeMember(fakeName, spawnX, spawnY, spawnZ);
            String npcType = "Kweebec_Sproutling";

            try {
               NPCPlugin npcPlugin = NPCPlugin.get();
               if (npcPlugin != null) {
                  Vector3dc spawnPos = new Vector3d(spawnX, spawnY, spawnZ);
                  Rotation3fc rotation = Rotation3f.IDENTITY;
                  Pair<Ref<EntityStore>, INonPlayerCharacter> npcPair = npcPlugin.spawnNPC(store, npcType, null, spawnPos, rotation);
                  if (npcPair != null) {
                     Ref<EntityStore> npcRef = (Ref<EntityStore>)npcPair.first();
                     fakeMember.setEntityRef(npcRef);
                     fakeMember.setNpcType(npcType);
                     Nameplate nameplate = (Nameplate)store.getComponent(npcRef, Nameplate.getComponentType());
                     if (nameplate != null) {
                        nameplate.setText(fakeName);
                     } else {
                        store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(fakeName));
                     }

                     context.sendMessage(
                        Message.raw("Spawned visible NPC '" + fakeName + "' at (" + (int)spawnX + ", " + (int)spawnY + ", " + (int)spawnZ + ").")
                     );
                  } else {
                     context.sendMessage(Message.raw("Could not spawn NPC (type '" + npcType + "' not found). Using marker-only mode."));
                  }
               }
            } catch (Exception e) {
               context.sendMessage(Message.raw("NPC spawning failed: " + e.getMessage() + ". Using marker-only mode."));
            }

            party.addFakeMember(fakeMember);
            context.sendMessage(Message.raw("Added fake party member '" + fakeName + "'. Check your compass!"));
         }
      }
   }

   private void handleDebugRemoveBots(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull UUID playerUuid) {
      Party party = this.partyManager.getPartyByPlayer(playerUuid);
      if (party == null) {
         context.sendMessage(Message.raw("You are not in a party."));
      } else if (!party.hasFakeMembers()) {
         context.sendMessage(Message.raw("No fake members to remove."));
      } else {
         int count = party.getFakeMembers().size();
         int entitiesRemoved = 0;
         PartyPlayerListHud.getInstance().removeFakeMembersFromHud(party);

         for (FakeMember fakeMember : party.getFakeMembers().values()) {
            if (fakeMember.hasEntity()) {
               try {
                  Ref<EntityStore> entityRef = fakeMember.getEntityRef();
                  if (entityRef != null && entityRef.isValid()) {
                     store.removeEntity(entityRef, RemoveReason.REMOVE);
                     entitiesRemoved++;
                  }
               } catch (Exception var10) {
               }
            }
         }

         party.clearFakeMembers();
         String message = "Removed " + count + " fake member(s)";
         if (entitiesRemoved > 0) {
            message = message + " and " + entitiesRemoved + " NPC entity(ies)";
         }

         context.sendMessage(Message.raw(message + "."));
      }
   }
}
