package dev.aetherwar.game;

import dev.aetherwar.AetherWar;
import dev.aetherwar.arena.Arena;
import dev.aetherwar.arena.NodeType;
import dev.aetherwar.item.Items;
import dev.aetherwar.item.Sfx;
import dev.aetherwar.skill.PlayerData;
import dev.aetherwar.skill.Skill;
import dev.aetherwar.skill.SkillManager;
import dev.aetherwar.skill.Stat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Owns one match: teams, phases, scores, nodes, cores. Ticks once per second. */
public class GameManager {
    public enum State { WAITING, RUNNING, ENDED }

    private final AetherWar plugin;
    private final Arena arena;
    private final Hud hud;
    private final Random random = new Random();

    private State state = State.WAITING;
    private long elapsed = 0; // seconds
    private Phase phase = Phase.EXPLORE;
    private BukkitTask task;

    private final Map<UUID, TeamColor> teams = new HashMap<>();
    private final Map<TeamColor, Integer> ether = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Integer> contribution = new HashMap<>();
    private final List<RiftNode> nodes = new ArrayList<>();
    private final Set<UUID> guardians = new HashSet<>();
    private final Map<UUID, Long> respawnAt = new HashMap<>();
    private boolean greatRiftOpen = false;

    // core raid state
    private final Map<TeamColor, Long> shieldUntil = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Integer> raidBase = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Integer> raidStolen = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Long> coreHitCooldown = new HashMap<>();

    public SkillManager skills;
    public dev.aetherwar.lobby.Lobby lobby;
    public Boss boss;
    public final Map<UUID, TeamColor> golemTeam = new HashMap<>();
    public final Map<UUID, Long> stealthUntil = new HashMap<>();
    private final Map<TeamColor, Location> forwardSpawn = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Long> forwardUntil = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Long> alarmCooldown = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Long> bossBuffUntil = new EnumMap<>(TeamColor.class);

    public GameManager(AetherWar plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.hud = new Hud(this);
        this.skills = new SkillManager(plugin, this);
        skills.actives = new dev.aetherwar.skill.ActiveSkills(skills);
        skills.deploy = new dev.aetherwar.skill.Deployables(skills);
    }

    public Random random() { return random; }

    // ---------------------------------------------------------------- config helpers

    private int cfgInt(String path, int def) {
        return plugin.getConfig().getInt(path, def);
    }

    public int phaseSeconds() {
        return cfgInt("game.phase-minutes", 30) * 60;
    }

    public long totalSeconds() {
        return 3L * phaseSeconds();
    }

    public int targetEther() {
        return cfgInt("game.target-ether", 1000);
    }

    // ---------------------------------------------------------------- getters

    public State state() { return state; }
    public Phase phase() { return phase; }
    public long elapsed() { return elapsed; }
    public Arena arena() { return arena; }
    public dev.aetherwar.AetherWar plugin() { return plugin; }
    public boolean isRunning() { return state == State.RUNNING; }
    public TeamColor teamOf(Player p) { return teams.get(p.getUniqueId()); }
    public int ether(TeamColor t) { return ether.getOrDefault(t, 0); }
    public int contribution(Player p) { return contribution.getOrDefault(p.getUniqueId(), 0); }
    public int skillPoints(Player p) { return contribution(p) / cfgInt("game.contribution-per-point", 100); }
    public boolean greatRiftOpen() { return greatRiftOpen; }
    public List<RiftNode> nodes() { return nodes; }
    public boolean isGuardian(Entity e) { return guardians.contains(e.getUniqueId()); }

    public long shieldLeft(TeamColor t) {
        return Math.max(0, shieldUntil.getOrDefault(t, 0L) - elapsed);
    }

