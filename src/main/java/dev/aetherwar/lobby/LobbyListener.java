package dev.aetherwar.lobby;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.skill.PlayerClass;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class LobbyListener implements Listener {
    private final GameManager game;
    private final Lobby lobby;

    public LobbyListener(GameManager game, Lobby lobby) {
        this.game = game;
        this.lobby = lobby;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction i)) return;
        PlayerClass c = lobby.pedestal(i);
        if (c == null) return;
        e.setCancelled(true);
        if (game.isRunning()) return;
        lobby.chooseClass(e.getPlayer(), c);
    }

    /** Left click on a pedestal counts too. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHitPedestal(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Interaction i) || !(e.getDamager() instanceof Player p)) return;
        PlayerClass c = lobby.pedestal(i);
        if (c == null) return;
        e.setCancelled(true);
        if (!game.isRunning()) lobby.chooseClass(p, c);
    }

    /** Nobody gets hurt or hungry between matches. */
    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && !game.isRunning()) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!game.isRunning()) e.setCancelled(true);
    }
}
