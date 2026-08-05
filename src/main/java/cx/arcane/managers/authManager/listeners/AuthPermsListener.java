package cx.arcane.managers.authManager.listeners;

import cx.arcane.Arcane;
import cx.arcane.utils.Log;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.player.PlayerDataSaveEvent;
import net.luckperms.api.event.player.PlayerLoginProcessEvent;
import net.luckperms.api.event.player.lookup.UniqueIdDetermineTypeEvent;
import net.luckperms.api.event.player.lookup.UniqueIdLookupEvent;
import net.luckperms.api.event.player.lookup.UsernameLookupEvent;
import net.luckperms.api.event.player.lookup.UsernameValidityCheckEvent;
import net.luckperms.api.event.user.*;

public class AuthPermsListener {

    public static void subscribe() {
        EventBus eventBus = LuckPermsProvider.get().getEventBus();

        eventBus.subscribe(Arcane.getPlugin(), UniqueIdDetermineTypeEvent.class, AuthPermsListener::onUniqueIdDetermineType);
        eventBus.subscribe(Arcane.getPlugin(), UniqueIdLookupEvent.class, AuthPermsListener::onUniqueIdLookup);
        eventBus.subscribe(Arcane.getPlugin(), UsernameLookupEvent.class, AuthPermsListener::onUsernameLookup);
        eventBus.subscribe(Arcane.getPlugin(), UsernameValidityCheckEvent.class, AuthPermsListener::onUsernameValidityCheck);
        eventBus.subscribe(Arcane.getPlugin(), PlayerDataSaveEvent.class, AuthPermsListener::onPlayerDataSave);
        eventBus.subscribe(Arcane.getPlugin(), PlayerLoginProcessEvent.class, AuthPermsListener::onPlayerLoginProcess);
        eventBus.subscribe(Arcane.getPlugin(), UserCacheLoadEvent.class, AuthPermsListener::onUserCacheLoad);
        eventBus.subscribe(Arcane.getPlugin(), UserDataRecalculateEvent.class, AuthPermsListener::onUserDataRecalculate);
        eventBus.subscribe(Arcane.getPlugin(), UserFirstLoginEvent.class, AuthPermsListener::onUserFirstLogin);
        eventBus.subscribe(Arcane.getPlugin(), UserLoadEvent.class, AuthPermsListener::onUserLoad);
        eventBus.subscribe(Arcane.getPlugin(), UserUnloadEvent.class, AuthPermsListener::onUserUnload);
    }

    private static void onUniqueIdDetermineType(UniqueIdDetermineTypeEvent e) {
        Log.info("[LP] Event 'UniqueIdDetermineTypeEvent' called");
    }

    private static void onUniqueIdLookup(UniqueIdLookupEvent e) {
        Log.info("[LP] Event 'UniqueIdLookupEvent' called");
    }

    private static void onUsernameLookup(UsernameLookupEvent e) {
        Log.info("[LP] Event 'UsernameLookupEvent' called");
    }

    private static void onUsernameValidityCheck(UsernameValidityCheckEvent e) {
        Log.info("[LP] Event 'UsernameValidityCheckEvent' called");
    }

    private static void onPlayerDataSave(PlayerDataSaveEvent e) {
        Log.info("[LP] Event 'PlayerDataSaveEvent' called");
    }

    private static void onPlayerLoginProcess(PlayerLoginProcessEvent e) {
        Log.info("[LP] Event 'PlayerLoginProcessEvent' called");
    }

    private static void onUserCacheLoad(UserCacheLoadEvent e) {
        Log.info("[LP] Event 'UserCacheLoadEvent' called");
    }

    private static void onUserDataRecalculate(UserDataRecalculateEvent e) {
        Log.info("[LP] Event 'UserDataRecalculateEvent' called");
    }

    private static void onUserFirstLogin(UserFirstLoginEvent e) {
        Log.info("[LP] Event 'UserFirstLoginEvent' called");
    }

    private static void onUserLoad(UserLoadEvent e) {
        Log.info("[LP] Event 'UserLoadEvent' called");
    }

    private static void onUserUnload(UserUnloadEvent e) {
        Log.info("[LP] Event 'UserUnloadEvent' called");
    }
}