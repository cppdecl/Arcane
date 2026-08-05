package cx.arcane.managers.interactionManager.listeners;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import cx.arcane.utils.Log;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.util.concurrent.ConcurrentHashMap;

public class InteractionListener implements Listener {

    private static ConcurrentHashMap<Location, Player> CRYSTALS = new ConcurrentHashMap<>();

}
