package com.astryxrpg.party.events;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.astryxrpg.party.party.Party;

public class PartyLeaveEvent extends PartyEvent {
   private final Party party;
   private final String partyId;
   private final UUID leavingPlayerUuid;
   private final boolean wasKicked;

   public PartyLeaveEvent(@Nullable Party party, @Nonnull String partyId, @Nonnull UUID leavingPlayerUuid, boolean wasKicked) {
      this.party = party;
      this.partyId = partyId;
      this.leavingPlayerUuid = leavingPlayerUuid;
      this.wasKicked = wasKicked;
   }

   @Nullable
   @Override
   public Party getParty() {
      return this.party;
   }

   @Nonnull
   @Override
   public String getPartyId() {
      return this.partyId;
   }

   @Nonnull
   @Override
   public UUID getPlayerUuid() {
      return this.leavingPlayerUuid;
   }

   @Nonnull
   public UUID getLeavingPlayerUuid() {
      return this.leavingPlayerUuid;
   }

   public boolean wasKicked() {
      return this.wasKicked;
   }
}
