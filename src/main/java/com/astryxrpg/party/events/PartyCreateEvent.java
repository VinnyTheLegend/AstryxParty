package com.astryxrpg.party.events;

import java.util.UUID;
import javax.annotation.Nonnull;

import com.astryxrpg.party.party.Party;

public class PartyCreateEvent extends PartyEvent {
   private final Party party;
   private final UUID leaderUuid;

   public PartyCreateEvent(@Nonnull Party party) {
      this.party = party;
      this.leaderUuid = party.getLeaderUuid();
   }

   @Nonnull
   @Override
   public Party getParty() {
      return this.party;
   }

   @Nonnull
   @Override
   public String getPartyId() {
      return this.party.getId();
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
}
