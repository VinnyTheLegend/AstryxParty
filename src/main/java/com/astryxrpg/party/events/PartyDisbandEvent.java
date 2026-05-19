package com.astryxrpg.party.events;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.astryxrpg.party.party.Party;

public class PartyDisbandEvent extends PartyEvent {
   private final String partyId;
   private final UUID leaderUuid;
   private final Set<UUID> formerMemberUuids;

   public PartyDisbandEvent(@Nonnull String partyId, @Nonnull UUID leaderUuid, @Nonnull Set<UUID> formerMemberUuids) {
      this.partyId = partyId;
      this.leaderUuid = leaderUuid;
      this.formerMemberUuids = Set.copyOf(formerMemberUuids);
   }

   @Nullable
   @Override
   public Party getParty() {
      return null;
   }

   @Nonnull
   @Override
   public String getPartyId() {
      return this.partyId;
   }

   @Nonnull
   @Override
   public UUID getPlayerUuid() {
      return this.leaderUuid;
   }

   @Nonnull
   public UUID getLeaderUuid() {
      return this.leaderUuid;
   }

   @Nonnull
   public Set<UUID> getFormerMemberUuids() {
      return this.formerMemberUuids;
   }
}
