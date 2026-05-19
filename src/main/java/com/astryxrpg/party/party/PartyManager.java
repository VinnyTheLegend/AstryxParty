package com.astryxrpg.party.party;

import com.astryxrpg.party.events.PartyCreateEvent;
import com.astryxrpg.party.events.PartyDisbandEvent;
import com.astryxrpg.party.events.PartyEventBus;
import com.astryxrpg.party.events.PartyJoinEvent;
import com.astryxrpg.party.events.PartyLeaveEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PartyManager {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private final Map<String, Party> parties = new ConcurrentHashMap<>();
   private final Map<UUID, String> playerPartyMap = new ConcurrentHashMap<>();
   private final Map<UUID, PartyInvite> pendingInvites = new ConcurrentHashMap<>();
   private final Map<String, List<PartyJoinRequest>> joinRequests = new ConcurrentHashMap<>();

    public PartyManager() {
       List<Party> loadedParties = PartyStorage.loadAllParties();
       ((Api)LOGGER.atInfo()).log("Loading %d parties from storage", loadedParties.size());

       for (Party party : loadedParties) {
          this.parties.put(party.getId(), party);
          party.getMemberUuids().forEach(uuid -> this.playerPartyMap.put(uuid, party.getId()));
          ((Api)LOGGER.atInfo()).log("Loaded party %s with %d members", party.getId(), party.getMemberCount());
       }
    }

   @Nullable
   public Party createParty(@Nonnull UUID leaderUuid) {
      String name = Party.getPlayerName(leaderUuid) + "'s Party";
      return this.createParty(leaderUuid, name);
   }

   @Nullable
   public Party createParty(@Nonnull UUID leaderUuid, @Nonnull String name) {
      ((Api)LOGGER.atInfo()).log("[DEBUG] createParty called for %s", leaderUuid);
      if (this.isInParty(leaderUuid)) {
         ((Api)LOGGER.atInfo()).log("[DEBUG] Player already in party, returning null");
         return null;
      }

      Party party = new Party(leaderUuid, name);
      this.parties.put(party.getId(), party);
      this.playerPartyMap.put(leaderUuid, party.getId());

      PartyStorage.saveParty(party);

      ((Api)LOGGER.atInfo()).log("[DEBUG] Firing PartyCreateEvent");
      PartyEventBus.fire(new PartyCreateEvent(party));
      ((Api)LOGGER.atInfo()).log("[DEBUG] PartyCreateEvent fired");
      return party;
   }

   public void disbandParty(@Nonnull String partyId) {
      Party party = this.parties.remove(partyId);
      if (party != null) {
         Set<UUID> formerMembers = Set.copyOf(party.getMemberUuids());
         UUID leaderUuid = party.getLeaderUuid();

         for (UUID memberUuid : formerMembers) {
            this.playerPartyMap.remove(memberUuid);
         }

         this.broadcastToParty(party, Message.raw("Party has been disbanded."));

         PartyStorage.deleteParty(partyId);

         PartyEventBus.fire(new PartyDisbandEvent(partyId, leaderUuid, formerMembers));
      }
   }

   @Nullable
   public Party getPartyByPlayer(@Nonnull UUID playerUuid) {
      String partyId = this.playerPartyMap.get(playerUuid);
      return partyId != null ? this.parties.get(partyId) : null;
   }

   public boolean isInParty(@Nonnull UUID playerUuid) {
      return this.playerPartyMap.containsKey(playerUuid);
   }

   @Nonnull
   public Collection<Party> getAllParties() {
      return this.parties.values();
   }

   public void addPlayerToPartyMap(@Nonnull UUID playerUuid, @Nonnull Party party) {
      this.playerPartyMap.put(playerUuid, party.getId());

      PartyStorage.addMember(party.getId(), playerUuid);
   }

   public boolean sendInvite(@Nonnull UUID inviterUuid, @Nonnull UUID inviteeUuid) {
      if (this.isInParty(inviteeUuid)) {
         return false;
      }

      Party party = this.getPartyByPlayer(inviterUuid);
      if (party == null) {
         party = this.createParty(inviterUuid);
      }

      if (party == null) {
         return false;
      }

      if (!party.isLeader(inviterUuid)) {
         PartyRole inviterRole = party.getRole(inviterUuid);
         if (inviterRole.getLevel() < PartyRole.MEMBER.getLevel()) {
            return false;
         }
      }

      PartyInvite invite = new PartyInvite(inviterUuid, inviteeUuid, party.getId(), 60);
      this.pendingInvites.put(inviteeUuid, invite);
      return true;
   }

   @Nullable
   public Party acceptInvite(@Nonnull UUID inviteeUuid) {
      PartyInvite invite = this.pendingInvites.remove(inviteeUuid);
      if (invite != null && !invite.isExpired()) {
         Party party = this.parties.get(invite.getPartyId());
         if (party == null) {
            return null;
         }

         party.addMember(inviteeUuid);
         this.playerPartyMap.put(inviteeUuid, party.getId());

         PartyStorage.addMember(party.getId(), inviteeUuid);

         String inviteeName = Party.getPlayerName(inviteeUuid);
         this.broadcastToParty(party, Message.raw(inviteeName + " joined the party."));
         PartyEventBus.fire(new PartyJoinEvent(party, inviteeUuid));
         return party;
      } else {
         return null;
      }
   }

   public boolean declineInvite(@Nonnull UUID inviteeUuid) {
      return this.pendingInvites.remove(inviteeUuid) != null;
   }

   @Nullable
   public PartyInvite getPendingInvite(@Nonnull UUID inviteeUuid) {
      PartyInvite invite = this.pendingInvites.get(inviteeUuid);
      if (invite != null && invite.isExpired()) {
         this.pendingInvites.remove(inviteeUuid);
         return null;
      } else {
         return invite;
      }
   }

   public boolean leaveParty(@Nonnull UUID playerUuid, boolean wasKicked) {
      String partyId = this.playerPartyMap.get(playerUuid);
      if (partyId == null) {
         return false;
      }

      Party party = this.parties.get(partyId);
      if (party == null) {
         this.playerPartyMap.remove(playerUuid);
         return false;
      }

      String playerName = Party.getPlayerName(playerUuid);
      if (party.isLeader(playerUuid)) {
         UUID newLeader = party.getNextLeaderCandidate();
         if (newLeader == null) {
            this.disbandParty(partyId);
            return true;
         }

         party.transferLeadership(newLeader);
         party.removeMember(playerUuid);
         this.playerPartyMap.remove(playerUuid);
         this.broadcastToParty(party, Message.raw(playerName + " left the party."));

         PartyStorage.updateLeader(partyId, newLeader);
         PartyStorage.removeMember(partyId, playerUuid);

         PartyEventBus.fire(new PartyLeaveEvent(party, partyId, playerUuid, wasKicked));
      } else {
         party.removeMember(playerUuid);
         this.playerPartyMap.remove(playerUuid);
         String message = wasKicked ? playerName + " was kicked from the party." : playerName + " left the party.";
         this.broadcastToParty(party, Message.raw(message));

         PartyStorage.removeMember(partyId, playerUuid);

         PartyEventBus.fire(new PartyLeaveEvent(party, partyId, playerUuid, wasKicked));
      }

      if (wasKicked) {
         Party.sendMessageToPlayer(playerUuid, Message.raw("You have been kicked from the party."));
      }

      return true;
   }

   public boolean kickPlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
      Party party = this.getPartyByPlayer(actorUuid);
      if (party != null && !party.isMember(targetUuid)) {
         if (!party.isLeader(actorUuid)) {
            PartyRole actorRole = party.getRole(actorUuid);
            PartyRole targetRole = party.getRole(targetUuid);
            if (!actorRole.canKick(targetRole)) {
               return false;
            }
         }

         return this.leaveParty(targetUuid, true);
      } else {
         return false;
      }
   }

   public boolean promotePlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
      Party party = this.getPartyByPlayer(actorUuid);
      if (party == null || party.isMember(targetUuid)) {
         return false;
      }

      if (!party.isLeader(actorUuid)) {
         return false;
      }

      if (party.isLeader(targetUuid)) {
         return false;
      }

      boolean promoted = party.promote(targetUuid);
      if (promoted) {
         PartyStorage.updateMemberRole(party.getId(), targetUuid, party.getRole(targetUuid));

         String targetName = Party.getPlayerName(targetUuid);
         this.broadcastToParty(party, Message.raw(targetName + " was promoted to " + party.getRoleDisplayName(targetUuid) + "."));
      }

      return promoted;
   }

   public boolean demotePlayer(@Nonnull UUID actorUuid, @Nonnull UUID targetUuid) {
      Party party = this.getPartyByPlayer(actorUuid);
      if (party == null || party.isMember(targetUuid)) {
         return false;
      }

      if (!party.isLeader(actorUuid)) {
         return false;
      }

      if (party.isLeader(targetUuid)) {
         return false;
      }

      boolean demoted = party.demote(targetUuid);
      if (demoted) {
         PartyStorage.updateMemberRole(party.getId(), targetUuid, party.getRole(targetUuid));

         String targetName = Party.getPlayerName(targetUuid);
         this.broadcastToParty(party, Message.raw(targetName + " was demoted to " + party.getRoleDisplayName(targetUuid) + "."));
      }

      return demoted;
   }

   public boolean transferLeadership(@Nonnull UUID leaderUuid, @Nonnull UUID newLeaderUuid) {
      Party party = this.getPartyByPlayer(leaderUuid);
      if (party == null || !party.isLeader(leaderUuid)) {
         return false;
      }

      if (!party.isMember(newLeaderUuid) && !party.isLeader(newLeaderUuid)) {
         party.transferLeadership(newLeaderUuid);

         PartyStorage.updateLeader(party.getId(), newLeaderUuid);
         PartyStorage.updateMemberRole(party.getId(), leaderUuid, party.getRole(leaderUuid));

         String newLeaderName = Party.getPlayerName(newLeaderUuid);
         this.broadcastToParty(party, Message.raw(newLeaderName + " is now the party leader."));
         return true;
      } else {
         return false;
      }
   }

   public void broadcastToParty(@Nonnull Party party, @Nonnull Message message) {
      for (UUID memberUuid : party.getMemberUuids()) {
         PlayerRef playerRef = Universe.get().getPlayer(memberUuid);
         if (playerRef != null) {
            playerRef.sendMessage(message);
         }
      }
   }

   @Nonnull
   public List<Party> getPublicParties() {
      return this.parties.values().stream().filter(p -> p.getAccessType().isPubliclyVisible()).filter(p -> !p.isFull()).toList();
   }

   @Nullable
   public Party getPartyById(@Nonnull String partyId) {
      return this.parties.get(partyId);
   }

   public boolean joinOpenParty(@Nonnull UUID playerUuid, @Nonnull String partyId) {
      if (this.isInParty(playerUuid)) {
         return false;
      }

      Party party = this.parties.get(partyId);
      if (party == null) {
         return false;
      }

      if (party.getAccessType() != PartyAccessType.OPEN) {
         return false;
      }

      if (party.isFull()) {
         return false;
      }

      party.addMember(playerUuid);
      this.playerPartyMap.put(playerUuid, party.getId());

      PartyStorage.addMember(party.getId(), playerUuid);

      String playerName = Party.getPlayerName(playerUuid);
      this.broadcastToParty(party, Message.raw(playerName + " joined the party."));
      PartyEventBus.fire(new PartyJoinEvent(party, playerUuid));
      return true;
   }

   public boolean joinWithPassword(@Nonnull UUID playerUuid, @Nonnull String partyId, @Nonnull String password) {
      if (this.isInParty(playerUuid)) {
         return false;
      }

      Party party = this.parties.get(partyId);
      if (party == null) {
         return false;
      }

      if (party.getAccessType() != PartyAccessType.PASSWORDED) {
         return false;
      }

      if (party.isFull()) {
         return false;
      }

      if (!party.checkPassword(password)) {
         return false;
      }

      party.addMember(playerUuid);
      this.playerPartyMap.put(playerUuid, party.getId());

      PartyStorage.addMember(party.getId(), playerUuid);

      String playerName = Party.getPlayerName(playerUuid);
      this.broadcastToParty(party, Message.raw(playerName + " joined the party."));
      PartyEventBus.fire(new PartyJoinEvent(party, playerUuid));
      return true;
   }

   public boolean sendJoinRequest(@Nonnull UUID playerUuid, @Nonnull String partyId) {
      if (this.isInParty(playerUuid)) {
         return false;
      }

      Party party = this.parties.get(partyId);
      if (party == null) {
         return false;
      }

      if (party.getAccessType() != PartyAccessType.REQUEST_ONLY) {
         return false;
      }

      if (party.isFull()) {
         return false;
      }

      List<PartyJoinRequest> requests = this.joinRequests.computeIfAbsent(partyId, k -> new ArrayList<>());
      boolean alreadyRequested = requests.stream().anyMatch(r -> r.getRequesterUuid().equals(playerUuid) && !r.isExpired());
      if (alreadyRequested) {
         return false;
      }

      PartyJoinRequest request = new PartyJoinRequest(playerUuid, partyId);
      requests.add(request);

      PartyStorage.saveJoinRequest(request);

      PlayerRef leaderRef = Universe.get().getPlayer(party.getLeaderUuid());
      if (leaderRef != null) {
         String requesterName = Party.getPlayerName(playerUuid);
         leaderRef.sendMessage(Message.raw(requesterName + " wants to join your party."));
      }

      return true;
   }

   public boolean acceptJoinRequest(@Nonnull UUID leaderUuid, @Nonnull UUID requesterUuid) {
      Party party = this.getPartyByPlayer(leaderUuid);
      if (party == null || !party.isLeader(leaderUuid)) {
         return false;
      }

      if (party.isFull()) {
         return false;
      }

      if (this.isInParty(requesterUuid)) {
         return false;
      }

      List<PartyJoinRequest> requests = this.joinRequests.get(party.getId());
      if (requests == null) {
         return false;
      }

      PartyJoinRequest request = requests.stream().filter(r -> r.getRequesterUuid().equals(requesterUuid) && !r.isExpired()).findFirst().orElse(null);
      if (request == null) {
         return false;
      }

      requests.remove(request);

      PartyStorage.deleteJoinRequest(party.getId(), requesterUuid);

      party.addMember(requesterUuid);
      this.playerPartyMap.put(requesterUuid, party.getId());

      PartyStorage.addMember(party.getId(), requesterUuid);

      String requesterName = Party.getPlayerName(requesterUuid);
      this.broadcastToParty(party, Message.raw(requesterName + " joined the party."));
      Party.sendMessageToPlayer(requesterUuid, Message.raw("Your join request was accepted!"));
      PartyEventBus.fire(new PartyJoinEvent(party, requesterUuid));
      return true;
   }

   public boolean declineJoinRequest(@Nonnull UUID leaderUuid, @Nonnull UUID requesterUuid) {
      Party party = this.getPartyByPlayer(leaderUuid);
      if (party != null && party.isLeader(leaderUuid)) {
         List<PartyJoinRequest> requests = this.joinRequests.get(party.getId());
         if (requests == null) {
            return false;
         }

         boolean removed = requests.removeIf(r -> r.getRequesterUuid().equals(requesterUuid));
         if (removed) {
            PartyStorage.deleteJoinRequest(party.getId(), requesterUuid);

            Party.sendMessageToPlayer(requesterUuid, Message.raw("Your join request was declined."));
         }

         return removed;
      } else {
         return false;
      }
   }

   public boolean updatePartySettings(
      @Nonnull UUID leaderUuid, @Nullable String name, @Nullable String password, @Nullable PartyAccessType accessType, @Nullable Integer maxMembers
   ) {
      Party party = this.getPartyByPlayer(leaderUuid);
      if (party != null && party.isLeader(leaderUuid)) {
         if (name != null && !name.isBlank()) {
            party.setName(name);
         }

         if (password != null) {
            party.setPassword(password.isEmpty() ? null : password);
         }

         if (accessType != null) {
            party.setAccessType(accessType);
         }

         if (maxMembers != null) {
            party.setMaxMembers(maxMembers);
         }

         PartyStorage.updatePartySettings(party);

         return true;
      } else {
         return false;
      }
   }

   @Nonnull
   public List<PartyJoinRequest> getJoinRequests(@Nonnull String partyId) {
      List<PartyJoinRequest> requests = this.joinRequests.get(partyId);
      if (requests == null) {
         return List.of();
      }

      requests.removeIf(PartyJoinRequest::isExpired);
      return new ArrayList<>(requests);
   }

   public boolean hasPendingJoinRequest(@Nonnull UUID playerUuid, @Nonnull String partyId) {
      List<PartyJoinRequest> requests = this.joinRequests.get(partyId);
      return requests == null ? false : requests.stream().anyMatch(r -> r.getRequesterUuid().equals(playerUuid) && !r.isExpired());
   }
}
