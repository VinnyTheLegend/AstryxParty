package com.astryxrpg.party.events;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.astryxrpg.party.party.Party;

public abstract class PartyEvent {
   private final long timestamp = System.currentTimeMillis();

   protected PartyEvent() {
   }

   @Nullable
   public abstract Party getParty();

   @Nonnull
   public abstract String getPartyId();

   @Nonnull
   public abstract UUID getPlayerUuid();

   public long getTimestamp() {
      return this.timestamp;
   }
}
