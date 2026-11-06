package dev.aetherwar.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

public class GameListener implements Listener {
    private final GameManager game;

    public GameListener(GameManager game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (!game.isRunning() || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            RiftNode n = game.nodeAt(b.getLocation());
            if (n != null) {
                e.setCancelled(true);
                game.hitNode(e.getPlayer(), n);
                return;
            }
            TeamColor core = game.coreAt(b.getLocation());
            if (core != null) {
                e.setCancelled(true);
                game.hitCore(e.getPlayer(), core);
            }
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && game.coreAt(b.getLocation()) != null) {
            e.setCancelled(true); // don't open the beacon UI
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!game.isRunning() || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (game.nodeAt(e.getBlock().getLocation()) != null || game.coreAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (game.isRunning() && game.coreAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!game.isRunning() || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        Entity d = e.getDamager();
        if (d instanceof Player p) attacker = p;
        else if (d instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker != null && attacker != victim) {
            if (!game.canPvp(attacker, victim)) {
                e.setCancelled(true);
                if (game.teamOf(attacker) != game.teamOf(victim))
                    attacker.sendActionBar(Component.text(pvpReason(), NamedTextColor.GRAY));
                return;
            }
        }
        e.setDamage(e.getDamage() * game.baseDamageMultiplier(victim));
    }

    private String pvpReason() {
        return switch (game.phase()) {
            case EXPLORE -> "1페이즈에는 PvP가 꺼져 있어요.";
            case EXPAND -> "2페이즈에는 중립 지역에서만 싸울 수 있어요.";
            case CLASH -> "";
        };
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!game.isRunning() || game.teamOf(e.getPlayer()) == null) return;
        Player p = e.getPlayer();
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        int dropped = game.dropHalfCrystals(p);
        if (dropped > 0) p.sendMessage(Component.text("결정 " + dropped + "개를 떨궜어요.", NamedTextColor.RED));
        Player killer = p.getKiller();
        if (killer != null && game.teamOf(killer) != game.teamOf(p)) game.addContribution(killer, 15);
        game.scheduleRespawn(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!game.isRunning() || game.teamOf(e.getPlayer()) == null) return;
        var s = game.spawnOf(e.getPlayer());
        if (s != null) e.setRespawnLocation(s);
        e.getPlayer().setGameMode(GameMode.SPECTATOR);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!game.isRunning() || !game.isGuardian(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        game.guardianDied(e.getEntity(), e.getEntity().getKiller());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        game.hud().show(e.getPlayer());
        if (!game.isRunning()) return;
        TeamColor t = game.teamOf(e.getPlayer());
        if (t == null) {
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
            e.getPlayer().sendMessage(Component.text("경기가 진행 중이라 관전 모드로 들어왔어요.", NamedTextColor.GRAY));
        }
    }

}
