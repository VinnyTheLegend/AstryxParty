package com.astryxrpg.party.party;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum PartyRole {
   GUEST(0, "Guest"),
   MEMBER(1, "Member"),
   MODERATOR(2, "Moderator"),
   ADMIN(3, "Admin");

   private final int level;
   private final String displayName;

   PartyRole(int level, String displayName) {
      this.level = level;
      this.displayName = displayName;
   }

   public int getLevel() {
      return this.level;
   }

   @Nonnull
   public String getDisplayName() {
      return this.displayName;
   }

   public boolean canKick(@Nonnull PartyRole other) {
      return this.level > other.level;
   }

   public boolean canPromote(@Nonnull PartyRole other) {
      return this.level > other.level && other != ADMIN;
   }

   public boolean canDemote(@Nonnull PartyRole other) {
      return this.level > other.level && other != GUEST;
   }

   @Nullable
   public PartyRole getNextRole() {
      return switch (this) {
         case GUEST -> MEMBER;
         case MEMBER -> MODERATOR;
         case MODERATOR -> ADMIN;
         case ADMIN -> null;
      };
   }

   @Nullable
   public PartyRole getPreviousRole() {
      return switch (this) {
         case GUEST -> null;
         case MEMBER -> GUEST;
         case MODERATOR -> MEMBER;
         case ADMIN -> MODERATOR;
      };
   }

   @Nonnull
   public static PartyRole fromLevel(int level) {
      for (PartyRole role : values()) {
         if (role.level == level) {
            return role;
         }
      }

      return GUEST;
   }
}