    public List<Player> members(TeamColor t) {
        List<Player> out = new ArrayList<>();
        for (Map.Entry<UUID, TeamColor> e : teams.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && e.getValue() == t) out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------- teams

    public boolean joinTeam(Player p, TeamColor t) {
        if (state == State.RUNNING) return false;
        teams.put(p.getUniqueId(), t);
        hud.applyTeam(p, t);
        return true;
    }

    private void autoAssign() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teams.containsKey(p.getUniqueId()) || p.getGameMode() == GameMode.SPECTATOR) continue;
            TeamColor smaller = members(TeamColor.RED).size() <= members(TeamColor.BLUE).size() ? TeamColor.RED : TeamColor.BLUE;
            joinTeam(p, smaller);
        }
    }

    // ---------------------------------------------------------------- lifecycle

    public String start() {
        if (state == State.RUNNING) return "이미 경기가 진행 중이에요.";
        if (!arena.isReady()) return "맵 설정이 덜 됐어요: " + arena.missing() + ". /aw buildmap 으로 테스트 맵을 만들 수 있어요.";
        reset();
        autoAssign();
        if (teams.isEmpty()) return "참가자가 없어요.";

        for (Arena.NodeDef def : arena.nodes) {
            RiftNode n = new RiftNode(def);
            n.setActive(def.type() != NodeType.GREAT);
            if (def.type() == NodeType.GREAT) n.blockLoc().getBlock().setType(Material.OBSIDIAN);
            n.label = spawnLabel(n.blockLoc().clone().add(0.5, 1.6, 0.5));
            nodes.add(n);
        }
        for (TeamColor t : TeamColor.values()) ether.put(t, 0);

        state = State.RUNNING;
        phase = Phase.EXPLORE;
        List<Player> players = new ArrayList<>();
        for (UUID id : teams.keySet()) { Player p = Bukkit.getPlayer(id); if (p != null) players.add(p); }
        skills.autoAssign(players);
        for (Player p : players) {
            PlayerData d = skills.data(p);
            d.reset();
            d.augments.clear();
            d.cooldownUntil.clear();
            d.respecPhase = -1;
            d.phoenixPhase = -1;
            d.pendingAugments = List.of();
            preparePlayer(p, teamOf(p));
            skills.onSkillsChanged(p);
        }
        boss = new Boss(plugin, this);
        hud.start();
        broadcastTitle(Component.text("균열 수확전", NamedTextColor.LIGHT_PURPLE), Component.text("1페이즈 탐험 시작 · PvP 꺼짐", NamedTextColor.GRAY));
        broadcast(Component.text("균열 노드(자수정 블록)를 3번 치면 결정이 나와요. 결정을 코어 근처로 가져가면 납품돼요.", NamedTextColor.GRAY));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return null;
    }

    private void preparePlayer(Player p, TeamColor t) {
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(10);
        p.teleport(arena.spawns.get(t));
        dev.aetherwar.skill.PlayerClass c = skills.classOf(p);
        switch (c) {
            case WARRIOR -> p.getInventory().addItem(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 32));
            case MINER -> p.getInventory().addItem(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.DIAMOND_PICKAXE), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 16));
            case ALCHEMIST -> p.getInventory().addItem(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.CROSSBOW), new ItemStack(Material.ARROW, 16));
            case BUILDER -> p.getInventory().addItem(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 32));
        }
        if (c == dev.aetherwar.skill.PlayerClass.WARRIOR) p.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        p.getInventory().setItem(5, new ItemStack(Material.BREAD, 16));
        p.sendMessage(Component.text("직업: " + c.displayName + " · " + c.role, c.color));
        p.sendMessage(Component.text("나침반(9번 칸)을 우클릭하면 스킬트리가 열려요. 처음에 1포인트가 있어요.", NamedTextColor.GRAY));
        p.getInventory().setHelmet(dyed(Material.LEATHER_HELMET, t));
        p.getInventory().setChestplate(dyed(Material.LEATHER_CHESTPLATE, t));
        p.getInventory().setLeggings(dyed(Material.LEATHER_LEGGINGS, t));
        p.getInventory().setBoots(dyed(Material.LEATHER_BOOTS, t));
    }

    private static ItemStack dyed(Material m, TeamColor t) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(t.armorColor);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    public void stop(TeamColor winner) {
        if (state != State.RUNNING) return;
        state = State.ENDED;
        if (task != null) task.cancel();
        Component title = winner == null
                ? Component.text("무승부", NamedTextColor.YELLOW)
                : Component.text(winner.displayName + " 승리!", winner.textColor);
        broadcastTitle(title, Component.text("홍팀 " + ether(TeamColor.RED) + " : " + ether(TeamColor.BLUE) + " 청팀", NamedTextColor.GRAY));
        broadcast(Component.text("— 기여도 순위 —", NamedTextColor.GOLD));
        contribution.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> {
                    String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    TeamColor t = teams.get(e.getKey());
                    broadcast(Component.text(" " + name + "  ", t == null ? NamedTextColor.WHITE : t.textColor)
                            .append(Component.text(e.getValue() + "점", NamedTextColor.GRAY)));
                });
        for (Player p : Bukkit.getOnlinePlayers()) Sfx.play(p, "victory", 1f, 1f);
        cleanupWorld();
        hud.stop();
        if (lobby != null) lobby.returnAfter(plugin.getConfig().getInt("lobby.return-seconds", 20));
    }

    /** Ends the match without a result (admin stop). */
    public void abort() {
        if (state != State.RUNNING) return;
        state = State.ENDED;
        if (task != null) task.cancel();
        broadcast(Component.text("경기가 중단됐어요.", NamedTextColor.RED));
        cleanupWorld();
        hud.stop();
        if (lobby != null && plugin.isEnabled()) lobby.sendAll();
    }

    private void cleanupWorld() {
        if (skills.deploy != null) skills.deploy.clearAll();
        if (boss != null) boss.remove();
        for (UUID id : golemTeam.keySet()) { Entity g = Bukkit.getEntity(id); if (g != null) g.remove(); }
        golemTeam.clear();
        for (Player p : Bukkit.getOnlinePlayers()) { skills.actives.reveal(p); skills.clearAttributes(p); }
        forwardSpawn.clear();
        for (RiftNode n : nodes) {
            if (n.label != null) n.label.remove();
            n.setActive(true);
        }
        for (UUID id : guardians) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        guardians.clear();
    }

    private void reset() {
        elapsed = 0;
        phase = Phase.EXPLORE;
        ether.clear();
        contribution.clear();
        nodes.clear();
        guardians.clear();
        respawnAt.clear();
        shieldUntil.clear();
        raidBase.clear();
        raidStolen.clear();
        coreHitCooldown.clear();
        greatRiftOpen = false;
        // forget players who left before the match
        teams.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    // ---------------------------------------------------------------- admin time controls

    public void skipPhase() {
        if (!isRunning()) return;
        long next = (phase.ordinal() + 1L) * phaseSeconds();
        elapsed = Math.min(next, totalSeconds()) - 1;
        tick();
    }

    public void setElapsedMinutes(int minutes) {
        if (!isRunning()) return;
        elapsed = Math.max(0, Math.min(totalSeconds() - 1, minutes * 60L)) - 1;
        tick();
    }

    // ---------------------------------------------------------------- the tick

    private void tick() {
        if (state != State.RUNNING) return;
        elapsed++;

        Phase newPhase = Phase.values()[(int) Math.min(2, elapsed / phaseSeconds())];
        if (newPhase != phase) {
            phase = newPhase;
            broadcastTitle(Component.text((phase.ordinal() + 1) + "페이즈 " + phase.displayName, NamedTextColor.GOLD),
                    Component.text(phase.rule, NamedTextColor.GRAY));
            for (Player p : Bukkit.getOnlinePlayers()) Sfx.play(p, "phase", 1f, 1f);
            offerAugments();
            if (phase == Phase.CLASH && boss != null) boss.spawn();
        }

        // rift surges during phase 2
        int surgeEvery = cfgInt("game.surge-every-minutes", 10) * 60;
        long sincePhase2 = elapsed - phaseSeconds();
        if (phase == Phase.EXPAND && sincePhase2 > 0 && sincePhase2 % surgeEvery == 300 % surgeEvery) surge();

        // great rift opens for the last N minutes
        long openAt = totalSeconds() - cfgInt("game.great-rift-minutes", 10) * 60L;
        if (!greatRiftOpen && elapsed >= openAt) openGreatRift();
        if (!greatRiftOpen && boss != null && boss.defeated()) openGreatRift();

        respawnTick();
        nodeTick();
        ambience();
        skills.tick();
        if (boss != null) boss.tick();
        baseAlarmTick();
        augmentTick();
        depositTick();
        hud.update();

        TeamColor winner = winnerByTarget();
        if (winner != null) {
            stop(winner);
        } else if (elapsed >= totalSeconds()) {
            int r = ether(TeamColor.RED), b = ether(TeamColor.BLUE);
            stop(r == b ? null : (r > b ? TeamColor.RED : TeamColor.BLUE));
        }
    }

    private TeamColor winnerByTarget() {
        for (TeamColor t : TeamColor.values()) if (ether(t) >= targetEther()) return t;
        return null;
    }

    // ---------------------------------------------------------------- nodes

    /** Soft particles so nodes and cores read from a distance. */
    private void ambience() {
        for (RiftNode n : nodes) {
            if (n.readyAt > 0 || (n.type() == NodeType.GREAT && !greatRiftOpen)) continue;
            Location c = n.blockLoc().clone().add(0.5, 0.6, 0.5);
            Particle.DustOptions dust = new Particle.DustOptions(
                    n.owner != null ? n.owner.armorColor : (n.type().safeFor != null ? n.type().safeFor.armorColor : org.bukkit.Color.fromRGB(0xB98CFF)), 1.1f);
            c.getWorld().spawnParticle(Particle.END_ROD, c, 2, 0.35, 0.4, 0.35, 0.01);
            c.getWorld().spawnParticle(Particle.DUST, c, 4, 0.5, 0.5, 0.5, 0, dust);
        }
        double a = (elapsed % 20) / 20.0 * Math.PI * 2;
        for (Map.Entry<TeamColor, Location> e : arena.cores.entrySet()) {
            Location core = e.getValue().clone().add(0.5, 0.5, 0.5);
            Particle.DustOptions dust = new Particle.DustOptions(e.getKey().armorColor, 1.4f);
            for (int i = 0; i < 6; i++) {
                double t = a + i * Math.PI / 3;
                core.getWorld().spawnParticle(Particle.DUST, core.clone().add(Math.cos(t) * 2.2, 0.2 + (i % 2) * 0.6, Math.sin(t) * 2.2), 1, 0, 0, 0, 0, dust);
            }
            if (shieldLeft(e.getKey()) > 0) core.getWorld().spawnParticle(Particle.ENCHANT, core, 30, 1.5, 1.2, 1.5, 0.5);
        }
    }

    private TextDisplay spawnLabel(Location loc) {
        return loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setSeeThrough(false);
        });
    }

    private void nodeTick() {
        int captureSeconds = cfgInt("game.capture-seconds", 8);
        for (RiftNode n : nodes) {
            // respawn depleted nodes
            if (n.readyAt > 0 && elapsed >= n.readyAt && (n.type() != NodeType.GREAT || greatRiftOpen)) {
                n.readyAt = 0;
                n.setActive(true);
                n.blockLoc().getWorld().spawnParticle(Particle.END_ROD, n.blockLoc().clone().add(0.5, 1, 0.5), 8, 0.3, 0.3, 0.3, 0.02);
            }
            // capture contested nodes: stand within 4 blocks with no enemy present
            if (n.type() == NodeType.CONTESTED && phase != Phase.EXPLORE) {
                Set<TeamColor> present = new HashSet<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    TeamColor t = teamOf(p);
                    if (t == null || p.getGameMode() == GameMode.SPECTATOR) continue;
                    if (p.getWorld() == n.blockLoc().getWorld() && p.getLocation().distanceSquared(n.blockLoc()) <= 16) present.add(t);
                }
                if (present.size() == 1) {
                    TeamColor t = present.iterator().next();
                    if (t != n.owner) {
                        if (n.capturingTeam != t) { n.capturingTeam = t; n.captureProgress = 0; }
                        double speed = 1;
                        for (Player m : members(t)) if (m.getWorld() == n.blockLoc().getWorld() && m.getLocation().distanceSquared(n.blockLoc()) <= 16)
                            speed = Math.max(speed, 1 + skills.stat(m, Stat.CAPTURE_SPEED));
                        n.captureProgress += (int) Math.round(speed);
                        if (n.captureProgress >= captureSeconds) {
                            n.owner = t;
                            n.captureProgress = 0;
                            n.capturingTeam = null;
                            broadcast(Component.text(t.displayName + "이 점령 노드를 차지했어요!", t.textColor));
                            for (Player p : members(t)) { addContribution(p, 20); Sfx.play(p, "capture", 1f, 1f); }
                        }
                    }
                } else if (present.isEmpty()) {
                    n.captureProgress = 0;
                    n.capturingTeam = null;
                }
            }
            updateLabel(n);
        }
    }

    private void updateLabel(RiftNode n) {
        if (n.label == null || !n.label.isValid()) return;
        Component c;
        switch (n.type()) {
            case SAFE_RED, SAFE_BLUE -> c = Component.text(n.type().safeFor.displayName + " 안전 노드", n.type().safeFor.textColor);
            case GREAT -> c = greatRiftOpen
                    ? Component.text("대균열 · 결정 2배", NamedTextColor.LIGHT_PURPLE)
                    : Component.text("대균열 · 마지막 " + cfgInt("game.great-rift-minutes", 10) + "분에 열림", NamedTextColor.DARK_PURPLE);
            default -> {
                Component owner = n.owner == null ? Component.text("중립", NamedTextColor.WHITE) : Component.text(n.owner.displayName, n.owner.textColor);
                c = Component.text("점령 노드 · ", NamedTextColor.GRAY).append(owner);
                if (n.capturingTeam != null && n.captureProgress > 0)
                    c = c.append(Component.text(" (" + n.capturingTeam.displayName + " 점령 중 " + n.captureProgress + "/" + cfgInt("game.capture-seconds", 8) + ")", n.capturingTeam.textColor));
                if (phase == Phase.EXPLORE) c = c.append(Component.text(" · 2페이즈부터 점령", NamedTextColor.DARK_GRAY));
            }
        }
        if (n.readyAt > 0 && (n.type() != NodeType.GREAT || greatRiftOpen))
            c = c.append(Component.newline()).append(Component.text("재생까지 " + (n.readyAt - elapsed) + "초", NamedTextColor.DARK_GRAY));
        n.label.text(c);
    }

    public RiftNode nodeAt(Location blockLoc) {
        for (RiftNode n : nodes) if (n.isAt(blockLoc)) return n;
        return null;
    }

    /** Called when a player left-clicks a node block. */
    public void hitNode(Player p, RiftNode n) {
        TeamColor t = teamOf(p);
        if (t == null || n.readyAt > 0) return;
        if (n.type().safeFor != null && n.type().safeFor != t) {
            p.sendActionBar(Component.text("상대 팀의 안전 노드는 캘 수 없어요.", NamedTextColor.RED));
            return;
        }
        if (n.type() == NodeType.GREAT && !greatRiftOpen) {
            p.sendActionBar(Component.text("대균열은 아직 닫혀 있어요.", NamedTextColor.DARK_PURPLE));
            return;
        }
        int needed = Math.max(1, cfgInt("game.hits-per-harvest", 3) - (int) skills.stat(p, Stat.MINE_HITS_REDUCE));
        int hits = n.hits.merge(p.getUniqueId(), 1, Integer::sum);
        Location fx = n.blockLoc().clone().add(0.5, 0.5, 0.5);
        fx.getWorld().playSound(fx, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 0.8f + 0.2f * hits);
        if (hits < needed) {
            p.sendActionBar(Component.text("채굴 " + hits + "/" + needed, NamedTextColor.LIGHT_PURPLE));
            return;
        }
        n.hits.clear();
        int amount = harvestAmount(p, n);
        giveCrystals(p, amount);
        fx.getWorld().playSound(fx, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.6f, 1.2f);
        Sfx.at(fx, "harvest", 1f, 1f);
        fx.getWorld().spawnParticle(Particle.WITCH, fx, 15, 0.4, 0.4, 0.4, 0.05);
        p.sendActionBar(Component.text("에테르 결정 +" + amount, NamedTextColor.LIGHT_PURPLE));

        long cooldown = Math.round(cfgInt("game.node-respawn-seconds", 20) * Math.max(0.3, 1 - skills.stat(p, Stat.NODE_CD_REDUCE)));
        // enemy-owned contested node: half mining speed
        if (n.type() == NodeType.CONTESTED && n.owner != null && n.owner != t) cooldown *= 2;
        n.readyAt = elapsed + cooldown;
        n.setActive(false);
        if (n.type() == NodeType.GREAT) n.blockLoc().getBlock().setType(Material.OBSIDIAN);
    }

    /** How many crystals one harvest gives, with skills applied. */
    private int harvestAmount(Player p, RiftNode n) {
        int amount = n.type() == NodeType.GREAT ? 2 : 1;
        if (n.type() == NodeType.GREAT || n.type() == NodeType.CONTESTED) amount += (int) skills.stat(p, Stat.RICH_NODE_BONUS);
        if (random.nextDouble() < skills.stat(p, Stat.DOUBLE_DROP)) amount *= 2;
        if (random.nextDouble() < skills.stat(p, Stat.JACKPOT_CHANCE)) { amount += 3; p.sendMessage(Component.text("숨은 광맥 발견! 결정 +3", NamedTextColor.GOLD)); }
        return amount;
    }

    /** Gives freshly mined crystals with mining contribution and the related passives. */
    private void giveCrystals(Player p, int amount) {
        if (random.nextDouble() < skills.stat(p, Stat.REFINED_CHANCE)) {
            p.getInventory().addItem(Items.refined(1));
            p.sendMessage(Component.text("행운의 손: 정제 결정!", NamedTextColor.AQUA));
        }
        p.getInventory().addItem(Items.crystal(amount)).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        addContribution(p, (int) Math.round(3 * amount * (1 + skills.stat(p, Stat.MINE_CONTRIB_MULT))));
        if (random.nextDouble() < skills.stat(p, Stat.INSTANT_DELIVER_CHANCE)) {
            p.sendMessage(Component.text("광산 수레: 결정이 바로 코어로!", NamedTextColor.GOLD));
            deposit(p);
        }
    }

    public int refreshNodesNear(Location c, double r) {
        int n = 0;
        for (RiftNode node : nodes) {
            if (node.readyAt == 0 || node.blockLoc().getWorld() != c.getWorld() || node.blockLoc().distanceSquared(c) > r * r) continue;
            if (node.type() == NodeType.GREAT && !greatRiftOpen) continue;
            node.readyAt = elapsed;
            n++;
        }
        return n;
    }

    public int harvestNodesNear(Player p, Location c, double r) {
        TeamColor t = teamOf(p);
        int n = 0;
        for (RiftNode node : nodes) {
            if (node.readyAt > 0 || node.blockLoc().getWorld() != c.getWorld() || node.blockLoc().distanceSquared(c) > r * r) continue;
            if (node.type().safeFor != null && node.type().safeFor != t) continue;
            if (node.type() == NodeType.GREAT && !greatRiftOpen) continue;
            giveCrystals(p, harvestAmount(p, node));
            node.readyAt = elapsed + cfgInt("game.node-respawn-seconds", 20);
            node.setActive(false);
            n++;
        }
        return n;
    }

    public boolean blastNode(Player p, RiftNode node, int crystals, int lockSeconds) {
        TeamColor t = teamOf(p);
        if (node.readyAt > 0 || (node.type().safeFor != null && node.type().safeFor != t) || (node.type() == NodeType.GREAT && !greatRiftOpen)) return false;
        Location fx = node.blockLoc().clone().add(0.5, 1, 0.5);
        for (int i = 0; i < crystals; i++) fx.getWorld().dropItemNaturally(fx, Items.crystal(1));
        fx.getWorld().spawnParticle(Particle.EXPLOSION, fx, 2);
        fx.getWorld().playSound(fx, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
        node.readyAt = elapsed + lockSeconds;
        node.setActive(false);
        addContribution(p, 3 * crystals);
        return true;
    }

    /** Action bar arrow toward the closest node this player can mine. */
    public void showNodeCompass(Player p) {
        TeamColor t = teamOf(p);
        RiftNode best = null;
        double bd = Double.MAX_VALUE;
        for (RiftNode n : nodes) {
            if (n.readyAt > 0 || (n.type().safeFor != null && n.type().safeFor != t) || (n.type() == NodeType.GREAT && !greatRiftOpen)) continue;
            if (n.blockLoc().getWorld() != p.getWorld()) continue;
            double d = n.blockLoc().distanceSquared(p.getLocation());
            if (d < bd) { bd = d; best = n; }
        }
        if (best == null || bd < 9) return;
        org.bukkit.util.Vector to = best.blockLoc().toVector().subtract(p.getLocation().toVector()).setY(0);
        double ang = Math.toDegrees(Math.atan2(-to.getX(), to.getZ())) - p.getLocation().getYaw();
        ang = ((ang % 360) + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        String a = arrows[(int) Math.round(((ang + 360) % 360) / 45.0) % 8];
        p.sendActionBar(Component.text("노드 " + a + " " + (int) Math.sqrt(bd) + "m", NamedTextColor.LIGHT_PURPLE));
    }

    public boolean isBoss(Entity e) {
        return boss != null && boss.is(e);
    }

    public void setForwardSpawn(TeamColor t, Location l, int seconds) {
        forwardSpawn.put(t, l);
        forwardUntil.put(t, elapsed + seconds);
        for (Player m : members(t)) m.sendMessage(Component.text("전진 기지 설치! " + seconds + "초 동안 여기서 부활해요.", t.textColor));
    }

    public void shieldCore(TeamColor t, int seconds) {
        shieldUntil.put(t, Math.max(shieldUntil.getOrDefault(t, 0L), elapsed + seconds));
        raidBase.remove(t);
        raidStolen.remove(t);
        broadcast(Component.text(t.displayName + " 코어에 보호막 (" + seconds + "초)", NamedTextColor.AQUA));
    }

    public void addEther(TeamColor t, int amount) {
        ether.merge(t, amount, Integer::sum);
    }

    public void setBossBuff(TeamColor t, int seconds) {
        bossBuffUntil.put(t, elapsed + seconds);
    }

    public boolean hasBossBuff(TeamColor t) {
        return bossBuffUntil.getOrDefault(t, 0L) > elapsed;
    }

    private void baseAlarmTick() {
        for (TeamColor t : TeamColor.values()) {
            if (skills.teamStat(t, Stat.BASE_ALARM) <= 0) continue;
            for (Player e : members(t.other())) {
                if (e.getGameMode() == GameMode.SPECTATOR || arena.baseAt(e.getLocation()) != t) continue;
                e.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.GLOWING, 40, 0));
                if (alarmCooldown.getOrDefault(t, 0L) <= elapsed) {
                    alarmCooldown.put(t, elapsed + 15);
                    for (Player m : members(t)) { m.sendMessage(Component.text("⚠ 적이 기지에 들어왔어요! (" + e.getName() + ")", NamedTextColor.RED)); Sfx.play(m, "core_hit", 0.6f, 0.8f); }
                }
            }
        }
    }

    // ---------------------------------------------------------------- rift augments

    /** Offers every player 3 random augments to pick one from. */
    public void offerAugments() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamOf(p) == null) continue;
            PlayerData d = skills.data(p);
            List<Skill> roll = skills.rollAugments(p);
            if (roll.isEmpty()) continue;
            d.pendingAugments = roll.stream().map(x -> x.id).toList();
            d.pendingSince = elapsed;
            dev.aetherwar.gui.AugmentMenu.open(this, p);
        }
    }

    private void augmentTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamOf(p) == null) continue;
            PlayerData d = skills.data(p);
            if (d.pendingAugments.isEmpty()) continue;
            if (elapsed - d.pendingSince >= 30) {
                Skill pick = dev.aetherwar.skill.SkillRegistry.get(d.pendingAugments.get(random.nextInt(d.pendingAugments.size())));
                skills.addAugment(p, pick);
                p.sendMessage(Component.text("균열 각인이 자동으로 정해졌어요: " + pick.name, NamedTextColor.LIGHT_PURPLE));
            } else if ((elapsed - d.pendingSince) % 10 == 5) {
                p.sendMessage(Component.text("균열 각인을 아직 안 골랐어요. ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text("[클릭해서 고르기]", NamedTextColor.GOLD).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/aw augment"))));
            }
        }
    }

    private void openGreatRift() {
        greatRiftOpen = true;
        for (RiftNode n : nodes) if (n.type() == NodeType.GREAT) { n.readyAt = 0; n.setActive(true); }
        broadcast(Component.text("✦ 대균열이 열렸어요! 중앙 노드에서 결정이 2배로 나와요.", NamedTextColor.LIGHT_PURPLE));
        broadcastTitle(Component.text("대균열이 열렸다!", NamedTextColor.LIGHT_PURPLE), Component.text("중앙 노드에서 결정이 2배로 나와요", NamedTextColor.GRAY));
        for (Player p : Bukkit.getOnlinePlayers()) Sfx.play(p, "rift_open", 1f, 1f);
    }

    // ---------------------------------------------------------------- surges & guardians

    private void surge() {
        List<RiftNode> contested = nodes.stream().filter(n -> n.type() == NodeType.CONTESTED).toList();
        if (contested.isEmpty()) return;
        RiftNode n = contested.get(random.nextInt(contested.size()));
        n.readyAt = 0;
        n.setActive(true);
        Location at = n.blockLoc().clone().add(0.5, 1, 1.5);
        Zombie z = at.getWorld().spawn(at, Zombie.class, e -> {
            e.customName(Component.text("균열 수호자", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
            e.setCustomNameVisible(true);
            e.setAdult();
            e.setShouldBurnInDay(false);
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(cfgInt("game.guardian-health", 60));
            e.setHealth(cfgInt("game.guardian-health", 60));
            e.getEquipment().setHelmet(new ItemStack(Material.AMETHYST_BLOCK));
            e.getEquipment().setHelmetDropChance(0f);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
        });
        guardians.add(z.getUniqueId());
        offerAugments();
        broadcastTitle(Component.text("균열 폭주!", NamedTextColor.DARK_PURPLE), Component.text("점령 노드에 균열 수호자가 나타났어요", NamedTextColor.GRAY));
        for (Player p : Bukkit.getOnlinePlayers()) Sfx.play(p, "surge", 1f, 1f);
    }

    /** A guardian died: drop crystals and reward the killer's nearby team. */
    public void guardianDied(Entity e, Player killer) {
        guardians.remove(e.getUniqueId());
        int min = cfgInt("game.guardian-drop-min", 5), max = cfgInt("game.guardian-drop-max", 8);
        e.getWorld().dropItemNaturally(e.getLocation(), Items.crystal(min + random.nextInt(max - min + 1)));
        if (killer == null) return;
        TeamColor t = teamOf(killer);
        if (t == null) return;
        for (Player p : members(t)) if (p.getWorld() == e.getWorld() && p.getLocation().distanceSquared(e.getLocation()) <= 225)
            addContribution(p, (int) Math.round(30 * (1 + skills.stat(p, Stat.GUARDIAN_CONTRIB))));
        broadcast(Component.text(t.displayName + "이 균열 수호자를 처치했어요!", t.textColor));
    }

    // ---------------------------------------------------------------- cores

    private void depositTick() {
        for (Map.Entry<UUID, TeamColor> e : teams.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || p.getGameMode() == GameMode.SPECTATOR) continue;
            Location core = arena.cores.get(e.getValue());
            if (core == null || p.getWorld() != core.getWorld() || p.getLocation().distanceSquared(core.clone().add(0.5, 0, 0.5)) > 16) continue;
            deposit(p);
        }
    }

    /** Delivers everything the player carries to their core, wherever they are. */
    public void deposit(Player p) {
        TeamColor t = teamOf(p);
        if (t == null) return;
        int perCrystal = cfgInt("game.ether-per-crystal", 5);
        int perRefined = cfgInt("game.ether-per-refined", 15) + (int) skills.stat(p, Stat.REFINED_VALUE_BONUS);
        int n = Items.countCrystals(p), r = Items.countRefined(p);
        if (n == 0 && r == 0) return;
        Items.takeCrystals(p, n);
        Items.takeRefined(p, r);
        if (skills.stat(p, Stat.AUTO_REFINE) > 0) { r += n / 2; n = n % 2; }
        double mult = underdogBonus(t) * (1 + skills.stat(p, Stat.DELIVER_MULT)) * (hasBossBuff(t) ? 1.25 : 1);
        int gained = (int) Math.round((n * perCrystal + r * perRefined) * mult);
        ether.merge(t, gained, Integer::sum);
        addContribution(p, (int) Math.round((10 * n + 30 * r) * (1 + skills.stat(p, Stat.DELIVER_CONTRIB_MULT))));
        if (skills.stat(p, Stat.DELIVER_SPEED) > 0) p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 160, 1));
        Sfx.play(p, "deliver", 1f, 1f);
        Location core = arena.cores.get(t);
        if (core != null) core.getWorld().spawnParticle(Particle.END_ROD, core.clone().add(0.5, 1, 0.5), 20, 0.3, 0.6, 0.3, 0.05);
        String what = (n > 0 ? "결정 " + n + "개" : "") + (n > 0 && r > 0 ? " + " : "") + (r > 0 ? "정제 결정 " + r + "개" : "");
        for (Player m : members(t)) m.sendMessage(Component.text(p.getName() + " " + what + " 납품 (+" + gained + " 에테르)", t.textColor));
    }

    /** Small team bonus for uneven matches (e.g. 3 vs 2). */
    private double underdogBonus(TeamColor t) {
        int mine = members(t).size(), theirs = members(t.other()).size();
        return mine < theirs ? 1.0 + plugin.getConfig().getDouble("game.underdog-bonus", 0.25) : 1.0;
    }

    public TeamColor coreAt(Location blockLoc) {
        for (Map.Entry<TeamColor, Location> e : arena.cores.entrySet()) {
            Location c = e.getValue();
            if (c.getWorld() == blockLoc.getWorld() && c.getBlockX() == blockLoc.getBlockX() && c.getBlockY() == blockLoc.getBlockY() && c.getBlockZ() == blockLoc.getBlockZ())
                return e.getKey();
        }
        return null;
    }

    /** An enemy hits a core: in phase 3 this steals ether, half of which drops as crystals. */
    public void hitCore(Player p, TeamColor coreTeam) {
        TeamColor t = teamOf(p);
        if (t == null) return;
        if (t == coreTeam) {
            p.sendActionBar(Component.text("결정을 들고 코어 근처에 서 있으면 자동으로 납품돼요.", NamedTextColor.GRAY));
            return;
        }
        if (phase != Phase.CLASH) {
            p.sendActionBar(Component.text("코어 약탈은 3페이즈부터 가능해요.", NamedTextColor.RED));
            return;
        }
        if (shieldLeft(coreTeam) > 0) {
            p.sendActionBar(Component.text("보호막이 " + shieldLeft(coreTeam) + "초 남았어요.", NamedTextColor.AQUA));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - coreHitCooldown.getOrDefault(p.getUniqueId(), 0L) < 600) return;
        coreHitCooldown.put(p.getUniqueId(), now);

        int current = ether(coreTeam);
        if (current <= 0) return;
        raidBase.putIfAbsent(coreTeam, current);
        double raidMult = (1 + skills.stat(p, Stat.RAID_MULT)) * (1 - skills.teamStat(coreTeam, Stat.TEAM_RAID_TAKEN_REDUCE));
        int steal = Math.min(current, (int) Math.round(cfgInt("game.raid-ether-per-hit", 10) * raidMult));
        ether.put(coreTeam, current - steal);
        int stolen = raidStolen.merge(coreTeam, steal, Integer::sum);
        int perCrystal = cfgInt("game.ether-per-crystal", 5);
        int dropCrystals = Math.max(1, steal / 2 / perCrystal);
        Location core = arena.cores.get(coreTeam).clone().add(0.5, 1, 0.5);
        core.getWorld().dropItemNaturally(core, Items.crystal(dropCrystals));
        core.getWorld().playSound(core, Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
        addContribution(p, 5);
        for (Player m : members(coreTeam)) {
            m.sendActionBar(Component.text("코어가 공격받고 있어요! -" + steal, NamedTextColor.RED));
            Sfx.play(m, "core_hit", 0.8f, 1f);
        }

        double cap = plugin.getConfig().getDouble("game.raid-cap-fraction", 0.2);
        if (stolen >= raidBase.get(coreTeam) * cap) {
            int shieldSec = cfgInt("game.shield-seconds", 60) + (int) skills.teamStat(coreTeam, Stat.TEAM_SHIELD_EXTRA);
            shieldUntil.put(coreTeam, elapsed + shieldSec);
            raidBase.remove(coreTeam);
            raidStolen.remove(coreTeam);
            broadcast(Component.text(coreTeam.displayName + " 코어에 보호막이 생겼어요 (" + shieldSec + "초).", NamedTextColor.AQUA));
        }
    }

    // ---------------------------------------------------------------- death & respawn

    /** Drops half of the crystals a player carried; returns how many dropped. */
    public int dropHalfCrystals(Player p) {
        int n = Items.countCrystals(p);
        double frac = Math.max(0.1, 0.5 - skills.stat(p, Stat.DEATH_DROP_REDUCE));
        int drop = (int) Math.ceil(n * frac);
        if (drop > 0) {
            Items.takeCrystals(p, drop);
            p.getWorld().dropItemNaturally(p.getLocation(), Items.crystal(drop));
        }
        return drop;
    }

    public void scheduleRespawn(Player p) {
        int sec = cfgInt("game.respawn-seconds", 10) - (int) skills.stat(p, Stat.RESPAWN_REDUCE) - (int) skills.teamStat(teamOf(p), Stat.TEAM_RESPAWN_REDUCE);
        respawnAt.put(p.getUniqueId(), elapsed + Math.max(3, sec));
    }

    public Location spawnOf(Player p) {
        TeamColor t = teamOf(p);
        if (t == null) return null;
        if (forwardSpawn.containsKey(t) && forwardUntil.getOrDefault(t, 0L) > elapsed) return forwardSpawn.get(t);
        return arena.spawns.get(t);
    }

    private void respawnTick() {
        respawnAt.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) return true;
            long left = e.getValue() - elapsed;
            if (left > 0) {
                p.showTitle(Title.title(Component.text("부활까지 " + left + "초", NamedTextColor.GRAY), Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ZERO)));
                return false;
            }
            Location s = spawnOf(p);
            if (s != null) p.teleport(s);
            p.setGameMode(GameMode.ADVENTURE);
            p.clearTitle();
            skills.onSkillsChanged(p);
            return true;
        });
    }

    // ---------------------------------------------------------------- PvP rules

    /** Whether attacker may damage victim right now. */
    public boolean canPvp(Player attacker, Player victim) {
        TeamColor a = teamOf(attacker), v = teamOf(victim);
        if (a == null || v == null) return true;
        if (a == v) return false;
        return switch (phase) {
            case EXPLORE -> false;
            case EXPAND -> arena.baseAt(victim.getLocation()) == null && arena.baseAt(attacker.getLocation()) == null;
            case CLASH -> true;
        };
    }

    public double baseDamageMultiplier(Player victim) {
        TeamColor v = teamOf(victim);
        return v != null && arena.baseAt(victim.getLocation()) == v ? 1.0 - plugin.getConfig().getDouble("game.base-damage-reduction", 0.3) : 1.0;
    }

    // ---------------------------------------------------------------- misc

    public void addContribution(Player p, int amount) {
        int before = skillPoints(p);
        contribution.merge(p.getUniqueId(), amount, Integer::sum);
        int after = skillPoints(p);
        if (after > before) {
            p.sendMessage(Component.text("✦ 스킬 포인트 +" + (after - before) + " (남은 포인트 " + skills.freePoints(p) + ") ", NamedTextColor.GREEN)
                    .append(Component.text("[스킬트리 열기]", NamedTextColor.LIGHT_PURPLE).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/aw skills"))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }
    }

    public void broadcast(Component c) {
        Bukkit.getServer().sendMessage(c);
    }

    public void broadcastTitle(Component title, Component sub) {
        Title t = Title.title(title, sub, Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700)));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(t);
    }

    public Hud hud() { return hud; }
}
