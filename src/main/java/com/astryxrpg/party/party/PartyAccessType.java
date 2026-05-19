package com.astryxrpg.party.party;

import javax.annotation.Nonnull;

public enum PartyAccessType {
   OPEN(0, "Open", "Anyone can join"),
   PASSWORDED(1, "Password", "Requires password to join"),
   REQUEST_ONLY(2, "Request", "Players must request to join"),
   LOCKED(3, "Locked", "Invite only");

   private final int level;
   private final String displayName;
   private final String description;

   PartyAccessType(int level, String displayName, String description) {
      this.level = level;
      this.displayName = displayName;
      this.description = description;
   }

   public int getLevel() {
      return this.level;
   }

   @Nonnull
   public String getDisplayName() {
      return this.displayName;
   }

   @Nonnull
   public String getDescription() {
      return this.description;
   }

   public boolean isPubliclyVisible() {
      return this != LOCKED;
   }

   @Nonnull
   public static PartyAccessType fromLevel(int level) {
      for (PartyAccessType type : values()) {
         if (type.level == level) {
            return type;
         }
      }

      return LOCKED;
   }
}
