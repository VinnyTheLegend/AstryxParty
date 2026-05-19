package com.astryxrpg.party.events;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nonnull;

public final class PartyEventBus {
   private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
   private static final List<PartyEventListener> listeners = new CopyOnWriteArrayList<>();

   private PartyEventBus() {
   }

   public static void register(@Nonnull PartyEventListener listener) {
      if (!listeners.contains(listener)) {
         listeners.add(listener);
         ((Api)LOGGER.atFine()).log("Registered party event listener: %s", listener.getClass().getSimpleName());
      }
   }

   public static void unregister(@Nonnull PartyEventListener listener) {
      if (listeners.remove(listener)) {
         ((Api)LOGGER.atFine()).log("Unregistered party event listener: %s", listener.getClass().getSimpleName());
      }
   }

   public static void fire(@Nonnull PartyEvent event) {
      ((Api)LOGGER.atFine()).log("Firing party event: %s", event.getClass().getSimpleName());

      for (PartyEventListener listener : listeners) {
         try {
            listener.onPartyEvent(event);
         } catch (Exception e) {
            ((Api)((Api)LOGGER.atWarning()).withCause(e))
               .log("Exception in party event listener %s while handling %s", listener.getClass().getSimpleName(), event.getClass().getSimpleName());
         }
      }
   }

   public static int getListenerCount() {
      return listeners.size();
   }

   public static void clearListeners() {
      listeners.clear();
      ((Api)LOGGER.atFine()).log("Cleared all party event listeners");
   }
}
