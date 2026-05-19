package com.astryxrpg.party.party;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FakeMember {
   private final UUID uuid;
   private final String name;
   private double x;
   private double y;
   private double z;
   private float yaw;
   private final double originX;
   private final double originZ;
   private double targetX;
   private double targetZ;
   private boolean isMoving = true;
   private final Random random = new Random();
   @Nullable
   private Ref<EntityStore> entityRef;
   private String npcType;

   public FakeMember(@Nonnull String name, double x, double y, double z) {
      this.uuid = UUID.randomUUID();
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
      this.yaw = 0.0F;
      this.originX = x;
      this.originZ = z;
      this.pickNewTarget();
   }

   @Nonnull
   public UUID getUuid() {
      return this.uuid;
   }

   @Nonnull
   public String getName() {
      return this.name;
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public double getZ() {
      return this.z;
   }

   public float getYaw() {
      return this.yaw;
   }

   public void setPosition(double x, double y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public void setYaw(float yaw) {
      this.yaw = yaw;
   }

   public boolean isMoving() {
      return this.isMoving;
   }

   public void setMoving(boolean moving) {
      this.isMoving = moving;
   }

   private void pickNewTarget() {
      double radius = 30.0;
      this.targetX = this.originX + (this.random.nextDouble() * 2.0 - 1.0) * radius;
      this.targetZ = this.originZ + (this.random.nextDouble() * 2.0 - 1.0) * radius;
   }

   public boolean updateMovement() {
      if (!this.isMoving) {
         return false;
      } else {
         double dx = this.targetX - this.x;
         double dz = this.targetZ - this.z;
         double distSq = dx * dx + dz * dz;
         if (distSq < 4.0) {
            this.pickNewTarget();
            return false;
         } else {
            double dist = Math.sqrt(distSq);
            double moveSpeed = 0.5;
            double moveX = dx / dist * moveSpeed;
            double moveZ = dz / dist * moveSpeed;
            this.x += moveX;
            this.z += moveZ;
            this.yaw = (float)Math.atan2(moveZ, moveX);
            return true;
         }
      }
   }

   public void setEntityRef(@Nullable Ref<EntityStore> entityRef) {
      this.entityRef = entityRef;
   }

   @Nullable
   public Ref<EntityStore> getEntityRef() {
      return this.entityRef;
   }

   public boolean hasEntity() {
      return this.entityRef != null && this.entityRef.isValid();
   }

   public void setNpcType(@Nullable String npcType) {
      this.npcType = npcType;
   }

   @Nullable
   public String getNpcType() {
      return this.npcType;
   }

   @Override
   public String toString() {
      return "FakeMember{name='" + this.name + "', pos=(" + this.x + ", " + this.y + ", " + this.z + ")" + (this.hasEntity() ? ", hasEntity=true" : "") + "}";
   }
}
