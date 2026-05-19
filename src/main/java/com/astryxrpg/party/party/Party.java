package com.astryxrpg.party.party;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Party {
   private static final int DEFAULT_MAX_MEMBERS = 8;
   private static final int MIN_MEMBERS = 1;
   private static final int MAX_MEMBERS_LIMIT = 20;
   private static final int MAX_NAME_LENGTH = 32;
   private String id;
   private UUID leaderUuid;
   private final Set<UUID> memberUuids = ConcurrentHashMap.newKeySet();
   private final Map<UUID, PartyRole> memberRoles = new ConcurrentHashMap<>();
   private long createdAt;
   private String name;
   private String password;
   private PartyAccessType accessType = PartyAccessType.LOCKED;
   private int maxMembers = 8;
   private transient Set<UUID> unmodifiableMembers;
   private transient Map<UUID, FakeMember> fakeMembers = new ConcurrentHashMap<>();

   public Party() {
      this.id = UUID.randomUUID().toString();
      this.createdAt = System.currentTimeMillis();
   }

   public Party(@Nonnull UUID leaderUuid) {
      this(leaderUuid, "Party");
   }

   public Party(@Nonnull UUID leaderUuid, @Nonnull String name) {
      this();
      this.leaderUuid = leaderUuid;
      this.name = this.sanitizeName(name);
      this.memberUuids.add(leaderUuid);
      this.updateUnmodifiable();
   }

   private String sanitizeName(String name) {
      if (name != null && !name.isBlank()) {
         String trimmed = name.trim();
         return trimmed.substring(0, Math.min(trimmed.length(), 32));
      } else {
         return "Party";
      }
   }

   private void updateUnmodifiable() {
      this.unmodifiableMembers = Set.copyOf(this.memberUuids);
   }

   @Nonnull
   public String getId() {
      return this.id;
   }

   @Nonnull
   public UUID getLeaderUuid() {
      return this.leaderUuid;
   }

   @Nonnull
   public Set<UUID> getMemberUuids() {
      if (this.unmodifiableMembers == null) {
         this.updateUnmodifiable();
      }

      return this.unmodifiableMembers;
   }

   public int getMemberCount() {
      return this.memberUuids.size();
   }

   public long getCreatedAt() {
      return this.createdAt;
   }

   @Nonnull
   public String getName() {
      return this.name != null ? this.name : "Party";
   }

   @Nullable
   public String getPassword() {
      return this.password;
   }

   @Nonnull
   public PartyAccessType getAccessType() {
      return this.accessType;
   }

   public int getMaxMembers() {
      return this.maxMembers;
   }

   public boolean isFull() {
      return this.memberUuids.size() >= this.maxMembers;
   }

   public boolean isJoinable() {
      return !this.isFull() && this.accessType != PartyAccessType.LOCKED;
   }

   public boolean checkPassword(@Nullable String input) {
      return this.password != null && !this.password.isEmpty() ? this.password.equals(input) : true;
   }

   public boolean isLeader(@Nonnull UUID uuid) {
      return this.leaderUuid.equals(uuid);
   }

   public boolean isMember(@Nonnull UUID uuid) {
      return this.memberUuids.contains(uuid);
   }

   @Nonnull
   public PartyRole getRole(@Nonnull UUID uuid) {
      return this.isLeader(uuid) ? PartyRole.ADMIN : this.memberRoles.getOrDefault(uuid, PartyRole.GUEST);
   }

   @Nonnull
   public String getRoleDisplayName(@Nonnull UUID uuid) {
      return this.isLeader(uuid) ? "Leader" : this.getRole(uuid).getDisplayName();
   }

   @Nonnull
   public Map<UUID, PartyRole> getMemberRoles() {
      return Collections.unmodifiableMap(this.memberRoles);
   }

   public void setId(@Nonnull String id) {
      this.id = id;
   }

   public void setLeaderUuid(@Nonnull UUID leaderUuid) {
      this.leaderUuid = leaderUuid;
   }

   public void setCreatedAt(long createdAt) {
      this.createdAt = createdAt;
   }

   public void setName(@Nonnull String name) {
      this.name = this.sanitizeName(name);
   }

   public void setPassword(@Nullable String password) {
      this.password = password != null && !password.isEmpty() ? password : null;
   }

   public void setAccessType(@Nonnull PartyAccessType accessType) {
      this.accessType = accessType;
   }

   public void setMaxMembers(int maxMembers) {
      this.maxMembers = Math.max(1, Math.min(20, maxMembers));
   }

   public void addMember(@Nonnull UUID uuid) {
      boolean added = this.memberUuids.add(uuid);
      if (added) {
         this.updateUnmodifiable();
      }
   }

   public void removeMember(@Nonnull UUID uuid) {
      if (!this.isLeader(uuid)) {
         boolean removed = this.memberUuids.remove(uuid);
         this.memberRoles.remove(uuid);
         if (removed) {
            this.updateUnmodifiable();
         }
      }
   }

   public void setRole(@Nonnull UUID uuid, @Nonnull PartyRole role) {
      if (!this.isMember(uuid) && !this.isLeader(uuid)) {
         this.memberRoles.put(uuid, role);
      }
   }

   public boolean promote(@Nonnull UUID uuid) {
      if (!this.isMember(uuid) && !this.isLeader(uuid)) {
         PartyRole current = this.getRole(uuid);
         PartyRole next = current.getNextRole();
         if (next != null) {
            this.memberRoles.put(uuid, next);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean demote(@Nonnull UUID uuid) {
      if (!this.isMember(uuid) && !this.isLeader(uuid)) {
         PartyRole current = this.getRole(uuid);
         PartyRole prev = current.getPreviousRole();
         if (prev != null) {
            this.memberRoles.put(uuid, prev);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public void transferLeadership(@Nonnull UUID newLeaderUuid) {
      if (!this.isMember(newLeaderUuid) && !this.isLeader(newLeaderUuid)) {
         this.memberRoles.put(this.leaderUuid, PartyRole.ADMIN);
         this.leaderUuid = newLeaderUuid;
         this.memberRoles.remove(newLeaderUuid);
      }
   }

   @Nonnull
   public Set<UUID> getMembersExcept(@Nonnull UUID excludeUuid) {
      Set<UUID> result = new HashSet<>(this.memberUuids);
      result.remove(excludeUuid);
      return result;
   }

   @Nullable
   public UUID getNextLeaderCandidate() {
      for (UUID uuid : this.memberUuids) {
         if (!uuid.equals(this.leaderUuid)) {
            return uuid;
         }
      }

      return null;
   }

   @Nonnull
   public static String getPlayerName(@Nonnull UUID uuid) {
      PlayerRef ref = Universe.get().getPlayer(uuid);
      if (ref != null) {
         String username = ref.getUsername();

         try {
            PartyStorage.cachePlayerName(uuid, username);
         } catch (Exception var4) {
         }

         return username;
      } else {
         try {
            String cachedName = PartyStorage.getCachedPlayerName(uuid);
            if (cachedName != null) {
               return cachedName;
            }
         } catch (Exception var5) {
         }

         return "Unknown";
      }
   }

   public static boolean isPlayerOnline(@Nonnull UUID uuid) {
      return Universe.get().getPlayer(uuid) != null;
   }

   @Nonnull
   public String getMemberName(@Nonnull UUID uuid) {
      FakeMember fake = this.getFakeMember(uuid);
      return fake != null ? fake.getName() : getPlayerName(uuid);
   }

   @Nonnull
   public String getMemberStatus(@Nonnull UUID uuid) {
      String role = this.getRoleDisplayName(uuid);
      if (this.getFakeMember(uuid) != null) {
         return role;
      } else {
         return isPlayerOnline(uuid) ? role : role + " (Offline)";
      }
   }

   @Nonnull
   public Map<UUID, String> getMemberNames() {
      Map<UUID, String> result = new LinkedHashMap<>();

      for (UUID uuid : this.memberUuids) {
         result.put(uuid, this.getMemberName(uuid));
      }

      if (this.fakeMembers != null) {
         for (UUID uuid : this.fakeMembers.keySet()) {
            result.put(uuid, this.getMemberName(uuid));
         }
      }

      return result;
   }

   public static void sendMessageToPlayer(@Nonnull UUID uuid, @Nonnull Message message) {
      PlayerRef ref = Universe.get().getPlayer(uuid);
      if (ref != null) {
         ref.sendMessage(message);
      }
   }

   public static int adjustBounded(@Nullable String action, int currentValue, int min, int max) {
      if ("increase".equals(action)) {
         return Math.min(max, currentValue + 1);
      } else {
         return "decrease".equals(action) ? Math.max(min, currentValue - 1) : currentValue;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.id);
   }

   @Override
   public String toString() {
      return "Party{id='" + this.id + "', leader=" + this.leaderUuid + ", members=" + this.memberUuids.size() + "}";
   }

   public void addFakeMember(@Nonnull FakeMember fakeMember) {
      if (this.fakeMembers == null) {
         this.fakeMembers = new ConcurrentHashMap<>();
      }

      this.fakeMembers.put(fakeMember.getUuid(), fakeMember);
   }

   public void removeFakeMember(@Nonnull UUID uuid) {
      if (this.fakeMembers != null) {
         this.fakeMembers.remove(uuid);
      }
   }

   public void clearFakeMembers() {
      if (this.fakeMembers != null) {
         this.fakeMembers.clear();
      }
   }

   @Nonnull
   public Map<UUID, FakeMember> getFakeMembers() {
      if (this.fakeMembers == null) {
         this.fakeMembers = new ConcurrentHashMap<>();
      }

      return this.fakeMembers;
   }

   @Nullable
   public FakeMember getFakeMember(@Nonnull UUID uuid) {
      return this.fakeMembers != null ? this.fakeMembers.get(uuid) : null;
   }

   public boolean hasFakeMembers() {
      return this.fakeMembers != null && !this.fakeMembers.isEmpty();
   }
}
