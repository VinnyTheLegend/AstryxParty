package com.astryxrpg.party.systems;

import com.astryxrpg.party.AstryxParty;
import com.astryxrpg.party.party.Party;
import com.astryxrpg.party.party.PartyManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class PartyDamageProtectionSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY;

    static {
        QUERY = Player.getComponentType();
    }

    @Override
    public void handle(
            int i,
            @NonNullDecl ArchetypeChunk<EntityStore> archetypeChunk,
            @NonNullDecl Store<EntityStore> store,
            @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
            @NonNullDecl Damage damage
    ) {
        Player playerComponent = archetypeChunk.getComponent(i, Player.getComponentType());
        if (playerComponent == null) return;

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;

        final Ref<EntityStore> attackerRef = entitySource.getRef();
        if (!attackerRef.isValid()) return;

        final PlayerRef attacker = commandBuffer.getComponent(attackerRef, PlayerRef.getComponentType());
        if (attacker == null) return;

        PartyManager partyManager = AstryxParty.getInstance().getPartyManager();

        final Party attackerParty = partyManager.getPartyByPlayer(attacker.getUuid());
        if (attackerParty == null) return;

        final UUIDComponent victimUuid = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
        if (victimUuid == null) return;

        final Party victimParty = partyManager.getPartyByPlayer(victimUuid.getUuid());
        if (victimParty == null) return;

        if (!attackerParty.getId().equals(victimParty.getId())) return;

        damage.setCancelled(true);
    }

    @NullableDecl
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }
}