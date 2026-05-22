package com.astryxrpg.party.config;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Component that stores HUD settings for a player entity.
 * This replaces the JSON-based PlayerHudSettings system with automatic serialization.
 */
public class PartySettingsComponent implements Component<EntityStore> {

    // Fields to store HUD settings
    private boolean ShowHud = true;
    private boolean ShowSelf = true;
    private int MaxDisplayedMembers = 8;
    private PartySettings.OrderMode OrderMode = PartySettings.OrderMode.FIXED;

    private static ComponentType<EntityStore, PartySettingsComponent> type;
    public static ComponentType<EntityStore, PartySettingsComponent> getComponentType(){
        if (type == null) {
            throw new IllegalStateException("PartySettingsComponent type not initialized. Ensure the plugin's setup() method is called.");
        }
        return type;
    }
    public static void setComponentType(ComponentType<EntityStore, PartySettingsComponent> type){
        PartySettingsComponent.type = type;
    }

    // BuilderCodec for serialization/deserialization with entity data
    @Nonnull
    public static final BuilderCodec<PartySettingsComponent> CODEC = BuilderCodec
            .builder(PartySettingsComponent.class, PartySettingsComponent::new)
            .append(
                    new KeyedCodec<>("ShowHud", Codec.BOOLEAN),
                    (component, value) -> component.ShowHud = value,
                    component -> component.ShowHud
            ).add()
            .append(
                    new KeyedCodec<>("ShowSelf", Codec.BOOLEAN),
                    (component, value) -> component.ShowSelf = value,
                    component -> component.ShowSelf
            ).add()
            .append(
                    new KeyedCodec<>("MaxDisplayedMembers", Codec.INTEGER),
                    (component, value) -> component.MaxDisplayedMembers = value,
                    component -> component.MaxDisplayedMembers
            ).add()
            .append(
            new KeyedCodec<>("OrderMode", Codec.STRING),
            (component, value) -> component.OrderMode = PartySettings.OrderMode.valueOf(value),
            component -> component.OrderMode.name()
        ).add()
            .build();

    // Constructors
    public PartySettingsComponent() {
        // Default constructor for codec
    }

    public PartySettingsComponent(boolean showHud, boolean showSelf, int maxDisplayedMembers, PartySettings.OrderMode orderMode) {
        this.ShowHud = showHud;
        this.ShowSelf = showSelf;
        this.MaxDisplayedMembers = Math.max(1, Math.min(8, maxDisplayedMembers)); // Clamp to valid range
        this.OrderMode = orderMode != null ? orderMode : PartySettings.OrderMode.FIXED;
    }

    public PartySettingsComponent(PartySettingsComponent other) {
        this.ShowHud = other.ShowHud;
        this.ShowSelf = other.ShowSelf;
        this.MaxDisplayedMembers = other.MaxDisplayedMembers;
        this.OrderMode = other.OrderMode;
    }

    // Component methods
    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new PartySettingsComponent(this);
    }

    // Getters and setters
    public boolean getShowHud() {
        return ShowHud;
    }

    public void setShowHud(boolean showHud) {
        this.ShowHud = showHud;
    }

    public boolean getShowSelf() {
        return ShowSelf;
    }

    public void setShowSelf(boolean showSelf) {
        this.ShowSelf = showSelf;
    }

    public int getMaxDisplayedMembers() {
        return MaxDisplayedMembers;
    }

    public void setMaxDisplayedMembers(int maxDisplayedMembers) {
        this.MaxDisplayedMembers = Math.max(1, Math.min(8, maxDisplayedMembers)); // Clamp to valid range
    }

    public PartySettings.OrderMode getOrderMode() {
        return OrderMode;
    }

    public void setOrderMode(PartySettings.OrderMode orderMode) {
        this.OrderMode = orderMode != null ? orderMode : PartySettings.OrderMode.FIXED;
    }

    // Validation method to ensure values are within valid ranges
    public void validate() {
        this.MaxDisplayedMembers = Math.max(1, Math.min(8, this.MaxDisplayedMembers));
        if (this.OrderMode == null) {
            this.OrderMode = PartySettings.OrderMode.FIXED;
        }
    }
}