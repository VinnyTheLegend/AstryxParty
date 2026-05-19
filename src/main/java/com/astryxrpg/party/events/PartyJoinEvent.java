package com.astryxrpg.party.events;

import java.util.UUID;
import javax.annotation.Nonnull;

import com.astryxrpg.party.party.Party;

public class PartyJoinEvent extends PartyEvent {
   private final Party party;
   private final UUID joiningPlayerUuid;

   public PartyJoinEvent(@Nonnull Party party, @Nonnull UUID joiningPlayerUuid) {
      this.party = party;
      this.joiningPlayerUuid = joiningPlayerUuid;
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
      return this.joiningPlayerUuid;
   }

   @Nonnull
   public UUID getJoiningPlayerUuid() {
      return this.joiningPlayerUuid;
   }
}
