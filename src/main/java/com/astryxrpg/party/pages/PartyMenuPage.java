package com.astryxrpg.party.pages;

import com.astryxrpg.party.AstryxParty;
import com.astryxrpg.party.config.PartySettings;
import com.astryxrpg.party.config.PartySettingsComponent;
import com.astryxrpg.party.party.Party;
import com.astryxrpg.party.party.PartyAccessType;
import com.astryxrpg.party.party.PartyInvite;
import com.astryxrpg.party.party.PartyJoinRequest;
import com.astryxrpg.party.party.PartyManager;
import com.astryxrpg.party.party.PartyPlayerListHud;
import com.astryxrpg.party.party.PartyRole;
import com.astryxrpg.party.ui.PartyMenuEventData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PartyMenuPage extends InteractiveCustomUIPage<PartyMenuEventData> {
   private final PartyManager partyManager;
   private PartyMenuPage.ViewState currentView = PartyMenuPage.ViewState.NO_PARTY_VIEW;
   private PartyMenuPage.TabState currentTab = PartyMenuPage.TabState.PARTY;
   private String inputPartyName = "";
   private String inputPassword = "";
   private PartyAccessType selectedAccessType = PartyAccessType.LOCKED;
   private int selectedMaxMembers = 8;
   private UUID selectedPlayerUuid = null;
   private String pendingJoinPartyId = null;
   private String pendingConfirmAction = null;
   private boolean mySettingsShowHud = true;
   private boolean mySettingsShowSelf = true;
   private int mySettingsMaxDisplayed = 8;
   private PartySettings.OrderMode mySettingsOrderMode = PartySettings.OrderMode.FIXED;

   public PartyMenuPage(@Nonnull PlayerRef playerRef, @Nonnull AstryxParty plugin) {
      super(playerRef, CustomPageLifetime.CanDismiss, PartyMenuEventData.CODEC);
      this.partyManager = plugin.getPartyManager();
   }

   public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
      cmd.append("PartyMenu.ui");
      this.bindStaticEvents(events);
      this.buildContent(cmd, events);
   }

   private void bindStaticEvents(@Nonnull UIEventBuilder events) {
      events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#PartyTab", EventData.of("Action", "switchToPartyTab"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#MySettingsTab", EventData.of("Action", "switchToMySettingsTab"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#LeaderSettingsTab", EventData.of("Action", "switchToLeaderSettingsTab"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ShowHudToggle", EventData.of("Action", "toggleShowHud"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ShowSelfToggle", EventData.of("Action", "toggleShowSelf"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#HudMaxDecrease", EventData.of("Action", "hudMaxChange").append("Target", "decrease"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#HudMaxIncrease", EventData.of("Action", "hudMaxChange").append("Target", "increase"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#OrderFixed", EventData.of("Action", "setOrderMode").append("Target", "FIXED"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#OrderDistance", EventData.of("Action", "setOrderMode").append("Target", "DISTANCE"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#SaveMySettingsButton", EventData.of("Action", "saveMySettings"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#CreatePartyButton", EventData.of("Action", "showCreateParty"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ViewInvitesButton", EventData.of("Action", "viewInvites"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshListButton", EventData.of("Action", "refreshList"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromCreate", EventData.of("Action", "backFromCreate"), false);
      events.addEventBinding(
         CustomUIEventBindingType.Activating,
         "#ConfirmCreateButton",
         EventData.of("Action", "confirmCreate").append("@PartyName", "#PartyNameInput.Value").append("@Password", "#CreatePasswordInput.Value"),
         false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#CreateAccessOpen", EventData.of("Action", "setCreateAccessType").append("Target", "OPEN"), false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#CreateAccessPassword", EventData.of("Action", "setCreateAccessType").append("Target", "PASSWORDED"), false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#CreateAccessRequest", EventData.of("Action", "setCreateAccessType").append("Target", "REQUEST_ONLY"), false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#CreateAccessLocked", EventData.of("Action", "setCreateAccessType").append("Target", "LOCKED"), false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#CreateMaxMembersIncrease", EventData.of("Action", "createMaxMembersChange").append("Target", "increase"), false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#CreateMaxMembersDecrease", EventData.of("Action", "createMaxMembersChange").append("Target", "decrease"), false
      );
      events.addEventBinding(CustomUIEventBindingType.Activating, "#InvitePlayerButton", EventData.of("Action", "showInvite"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#LeavePartyButton", EventData.of("Action", "leave"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ViewRequestsButton", EventData.of("Action", "viewJoinRequests"), false);
      events.addEventBinding(
         CustomUIEventBindingType.Activating,
         "#SaveSettingsButton",
         EventData.of("Action", "saveSettings").append("@PartyName", "#SettingsNameInput.Value").append("@Password", "#SettingsPasswordInput.Value"),
         false
      );
      events.addEventBinding(CustomUIEventBindingType.Activating, "#DisbandButton", EventData.of("Action", "disband"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#AccessOpen", EventData.of("Action", "setAccessType").append("Target", "OPEN"), false);
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#AccessPassword", EventData.of("Action", "setAccessType").append("Target", "PASSWORDED"), false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#AccessRequest", EventData.of("Action", "setAccessType").append("Target", "REQUEST_ONLY"), false
      );
      events.addEventBinding(CustomUIEventBindingType.Activating, "#AccessLocked", EventData.of("Action", "setAccessType").append("Target", "LOCKED"), false);
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#MaxMembersIncrease", EventData.of("Action", "maxMembersChange").append("Target", "increase"), false
      );
      events.addEventBinding(
         CustomUIEventBindingType.Activating, "#MaxMembersDecrease", EventData.of("Action", "maxMembersChange").append("Target", "decrease"), false
      );
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BackToPartyButton", EventData.of("Action", "backToParty"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromPassword", EventData.of("Action", "backFromPassword"), false);
      events.addEventBinding(
         CustomUIEventBindingType.Activating,
         "#JoinWithPasswordButton",
         EventData.of("Action", "joinWithPassword").append("@Password", "#PasswordInput.Value"),
         false
      );
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromInvites", EventData.of("Action", "backFromInvites"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BackFromRequests", EventData.of("Action", "backFromRequests"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#AcceptInvite", EventData.of("Action", "accept"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#DeclineInvite", EventData.of("Action", "decline"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelConfirmButton", EventData.of("Action", "cancelConfirm"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmActionButton", EventData.of("Action", "executeConfirm"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#BackToPartyFromAction", EventData.of("Action", "backToPartyFromAction"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#PromoteButton", EventData.of("Action", "promote"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#DemoteButton", EventData.of("Action", "demote"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#KickPlayerButton", EventData.of("Action", "kickSelected"), false);
      events.addEventBinding(CustomUIEventBindingType.Activating, "#TransferLeadershipButton", EventData.of("Action", "confirmTransfer"), false);
   }

   public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PartyMenuEventData data) {
      String action = data.getAction();
      String target = data.getTarget();
      switch (action) {
         case "close":
            this.closePage(ref, store);
            break;
         case "switchToPartyTab":
            this.currentTab = PartyMenuPage.TabState.PARTY;
            this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
            this.refreshUI(ref, store);
            break;
         case "switchToMySettingsTab":
            this.currentTab = PartyMenuPage.TabState.MY_SETTINGS;
            this.currentView = PartyMenuPage.ViewState.MY_SETTINGS_VIEW;
            this.loadMySettingsFromConfig();
            this.refreshUI(ref, store);
            break;
         case "switchToLeaderSettingsTab":
            this.currentTab = PartyMenuPage.TabState.LEADER_SETTINGS;
            this.currentView = PartyMenuPage.ViewState.SETTINGS_VIEW;
            this.loadSettingsFromParty();
            this.refreshUI(ref, store);
            break;
         case "toggleShowHud":
            this.mySettingsShowHud = !this.mySettingsShowHud;
            this.refreshUI(ref, store);
            break;
         case "toggleShowSelf":
            this.mySettingsShowSelf = !this.mySettingsShowSelf;
            this.saveMySettingsToConfig();
            this.refreshUI(ref, store);
            break;
         case "hudMaxChange":
            this.mySettingsMaxDisplayed = Party.adjustBounded(target, this.mySettingsMaxDisplayed, 1, 8);
            this.refreshUI(ref, store);
            break;
         case "setOrderMode":
            if (target != null) {
               this.mySettingsOrderMode = PartySettings.OrderMode.valueOf(target);
               this.refreshUI(ref, store);
            }
            break;
         case "saveMySettings":
            this.saveMySettingsToConfig();
            this.playerRef.sendMessage(Message.raw("HUD settings saved!"));
            this.refreshUI(ref, store);
            break;
         case "showCreateParty":
            this.currentView = PartyMenuPage.ViewState.CREATE_PARTY_VIEW;
            this.inputPartyName = this.playerRef.getUsername() + "'s Party";
            this.inputPassword = "";
            this.selectedAccessType = PartyAccessType.OPEN;
            this.selectedMaxMembers = 8;
            this.refreshUI(ref, store);
            break;
         case "backFromCreate":
            this.currentView = PartyMenuPage.ViewState.NO_PARTY_VIEW;
            this.refreshUI(ref, store);
            break;
         case "setCreateAccessType":
            if (target != null) {
               this.selectedAccessType = PartyAccessType.valueOf(target);
               this.refreshUI(ref, store);
            }
            break;
         case "createMaxMembersChange":
            this.selectedMaxMembers = Party.adjustBounded(target, this.selectedMaxMembers, 1, 20);
            this.refreshUI(ref, store);
            break;
         case "confirmCreate": {
            String partyName = data.getPartyName();
            String password = data.getPassword();
            if (partyName != null && !partyName.isBlank()) {
               this.inputPartyName = partyName;
            }

            if (password != null) {
               this.inputPassword = password;
            }

            if (!this.inputPartyName.isBlank()) {
               Party party = this.partyManager.createParty(this.playerRef.getUuid(), this.inputPartyName);
               if (party != null) {
                  String finalPassword = this.selectedAccessType == PartyAccessType.PASSWORDED && !this.inputPassword.isEmpty() ? this.inputPassword : null;
                  this.partyManager
                     .updatePartySettings(this.playerRef.getUuid(), this.inputPartyName, finalPassword, this.selectedAccessType, this.selectedMaxMembers);
               }

               this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
               this.currentTab = PartyMenuPage.TabState.PARTY;
            }

            this.refreshUI(ref, store);
            break;
         }
         case "joinOpenParty":
            if (target != null) {
               boolean success = this.partyManager.joinOpenParty(this.playerRef.getUuid(), target);
               if (success) {
                  this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
                  this.currentTab = PartyMenuPage.TabState.PARTY;
               }
            }

            this.refreshUI(ref, store);
            break;
         case "showPasswordEntry":
            if (target != null) {
               this.pendingJoinPartyId = target;
               this.currentView = PartyMenuPage.ViewState.ENTER_PASSWORD_VIEW;
               this.inputPassword = "";
            }

            this.refreshUI(ref, store);
            break;
         case "sendJoinRequest":
            if (target != null) {
               boolean success = this.partyManager.sendJoinRequest(this.playerRef.getUuid(), target);
               if (success) {
                  this.playerRef.sendMessage(Message.raw("Join request sent!"));
               }
            }

            this.refreshUI(ref, store);
            break;
         case "backFromPassword":
            this.currentView = PartyMenuPage.ViewState.NO_PARTY_VIEW;
            this.pendingJoinPartyId = null;
            this.refreshUI(ref, store);
            break;
         case "joinWithPassword": {
            if (this.pendingJoinPartyId != null) {
               String password = data.getPassword();
               if (password != null) {
                  this.inputPassword = password;
               }

               boolean success = this.partyManager.joinWithPassword(this.playerRef.getUuid(), this.pendingJoinPartyId, this.inputPassword);
               if (success) {
                  this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
                  this.currentTab = PartyMenuPage.TabState.PARTY;
               } else {
                  this.playerRef.sendMessage(Message.raw("Wrong password!"));
               }

               this.pendingJoinPartyId = null;
            }

            this.refreshUI(ref, store);
            break;
         }
         case "viewInvites":
            this.currentView = PartyMenuPage.ViewState.INVITES_LIST_VIEW;
            this.refreshUI(ref, store);
            break;
         case "backFromInvites":
            this.currentView = PartyMenuPage.ViewState.NO_PARTY_VIEW;
            this.refreshUI(ref, store);
            break;
         case "refreshList":
            this.refreshUI(ref, store);
            break;
         case "setAccessType":
            if (target != null) {
               this.selectedAccessType = PartyAccessType.valueOf(target);
            }

            this.refreshUI(ref, store);
            break;
         case "maxMembersChange":
            this.selectedMaxMembers = Party.adjustBounded(target, this.selectedMaxMembers, 1, 20);
            this.refreshUI(ref, store);
            break;
         case "saveSettings": {
            String partyName = data.getPartyName();
            String password = data.getPassword();
            if (partyName != null && !partyName.isBlank()) {
               this.inputPartyName = partyName;
            }

            if (password != null) {
               this.inputPassword = password;
            }

            String finalPassword = this.selectedAccessType == PartyAccessType.PASSWORDED && !this.inputPassword.isEmpty() ? this.inputPassword : null;
            this.partyManager
               .updatePartySettings(this.playerRef.getUuid(), this.inputPartyName, finalPassword, this.selectedAccessType, this.selectedMaxMembers);
            this.playerRef.sendMessage(Message.raw("Settings saved!"));
            this.refreshUI(ref, store);
            break;
         }
         case "viewJoinRequests":
            this.currentView = PartyMenuPage.ViewState.JOIN_REQUEST_VIEW;
            this.refreshUI(ref, store);
            break;
         case "backFromRequests":
            this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
            this.refreshUI(ref, store);
            break;
         case "acceptRequest":
            if (target != null) {
               this.partyManager.acceptJoinRequest(this.playerRef.getUuid(), UUID.fromString(target));
            }

            this.refreshUI(ref, store);
            break;
         case "declineRequest":
            if (target != null) {
               this.partyManager.declineJoinRequest(this.playerRef.getUuid(), UUID.fromString(target));
            }

            this.refreshUI(ref, store);
            break;
         case "showInvite":
            this.currentView = PartyMenuPage.ViewState.INVITE_VIEW;
            this.refreshUI(ref, store);
            break;
         case "backToParty":
            this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
            this.refreshUI(ref, store);
            break;
         case "invite":
            if (target != null) {
               UUID targetUuid = UUID.fromString(target);
               this.partyManager.sendInvite(this.playerRef.getUuid(), targetUuid);
               PlayerRef targetRef = Universe.get().getPlayer(targetUuid);
               if (targetRef != null) {
                  targetRef.sendMessage(Message.raw(this.playerRef.getUsername() + " invited you to their party!"));
               }
            }

            this.refreshUI(ref, store);
            break;
         case "leave":
            this.pendingConfirmAction = "leave";
            this.refreshUI(ref, store);
            break;
         case "disband":
            this.pendingConfirmAction = "disband";
            this.refreshUI(ref, store);
            break;
         case "cancelConfirm":
            this.pendingConfirmAction = null;
            this.refreshUI(ref, store);
            break;
         case "executeConfirm":
            if ("leave".equals(this.pendingConfirmAction)) {
               this.partyManager.leaveParty(this.playerRef.getUuid(), false);
               this.currentView = PartyMenuPage.ViewState.NO_PARTY_VIEW;
               this.pendingConfirmAction = null;
               this.refreshUI(ref, store);
            } else if ("disband".equals(this.pendingConfirmAction)) {
               Party party = this.partyManager.getPartyByPlayer(this.playerRef.getUuid());
               if (party != null) {
                  this.partyManager.disbandParty(party.getId());
               }

               this.currentView = PartyMenuPage.ViewState.NO_PARTY_VIEW;
               this.pendingConfirmAction = null;
               this.refreshUI(ref, store);
            } else if ("transferLeadership".equals(this.pendingConfirmAction) && this.selectedPlayerUuid != null) {
               this.partyManager.transferLeadership(this.playerRef.getUuid(), this.selectedPlayerUuid);
               this.selectedPlayerUuid = null;
               this.pendingConfirmAction = null;
               this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
               this.refreshUI(ref, store);
            }
            break;
         case "selectPlayer":
            if (target != null) {
               this.selectedPlayerUuid = UUID.fromString(target);
               this.currentView = PartyMenuPage.ViewState.PLAYER_ACTION_VIEW;
               this.refreshUI(ref, store);
            }
            break;
         case "backToPartyFromAction":
            this.selectedPlayerUuid = null;
            this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
            this.refreshUI(ref, store);
            break;
         case "promote":
            if (this.selectedPlayerUuid != null) {
               this.partyManager.promotePlayer(this.playerRef.getUuid(), this.selectedPlayerUuid);
               this.refreshUI(ref, store);
            }
            break;
         case "demote":
            if (this.selectedPlayerUuid != null) {
               this.partyManager.demotePlayer(this.playerRef.getUuid(), this.selectedPlayerUuid);
               this.refreshUI(ref, store);
            }
            break;
         case "kickSelected":
            if (this.selectedPlayerUuid != null) {
               this.partyManager.kickPlayer(this.playerRef.getUuid(), this.selectedPlayerUuid);
               this.selectedPlayerUuid = null;
               this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
               this.refreshUI(ref, store);
            }
            break;
         case "confirmTransfer":
            this.pendingConfirmAction = "transferLeadership";
            this.refreshUI(ref, store);
            break;
         case "accept":
            this.partyManager.acceptInvite(this.playerRef.getUuid());
            this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
            this.currentTab = PartyMenuPage.TabState.PARTY;
            this.refreshUI(ref, store);
            break;
         case "decline":
            this.partyManager.declineInvite(this.playerRef.getUuid());
            this.refreshUI(ref, store);
            break;
         default:
            this.refreshUI(ref, store);
      }
   }

   private void loadSettingsFromParty() {
      Party party = this.partyManager.getPartyByPlayer(this.playerRef.getUuid());
      if (party != null) {
         this.inputPartyName = party.getName();
         this.inputPassword = party.getPassword() != null ? party.getPassword() : "";
         this.selectedAccessType = party.getAccessType();
         this.selectedMaxMembers = party.getMaxMembers();
      }
   }

private void loadMySettingsFromConfig() {
        PartySettingsComponent settings = this.playerRef.getComponent(PartySettingsComponent.getComponentType());
        if (settings == null) {
           settings = new PartySettingsComponent();
        }
        this.mySettingsShowHud = settings.getShowHud();
        this.mySettingsShowSelf = settings.getShowSelf();
        this.mySettingsMaxDisplayed = settings.getMaxDisplayedMembers();
        this.mySettingsOrderMode = settings.getOrderMode();
     }

     private void saveMySettingsToConfig() {
        PartySettingsComponent settings = this.playerRef.getComponent(PartySettingsComponent.getComponentType());
        if (settings == null) {
           settings = new PartySettingsComponent();
        }
        settings.setShowHud(this.mySettingsShowHud);
        settings.setShowSelf(this.mySettingsShowSelf);
        settings.setMaxDisplayedMembers(this.mySettingsMaxDisplayed);
        settings.setOrderMode(this.mySettingsOrderMode);
        PartyPlayerListHud.getInstance().refreshHudForPlayer(this.playerRef.getUuid());
     }

   private void buildContent(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
      UUID playerUuid = this.playerRef.getUuid();
      Party party = this.partyManager.getPartyByPlayer(playerUuid);
      PartyInvite invite = this.partyManager.getPendingInvite(playerUuid);
      this.hideAllViews(cmd);
      cmd.set("#InviteSection.Visible", invite != null && this.currentView != PartyMenuPage.ViewState.INVITES_LIST_VIEW);
      boolean isInParty = party != null;
      boolean isLeader = party != null && party.isLeader(playerUuid);
      cmd.set("#TabBarContainer.Visible", isInParty && this.currentView != PartyMenuPage.ViewState.CONFIRMATION_VIEW);
      cmd.set("#RightTabBar.Visible", isLeader);
      if (isInParty) {
         String leftSelectedTab = switch (this.currentTab) {
            case PARTY -> "Party";
            case MY_SETTINGS -> "MySettings";
            case LEADER_SETTINGS -> "Party";
         };
         cmd.set("#LeftTabBar.SelectedTab", leftSelectedTab);
         if (this.currentTab == PartyMenuPage.TabState.LEADER_SETTINGS) {
            cmd.set("#RightTabBar.SelectedTab", "LeaderSettings");
         } else {
            cmd.set("#RightTabBar.SelectedTab", "");
         }
      }

      if (this.pendingConfirmAction != null) {
         this.buildConfirmView(cmd);
      } else {
         if (!isInParty) {
            if (this.currentView == PartyMenuPage.ViewState.PARTY_VIEW
               || this.currentView == PartyMenuPage.ViewState.SETTINGS_VIEW
               || this.currentView == PartyMenuPage.ViewState.MY_SETTINGS_VIEW
               || this.currentView == PartyMenuPage.ViewState.INVITE_VIEW
               || this.currentView == PartyMenuPage.ViewState.PLAYER_ACTION_VIEW
               || this.currentView == PartyMenuPage.ViewState.JOIN_REQUEST_VIEW) {
               this.currentView = PartyMenuPage.ViewState.NO_PARTY_VIEW;
            }
         } else if (this.currentView == PartyMenuPage.ViewState.NO_PARTY_VIEW
            || this.currentView == PartyMenuPage.ViewState.CREATE_PARTY_VIEW
            || this.currentView == PartyMenuPage.ViewState.INVITES_LIST_VIEW
            || this.currentView == PartyMenuPage.ViewState.ENTER_PASSWORD_VIEW) {
            this.currentView = PartyMenuPage.ViewState.PARTY_VIEW;
         }

          switch (this.currentView) {
             case NO_PARTY_VIEW:
                this.buildNoPartyView(cmd, events);
                break;
             case CREATE_PARTY_VIEW:
                this.buildCreatePartyView(cmd, events);
                break;
             case PARTY_VIEW:
                this.buildPartyView(cmd, events, playerUuid, party);
                break;
             case SETTINGS_VIEW:
                this.buildSettingsView(cmd, events, party);
                break;
             case MY_SETTINGS_VIEW:
                this.buildMySettingsView(cmd, events, party);
                break;
             case INVITE_VIEW:
                this.buildInvitePlayersView(cmd, events, playerUuid);
                break;
             case PLAYER_ACTION_VIEW:
                this.buildPlayerActionView(cmd, events, playerUuid, party);
                break;
             case INVITES_LIST_VIEW:
                this.buildInvitesListView(cmd, events, playerUuid);
                break;
             case ENTER_PASSWORD_VIEW:
                this.buildPasswordEntryView(cmd, events);
                break;
             case JOIN_REQUEST_VIEW:
                this.buildJoinRequestsView(cmd, events, party);
                break;
             case CONFIRMATION_VIEW:
                this.buildConfirmView(cmd);
                break;
          }
      }
   }

   private void hideAllViews(@Nonnull UICommandBuilder cmd) {
      cmd.set("#NoPartyView.Visible", false);
      cmd.set("#CreatePartyView.Visible", false);
      cmd.set("#PartyView.Visible", false);
      cmd.set("#SettingsView.Visible", false);
      cmd.set("#MySettingsView.Visible", false);
      cmd.set("#InvitePlayersView.Visible", false);
      cmd.set("#PlayerActionView.Visible", false);
      cmd.set("#InvitesListView.Visible", false);
      cmd.set("#PasswordEntryView.Visible", false);
      cmd.set("#JoinRequestsView.Visible", false);
      cmd.set("#ConfirmView.Visible", false);
   }

   private void buildConfirmView(@Nonnull UICommandBuilder cmd) {
      cmd.set("#ConfirmView.Visible", true);
      if ("leave".equals(this.pendingConfirmAction)) {
         cmd.set("#ConfirmTitle.Text", "Leave Party");
         cmd.set("#ConfirmMessage.Text", "Are you sure you want to leave the party?");
      } else if ("disband".equals(this.pendingConfirmAction)) {
         cmd.set("#ConfirmTitle.Text", "Disband Party");
         cmd.set("#ConfirmMessage.Text", "Are you sure you want to disband the party?");
      } else if ("transferLeadership".equals(this.pendingConfirmAction)) {
         String selectedName = this.selectedPlayerUuid != null ? Party.getPlayerName(this.selectedPlayerUuid) : "this player";
         cmd.set("#ConfirmTitle.Text", "Transfer Leadership");
         cmd.set("#ConfirmMessage.Text", "Make " + selectedName + " the new party leader?");
      }
   }

   private void buildNoPartyView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
      cmd.set("#NoPartyView.Visible", true);
      List<Party> publicParties = this.partyManager.getPublicParties();
      int index = 0;

      for (Party party : publicParties) {
         if (!this.partyManager.hasPendingJoinRequest(this.playerRef.getUuid(), party.getId())) {
            String selector = "#PublicPartiesList[" + index + "]";
            cmd.append("#PublicPartiesList", "Components/PublicPartyButton.ui");
            cmd.set(selector + " #PartyName.Text", party.getName());
            String leaderName = Party.getPlayerName(party.getLeaderUuid());
            cmd.set(selector + " #PartyLeader.Text", "Leader: " + leaderName);
            cmd.set(selector + " #MemberCount.Text", party.getMemberCount() + "/" + party.getMaxMembers());
            cmd.set(selector + " #AccessIcon.Visible", party.getAccessType() != PartyAccessType.OPEN);

            String eventAction = switch (party.getAccessType()) {
               case OPEN -> "joinOpenParty";
               case PASSWORDED -> "showPasswordEntry";
               case REQUEST_ONLY -> "sendJoinRequest";
               case LOCKED -> "none";
            };
            if (!eventAction.equals("none")) {
               events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", eventAction).append("Target", party.getId()), false);
            }

            index++;
         }
      }

      cmd.set("#NoPartiesLabel.Visible", index == 0);
      PartyInvite invite = this.partyManager.getPendingInvite(this.playerRef.getUuid());
      int inviteCount = invite != null ? 1 : 0;
      cmd.set("#ViewInvitesButton.Text", "Invites (" + inviteCount + ")");
   }

   private void buildCreatePartyView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
      cmd.set("#CreatePartyView.Visible", true);
      cmd.set("#CreateAccessOpenCheck.Visible", this.selectedAccessType == PartyAccessType.OPEN);
      cmd.set("#CreateAccessPasswordCheck.Visible", this.selectedAccessType == PartyAccessType.PASSWORDED);
      cmd.set("#CreateAccessRequestCheck.Visible", this.selectedAccessType == PartyAccessType.REQUEST_ONLY);
      cmd.set("#CreateAccessLockedCheck.Visible", this.selectedAccessType == PartyAccessType.LOCKED);
      cmd.set("#CreatePasswordGroup.Visible", this.selectedAccessType == PartyAccessType.PASSWORDED);
      cmd.set("#CreateMaxMembersValue.Text", String.valueOf(this.selectedMaxMembers));
   }

   private void buildPartyView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull UUID playerUuid, @Nonnull Party party) {
      cmd.set("#PartyView.Visible", true);
      boolean isLeader = party.isLeader(playerUuid);
      PartyRole playerRole = party.getRole(playerUuid);
      boolean canInvite = isLeader || playerRole.getLevel() >= PartyRole.MEMBER.getLevel();
      int memberCount = party.getMemberUuids().size();
      cmd.set("#PartyNameTitle.Text", party.getName());
      cmd.set("#MemberCount.Text", memberCount + "/" + party.getMaxMembers());
      cmd.set("#InvitePlayerButton.Visible", canInvite);
      List<PartyJoinRequest> requests = this.partyManager.getJoinRequests(party.getId());
      boolean showRequests = isLeader && party.getAccessType() == PartyAccessType.REQUEST_ONLY && !requests.isEmpty();
      cmd.set("#ViewRequestsButton.Visible", showRequests);
      if (showRequests) {
         cmd.set("#ViewRequestsButton.Text", "Requests (" + requests.size() + ")");
      }

      int index = 0;

      for (UUID memberUuid : party.getMemberUuids()) {
         String name = party.getMemberName(memberUuid);
         String status = party.getMemberStatus(memberUuid);
         boolean isOnline = Party.isPlayerOnline(memberUuid);
         String selector = "#PartyMembersList[" + index + "]";
         cmd.append("#PartyMembersList", "Components/PartyButton.ui");
         cmd.set(selector + " #Name.Text", name);
         cmd.set(selector + " #Subtext.Text", status);
         cmd.set(selector + " #StatusBadge.Visible", isOnline);
         if (isLeader && !memberUuid.equals(playerUuid)) {
            events.addEventBinding(
               CustomUIEventBindingType.Activating, selector, EventData.of("Action", "selectPlayer").append("Target", memberUuid.toString()), false
            );
         }

         index++;
      }
   }

   private void buildSettingsView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nullable Party party) {
      if (party != null) {
         cmd.set("#SettingsView.Visible", true);
         if (this.inputPartyName.isEmpty() || !this.inputPartyName.equals(party.getName())) {
            this.inputPartyName = party.getName();
            this.inputPassword = party.getPassword() != null ? party.getPassword() : "";
            this.selectedAccessType = party.getAccessType();
            this.selectedMaxMembers = party.getMaxMembers();
         }

         cmd.set("#SettingsNameInput.Value", this.inputPartyName);
         cmd.set("#SettingsPasswordInput.Value", this.inputPassword != null ? this.inputPassword : "");
         cmd.set("#AccessOpenCheck.Visible", this.selectedAccessType == PartyAccessType.OPEN);
         cmd.set("#AccessPasswordCheck.Visible", this.selectedAccessType == PartyAccessType.PASSWORDED);
         cmd.set("#AccessRequestCheck.Visible", this.selectedAccessType == PartyAccessType.REQUEST_ONLY);
         cmd.set("#AccessLockedCheck.Visible", this.selectedAccessType == PartyAccessType.LOCKED);
         cmd.set("#SettingsPasswordGroup.Visible", this.selectedAccessType == PartyAccessType.PASSWORDED);
         cmd.set("#MaxMembersValue.Text", String.valueOf(this.selectedMaxMembers));
      }
   }

   private void buildMySettingsView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nullable Party party) {
      cmd.set("#MySettingsView.Visible", true);
      if (this.mySettingsMaxDisplayed < 1 || this.mySettingsMaxDisplayed > 8) {
         this.loadMySettingsFromConfig();
      }

      cmd.set("#ShowHudToggle.Text", this.mySettingsShowHud ? "Enabled" : "Disabled");
      cmd.set("#ShowHudCheck.Visible", this.mySettingsShowHud);
      cmd.set("#ShowSelfToggle.Text", this.mySettingsShowSelf ? "Enabled" : "Disabled");
      cmd.set("#ShowSelfCheck.Visible", this.mySettingsShowSelf);
      cmd.set("#HudMaxValue.Text", String.valueOf(this.mySettingsMaxDisplayed));
      cmd.set("#OrderFixedCheck.Visible", this.mySettingsOrderMode == PartySettings.OrderMode.FIXED);
      cmd.set("#OrderDistanceCheck.Visible", this.mySettingsOrderMode == PartySettings.OrderMode.DISTANCE);
      boolean showWarning = party != null
         && party.getMemberCount() > this.mySettingsMaxDisplayed
         && this.mySettingsOrderMode != PartySettings.OrderMode.DISTANCE;
      cmd.set("#HudCapacityWarning.Visible", showWarning);
   }

   private void buildInvitePlayersView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull UUID playerUuid) {
      cmd.set("#InvitePlayersView.Visible", true);
      int index = 0;

      for (PlayerRef player : Universe.get().getPlayers()) {
         UUID otherUuid = player.getUuid();
         if (!otherUuid.equals(playerUuid) && !this.partyManager.isInParty(otherUuid)) {
            String name = player.getUsername();
            String selector = "#PlayersList[" + index + "]";
            cmd.append("#PlayersList", "Components/PartyButton.ui");
            cmd.set(selector + " #Name.Text", name);
            cmd.set(selector + " #Subtext.Text", "Invite");
            cmd.set(selector + " #StatusBadge.Visible", true);
            events.addEventBinding(
               CustomUIEventBindingType.Activating, selector, EventData.of("Action", "invite").append("Target", otherUuid.toString()), false
            );
            index++;
         }
      }

      if (index == 0) {
         cmd.append("#PlayersList", "Components/PartyButton.ui");
         cmd.set("#PlayersList[0] #Name.Text", "No players available");
         cmd.set("#PlayersList[0] #Subtext.Text", "");
      }
   }

   private void buildPlayerActionView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull UUID playerUuid, @Nullable Party party) {
      if (this.selectedPlayerUuid != null && party != null) {
         cmd.set("#PlayerActionView.Visible", true);
         String selectedName = party.getMemberName(this.selectedPlayerUuid);
         boolean isOnline = Party.isPlayerOnline(this.selectedPlayerUuid);
         PartyRole currentRole = party.getRole(this.selectedPlayerUuid);
         String roleDisplay = party.getRoleDisplayName(this.selectedPlayerUuid);
         cmd.set("#SelectedPlayerName.Text", selectedName);
         cmd.set("#SelectedPlayerRole.Text", roleDisplay);
         cmd.set("#SelectedPlayerBadge.Visible", isOnline);
         PartyRole nextRole = currentRole.getNextRole();
         if (nextRole != null) {
            cmd.set("#PromoteButton.Visible", true);
            cmd.set("#PromoteButton.Text", "Promote to " + nextRole.getDisplayName());
         } else {
            cmd.set("#PromoteButton.Visible", false);
         }

         PartyRole prevRole = currentRole.getPreviousRole();
         if (prevRole != null) {
            cmd.set("#DemoteButton.Visible", true);
            cmd.set("#DemoteButton.Text", "Demote to " + prevRole.getDisplayName());
         } else {
            cmd.set("#DemoteButton.Visible", false);
         }

         cmd.set("#KickPlayerButton.Visible", true);
         cmd.set("#TransferLeadershipButton.Visible", party.isLeader(playerUuid));
      }
   }

   private void buildInvitesListView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nonnull UUID playerUuid) {
      cmd.set("#InvitesListView.Visible", true);
      PartyInvite invite = this.partyManager.getPendingInvite(playerUuid);
      if (invite != null) {
         String selector = "#InvitesList[0]";
         cmd.append("#InvitesList", "Components/InviteEntryButton.ui");
         Party party = this.partyManager.getPartyById(invite.getPartyId());
         String partyName = party != null ? party.getName() : "Unknown Party";
         String inviterName = Party.getPlayerName(invite.getInviterUuid());
         cmd.set(selector + " #PartyName.Text", partyName);
         cmd.set(selector + " #InviterName.Text", "Invited by: " + inviterName);
         events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #QuickAccept", EventData.of("Action", "accept"), false);
         events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #QuickDecline", EventData.of("Action", "decline"), false);
      } else {
         cmd.append("#InvitesList", "Components/PartyButton.ui");
         cmd.set("#InvitesList[0] #Name.Text", "No invites");
         cmd.set("#InvitesList[0] #Subtext.Text", "");
      }
   }

   private void buildPasswordEntryView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events) {
      cmd.set("#PasswordEntryView.Visible", true);
      if (this.pendingJoinPartyId != null) {
         Party party = this.partyManager.getPartyById(this.pendingJoinPartyId);
         String partyName = party != null ? party.getName() : "Party";
         cmd.set("#PasswordPartyName.Text", partyName);
      }
   }

   private void buildJoinRequestsView(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder events, @Nullable Party party) {
      if (party != null) {
         cmd.set("#JoinRequestsView.Visible", true);
         List<PartyJoinRequest> requests = this.partyManager.getJoinRequests(party.getId());
         int index = 0;

         for (PartyJoinRequest request : requests) {
            String selector = "#RequestsList[" + index + "]";
            cmd.append("#RequestsList", "Components/RequestEntryButton.ui");
            String name = Party.getPlayerName(request.getRequesterUuid());
            cmd.set(selector + " #RequesterName.Text", name);
            events.addEventBinding(
               CustomUIEventBindingType.Activating,
               selector + " #AcceptRequest",
               EventData.of("Action", "acceptRequest").append("Target", request.getRequesterUuid().toString()),
               false
            );
            events.addEventBinding(
               CustomUIEventBindingType.Activating,
               selector + " #DeclineRequest",
               EventData.of("Action", "declineRequest").append("Target", request.getRequesterUuid().toString()),
               false
            );
            index++;
         }

         if (index == 0) {
            cmd.append("#RequestsList", "Components/PartyButton.ui");
            cmd.set("#RequestsList[0] #Name.Text", "No pending requests");
            cmd.set("#RequestsList[0] #Subtext.Text", "");
         }
      }
   }

   private void refreshUI(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
      UICommandBuilder cmd = new UICommandBuilder();
      UIEventBuilder events = new UIEventBuilder();
      cmd.clear("#PartyMembersList");
      cmd.clear("#PlayersList");
      cmd.clear("#PublicPartiesList");
      cmd.clear("#InvitesList");
      cmd.clear("#RequestsList");
      this.buildContent(cmd, events);
      this.sendUpdate(cmd, events, false);
   }

   private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
      Player player = (Player)store.getComponent(ref, Player.getComponentType());
      if (player != null) {
         player.getPageManager().setPage(ref, store, Page.None);
      }
   }

   private enum TabState {
      PARTY,
      MY_SETTINGS,
      LEADER_SETTINGS;
   }

   private enum ViewState {
      NO_PARTY_VIEW,
      CREATE_PARTY_VIEW,
      PARTY_VIEW,
      SETTINGS_VIEW,
      MY_SETTINGS_VIEW,
      INVITE_VIEW,
      PLAYER_ACTION_VIEW,
      INVITES_LIST_VIEW,
      ENTER_PASSWORD_VIEW,
      JOIN_REQUEST_VIEW,
      CONFIRMATION_VIEW;
   }
}
