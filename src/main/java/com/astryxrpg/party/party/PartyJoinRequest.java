package com.astryxrpg.party.party;

import java.util.UUID;
import javax.annotation.Nonnull;

public class PartyJoinRequest {
   private static final int DEFAULT_TIMEOUT_SECONDS = 300;
   private final UUID requesterUuid;
   private final String partyId;
   private final long createdAt;
   private final long expiresAt;

   public PartyJoinRequest(@Nonnull UUID requesterUuid, @Nonnull String partyId) {
      this(requesterUuid, partyId, 300);
   }

   public PartyJoinRequest(@Nonnull UUID requesterUuid, @Nonnull String partyId, int timeoutSeconds) {
      this.requesterUuid = requesterUuid;
      this.partyId = partyId;
      this.createdAt = System.currentTimeMillis();
      this.expiresAt = this.createdAt + timeoutSeconds * 1000L;
   }

   public PartyJoinRequest(@Nonnull UUID requesterUuid, @Nonnull String partyId, long createdAt, long expiresAt) {
      this.requesterUuid = requesterUuid;
      this.partyId = partyId;
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
   }

   @Nonnull
   public UUID getRequesterUuid() {
      return this.requesterUuid;
   }

   @Nonnull
   public String getPartyId() {
      return this.partyId;
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   public long getExpiresAt() {
      return this.expiresAt;
   }

   public boolean isExpired() {
      return System.currentTimeMillis() > this.expiresAt;
   }

   public long getRemainingTimeMs() {
      return Math.max(0L, this.expiresAt - System.currentTimeMillis());
   }

   @Override
   public String toString() {
      return "PartyJoinRequest{requester=" + this.requesterUuid + ", partyId='" + this.partyId + "', expired=" + this.isExpired() + "}";
   }
}
