package com.astryxrpg.party.party;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

public class PartyInvite {
   private final UUID inviterUuid;
   private final UUID inviteeUuid;
   private final String partyId;
   private final long createdAt;
   private final long expiresAt;

   public PartyInvite(@Nonnull UUID inviterUuid, @Nonnull UUID inviteeUuid, @Nonnull String partyId, int timeoutSeconds) {
      this.inviterUuid = inviterUuid;
      this.inviteeUuid = inviteeUuid;
      this.partyId = partyId;
      this.createdAt = System.currentTimeMillis();
      this.expiresAt = this.createdAt + timeoutSeconds * 1000L;
   }

   @Nonnull
   public UUID getInviterUuid() {
      return this.inviterUuid;
   }

   @Nonnull
   public UUID getInviteeUuid() {
      return this.inviteeUuid;
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
   public boolean equals(Object object) {
      if (this == object) {
         return true;
      } else if (object != null && this.getClass() == object.getClass()) {
         PartyInvite that = (PartyInvite)object;
         return Objects.equals(this.inviterUuid, that.inviterUuid)
            && Objects.equals(this.inviteeUuid, that.inviteeUuid)
            && Objects.equals(this.partyId, that.partyId);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.inviterUuid, this.inviteeUuid, this.partyId);
   }

   @Override
   public String toString() {
      return "PartyInvite{inviter="
         + this.inviterUuid
         + ", invitee="
         + this.inviteeUuid
         + ", partyId='"
         + this.partyId
         + "', expired="
         + this.isExpired()
         + "}";
   }
}
