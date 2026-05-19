package com.astryxrpg.party.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * In-memory storage for parties, replacing the previous SQLite-based implementation.
 * Parties are stored in a map keyed by party ID.
 * Note: This implementation does not persist data to disk and is not thread-safe.
 * For a production environment, consider adding synchronization or using a concurrent map.
 */
public class PartyStorage {
    /** Map to store parties by their ID. */
    private static final Map<String, Party> parties = new HashMap<>();

    /** Map to store party join requests by party ID. */
    private static final Map<String, List<PartyJoinRequest>> partyJoinRequests = new HashMap<>();

    /** Map to cache player names by UUID. */
    private static final Map<UUID, String> playerNameCache = new HashMap<>();

    /**
     * Initializes the storage. In this in-memory version, this method does nothing
     * but is kept for compatibility with the previous interface.
     */
    public static void init() {
        // No initialization required for in-memory storage
    }

    /**
     * Checks if the storage is initialized.
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        // Always considered initialized for in-memory storage
        return true;
    }

    /**
     * Saves a party to the in-memory map.
     * @param party the party to save
     */
    public static void saveParty(@Nonnull Party party) {
        parties.put(party.getId(), party);
    }

    /**
     * Loads all parties from the in-memory map.
     * @return a list of all parties
     */
    @Nonnull
    public static List<Party> loadAllParties() {
        return new ArrayList<>(parties.values());
    }

    /**
     * Loads join requests for a party that have not expired.
     * @param partyId the ID of the party
     * @return a list of valid join requests
     */
    @Nonnull
    public static List<PartyJoinRequest> loadJoinRequests(@Nonnull String partyId) {
        List<PartyJoinRequest> requests = partyJoinRequests.get(partyId);
        if (requests == null) {
            return new ArrayList<>();
        }
        // Filter out expired requests
        long now = System.currentTimeMillis();
        List<PartyJoinRequest> validRequests = new ArrayList<>();
        for (PartyJoinRequest request : requests) {
            if (request.getExpiresAt() > now) {
                validRequests.add(request);
            }
        }
        return validRequests;
    }

    /**
     * Adds a member to a party.
     * @param partyId the ID of the party
     * @param memberUuid the UUID of the member to add
     */
    public static void addMember(@Nonnull String partyId, @Nonnull UUID memberUuid) {
        Party party = parties.get(partyId);
        if (party != null) {
            party.addMember(memberUuid);
        }
    }

    /**
     * Removes a member from a party.
     * @param partyId the ID of the party
     * @param memberUuid the UUID of the member to remove
     */
    public static void removeMember(@Nonnull String partyId, @Nonnull UUID memberUuid) {
        Party party = parties.get(partyId);
        if (party != null) {
            party.removeMember(memberUuid);
        }
    }

    /**
     * Updates the role of a member in a party.
     * @param partyId the ID of the party
     * @param memberUuid the UUID of the member
     * @param role the new role for the member
     */
    public static void updateMemberRole(@Nonnull String partyId, @Nonnull UUID memberUuid, @Nonnull PartyRole role) {
        Party party = parties.get(partyId);
        if (party != null) {
            party.setRole(memberUuid, role);
        }
    }

    /**
     * Updates the leader of a party.
     * @param partyId the ID of the party
     * @param newLeaderUuid the UUID of the new leader
     */
    public static void updateLeader(@Nonnull String partyId, @Nonnull UUID newLeaderUuid) {
        Party party = parties.get(partyId);
        if (party != null) {
            party.setLeaderUuid(newLeaderUuid);
        }
    }

    /**
     * Updates the settings of a party.
     * @param party the party with updated settings
     */
    public static void updatePartySettings(@Nonnull Party party) {
        Party existing = parties.get(party.getId());
        if (existing != null) {
            // Update the existing party with the new settings
            existing.setName(party.getName());
            existing.setPassword(party.getPassword());
            existing.setAccessType(party.getAccessType());
            existing.setMaxMembers(party.getMaxMembers());
            // Note: members and leader are not updated here intentionally, as they are set via other methods
        }
    }

    /**
     * Deletes a party from the in-memory map by its ID.
     * @param partyId the ID of the party to delete
     */
    public static void deleteParty(@Nonnull String partyId) {
        parties.remove(partyId);
        // Also remove any join requests for this party
        partyJoinRequests.remove(partyId);
    }

    /**
     * Saves a party join request.
     * @param request the join request to save
     */
    public static void saveJoinRequest(@Nonnull PartyJoinRequest request) {
        partyJoinRequests.computeIfAbsent(request.getPartyId(), k -> new ArrayList<>()).add(request);
    }

    /**
     * Deletes a party join request.
     * @param partyId the ID of the party
     * @param requesterUuid the UUID of the requester
     */
    public static void deleteJoinRequest(@Nonnull String partyId, @Nonnull UUID requesterUuid) {
        List<PartyJoinRequest> requests = partyJoinRequests.get(partyId);
        if (requests != null) {
            requests.removeIf(r -> r.getRequesterUuid().equals(requesterUuid));
            // If the list becomes empty, remove the key to avoid memory leak
            if (requests.isEmpty()) {
                partyJoinRequests.remove(partyId);
            }
        }
    }

    /**
     * Cleans up expired join requests for all parties.
     */
    public static void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<PartyJoinRequest>> entry : partyJoinRequests.entrySet()) {
            List<PartyJoinRequest> requests = entry.getValue();
            requests.removeIf(r -> r.getExpiresAt() < now);
            // Remove the party ID if no requests remain
            if (requests.isEmpty()) {
                partyJoinRequests.remove(entry.getKey());
            }
        }
    }

    /**
     * Caches a player's name.
     * @param uuid the player's UUID
     * @param username the player's username
     */
    public static void cachePlayerName(@Nonnull UUID uuid, @Nonnull String username) {
        playerNameCache.put(uuid, username);
    }

    /**
     * Gets a cached player name by UUID.
     * @param uuid the player's UUID
     * @return the cached username, or null if not found
     */
    @Nullable
    public static String getCachedPlayerName(@Nonnull UUID uuid) {
        return playerNameCache.get(uuid);
    }

    /**
     * Closes the storage. In this in-memory version, this method does nothing
     * but is kept for compatibility with the previous interface.
     */
    public static void close() {
        // No resources to close for in-memory storage
    }

    /**
     * Gets the internal connection object for compatibility.
     * @return null, as there is no connection in this in-memory implementation
     */
    public static Object getInstance() {
        return null;
    }
}