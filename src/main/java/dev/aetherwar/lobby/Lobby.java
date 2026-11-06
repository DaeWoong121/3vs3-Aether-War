package dev.aetherwar.lobby;

import dev.aetherwar.AetherWar;
import dev.aetherwar.arena.Arena;
import dev.aetherwar.game.GameManager;
import dev.aetherwar.game.TeamColor;
import dev.aetherwar.item.Sfx;
import dev.aetherwar.skill.PlayerClass;
import dev.aetherwar.skill.SkillItems;
import dev.aetherwar.skill.SkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The floating lobby: team pads, class pedestals, a ready check with an automatic countdown,
 * and the lobby hotbar. Everything here works without commands.
 */
public class Lobby {
    private static final TextColor PURPLE = TextColor.color(0xC77DFF);
    private static final int BG = 0xA0140C24;

    private final AetherWar plugin;
    private final GameManager game;
    private final Arena arena;
    private final Set<UUID> ready = new HashSet<>();
    private final List<Entity> displays = new ArrayList<>();
    private final Map<UUID, PlayerClass> pedestalClass = new HashMap<>();
    private TextDisplay redLabel, blueLabel, titleLabel, statusLabel;
    private final TextDisplay[] classLabels = new TextDisplay[4];
    private final ItemDisplay[] classItems = new ItemDisplay[4];
    private BukkitTask task;
    private int countdown = -1;
    private int ticks;

    public Lobby(AetherWar plugin, GameManager game, Arena arena) {
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
    }

    public boolean exists() {
        return arena.lobby != null && arena.lobby.getWorld() != null;
    }

    public Location center() { return arena.lobby; }

    public Location spawn() { return LobbyBuilder.spawn(arena.lobby); }

    /** Builds the island above the middle of the map and remembers it. */
    public void build() {
        Location a = arena.cores.get(TeamColor.RED), b = arena.cores.get(TeamColor.BLUE);
        int y = plugin.getConfig().getInt("lobby.height", 140);
        Location c = a != null && b != null
                ? new Location(a.getWorld(), Math.floor((a.getX() + b.getX()) / 2), y, Math.floor((a.getZ() + b.getZ()) / 2))
                : new Location(arena.world(), 0, y, 0);
        removeDisplays();
        LobbyBuilder.build(c);
        arena.lobby = c;
        arena.save(plugin.getConfig());
        plugin.saveConfig();
        arena.saveToWorld();
        spawnDisplays();
    }

    public void start() {
        if (task != null) task.cancel();
        if (exists()) spawnDisplays();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        removeDisplays();
    }

    // ------------------------------------------------------------ displays

    private void removeDisplays() {
        for (Entity e : displays) e.remove();
        displays.clear();
        pedestalClass.clear();
        // leftovers from a crash (displays are not persistent, but be safe)
        if (exists()) for (Entity e : arena.lobby.getWorld().getNearbyEntities(arena.lobby, 24, 14, 24))
            if (e.getScoreboardTags().contains("aw_lobby")) e.remove();
    }

    private TextDisplay text(Location at, Component c, float scale) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(c);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(BG));
            t.setShadowed(true);
            t.setLineWidth(220);
            t.setPersistent(false);
            t.addScoreboardTag("aw_lobby");
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        displays.add(d);
        return d;
    }

    private void spawnDisplays() {
        removeDisplays();
        Location c = arena.lobby;
        titleLabel = text(c.clone().add(0.5, 8.2, 0.5), Component.text("✦ AETHER WAR ✦", PURPLE).decorate(TextDecoration.BOLD)
                .append(Component.newline()).append(Component.text("균열 수확전 · 3 대 3", NamedTextColor.GRAY)), 2.2f);
        statusLabel = text(c.clone().add(0.5, 7.0, 0.5), Component.empty(), 1.2f);
        redLabel = text(LobbyBuilder.pad(c, -1).add(0, 2.4, 0), Component.empty(), 1.1f);
        blueLabel = text(LobbyBuilder.pad(c, 1).add(0, 2.4, 0), Component.empty(), 1.1f);
        PlayerClass[] all = PlayerClass.values();
        for (int i = 0; i < 4; i++) {
            Location p = LobbyBuilder.pedestal(c, i);
            PlayerClass cls = all[i];
            ItemStack icon = SkillItems.icon(cls.icon, "class_" + cls.key(), Component.text(cls.displayName), List.of(), false, null);
            ItemDisplay item = p.getWorld().spawn(p.clone().add(0, 0.55, 0), ItemDisplay.class, d -> {
                d.setItemStack(icon);
                d.setBillboard(Display.Billboard.FIXED);
                d.setPersistent(false);
                d.addScoreboardTag("aw_lobby");
                d.setGlowColorOverride(Color.fromRGB(cls.color.value()));
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9f, 0.9f, 0.9f), new AxisAngle4f()));
            });
            displays.add(item);
            classItems[i] = item;
            classLabels[i] = text(p.clone().add(0, 1.55, 0), Component.empty(), 0.85f);
            Interaction hit = p.getWorld().spawn(p.clone().add(0, -1, 0), Interaction.class, d -> {
                d.setInteractionWidth(1.4f);
                d.setInteractionHeight(2.6f);
                d.setResponsive(true);
                d.setPersistent(false);
                d.addScoreboardTag("aw_lobby");
            });
            displays.add(hit);
            pedestalClass.put(hit.getUniqueId(), cls);
        }
        text(c.clone().add(-12.5, 3.2, -1.5), Component.text("규칙", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).append(Component.newline())
                .append(Component.text("자수정 노드를 쳐서 결정을 캐고\n우리 코어에 가져가면 에테르!\n먼저 1000 에테르를 모으면 승리\n\n", NamedTextColor.WHITE))
                .append(Component.text("1페이즈 0~30분 ", NamedTextColor.AQUA)).append(Component.text("PvP 없음\n", NamedTextColor.GRAY))
                .append(Component.text("2페이즈 30~60분 ", NamedTextColor.AQUA)).append(Component.text("중립 지역 PvP\n", NamedTextColor.GRAY))
                .append(Component.text("3페이즈 60~90분 ", NamedTextColor.AQUA)).append(Component.text("보스, 코어 약탈", NamedTextColor.GRAY)), 0.9f);
        text(c.clone().add(13.5, 3.2, -1.5), Component.text("조작", NamedTextColor.GOLD).decorate(TextDecoration.BOLD).append(Component.newline())
                .append(Component.text("발판을 밟으면 팀 참가\n받침대를 누르면 직업 선택\n초록 염료를 누르면 준비 완료\n\n", NamedTextColor.WHITE))
                .append(Component.text("경기 중\n", NamedTextColor.AQUA))
                .append(Component.text("9번 칸 나침반: 스킬트리\n7, 8번 칸: 스킬 사용\n결정은 코어 옆에 서면 자동 납품", NamedTextColor.GRAY)), 0.9f);
        refreshDisplays();
    }

    private void refreshDisplays() {
        if (!exists() || redLabel == null) return;
        redLabel.text(teamText(TeamColor.RED));
        blueLabel.text(teamText(TeamColor.BLUE));
        PlayerClass[] all = PlayerClass.values();
        String[] desc = {"노드 점령 · 약탈 · 호위", "빠른 채굴 · 대량 운반", "정제 · 치유 · 방해", "벽 · 포탑 · 점프대"};
        for (int i = 0; i < 4; i++) {
            PlayerClass cls = all[i];
            TextComponent.Builder b = Component.text().append(Component.text(cls.displayName, cls.color).decorate(TextDecoration.BOLD))
                    .append(Component.newline()).append(Component.text(desc[i], NamedTextColor.GRAY));
            List<String> red = new ArrayList<>(), blue = new ArrayList<>();
            for (Player p : game.members(TeamColor.RED)) if (game.skills.classOf(p) == cls) red.add(p.getName());
            for (Player p : game.members(TeamColor.BLUE)) if (game.skills.classOf(p) == cls) blue.add(p.getName());
            if (!red.isEmpty()) b.append(Component.newline()).append(Component.text(String.join(", ", red), TeamColor.RED.textColor));
            if (!blue.isEmpty()) b.append(Component.newline()).append(Component.text(String.join(", ", blue), TeamColor.BLUE.textColor));
            b.append(Component.newline()).append(Component.text("▶ 눌러서 선택", NamedTextColor.YELLOW));
            classLabels[i].text(b.build());
            Transformation t = classItems[i].getTransformation();
            classItems[i].setInterpolationDuration(20);
            classItems[i].setInterpolationDelay(0);
            classItems[i].setTransformation(new Transformation(t.getTranslation(), new org.joml.Quaternionf().rotateY((float) (ticks * 0.6 + i)), t.getScale(), t.getRightRotation()));
        }
        statusLabel.text(statusText());
    }

    private Component teamText(TeamColor t) {
        TextComponent.Builder b = Component.text().append(Component.text(t.displayName, t.textColor).decorate(TextDecoration.BOLD))
                .append(Component.text("  " + game.members(t).size() + "명", NamedTextColor.GRAY));
        for (Player p : game.members(t)) {
            PlayerClass c = game.skills.classOf(p);
            b.append(Component.newline())
                    .append(Component.text(ready.contains(p.getUniqueId()) ? "✔ " : "· ", ready.contains(p.getUniqueId()) ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                    .append(Component.text(p.getName(), NamedTextColor.WHITE))
                    .append(Component.text(c == null ? "  직업 미정" : "  " + c.displayName, c == null ? NamedTextColor.DARK_GRAY : c.color));
        }
        b.append(Component.newline()).append(Component.text("▼ 발판을 밟아 참가", NamedTextColor.YELLOW));
        return b.build();
    }

    private Component statusText() {
        List<Player> ps = participants();
        long r = ps.stream().filter(p -> ready.contains(p.getUniqueId())).count();
        if (game.isRunning()) return Component.text("경기 진행 중", NamedTextColor.GOLD);
        if (countdown >= 0) return Component.text(countdown + "초 뒤 시작!", NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        if (ps.size() < 2) return Component.text("친구를 기다리는 중 · " + ps.size() + "명", NamedTextColor.GRAY);
        return Component.text("준비 " + r + " / " + ps.size(), r == ps.size() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .append(Component.text("  · 모두 준비하면 자동 시작", NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------ players

    public List<Player> participants() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getGameMode() != GameMode.SPECTATOR || !game.isRunning()) out.add(p);
        return out;
    }

    public boolean inLobby(Player p) {
        return !game.isRunning() && exists() && p.getWorld() == arena.lobby.getWorld();
    }

    /** Puts a player into the lobby with the lobby hotbar. */
    public void send(Player p) {
        if (!exists()) return;
        ready.remove(p.getUniqueId());
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.teleport(spawn());
        game.hud().show(p);
        giveItems(p);
        p.showTitle(Title.title(Component.text("✦ Aether War ✦", PURPLE), Component.text("발판을 밟아 팀을, 받침대를 눌러 직업을 골라요", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
    }

    public void sendAll() {
        countdown = -1;
        ready.clear();
        for (Player p : Bukkit.getOnlinePlayers()) send(p);
    }

    public void giveItems(Player p) {
        var inv = p.getInventory();
        TeamColor t = game.teamOf(p);
        inv.setItem(0, SkillItems.icon(t == TeamColor.BLUE ? Material.BLUE_BANNER : t == TeamColor.RED ? Material.RED_BANNER : Material.WHITE_BANNER, null,
                Component.text("팀 고르기", t == null ? NamedTextColor.WHITE : t.textColor),
                List.of(Component.text(t == null ? "아직 팀이 없어요" : "지금: " + t.displayName, NamedTextColor.GRAY), Component.text("우클릭", NamedTextColor.YELLOW)), false, "lobby:team"));
        PlayerClass c = game.skills.classOf(p);
        inv.setItem(1, SkillItems.icon(c == null ? Material.BOOK : c.icon, c == null ? null : "class_" + c.key(),
                Component.text("직업 고르기", c == null ? NamedTextColor.WHITE : c.color),
                List.of(Component.text(c == null ? "아직 직업이 없어요" : "지금: " + c.displayName, NamedTextColor.GRAY), Component.text("우클릭", NamedTextColor.YELLOW)), false, "lobby:class"));
        inv.setItem(2, SkillItems.icon(Material.RECOVERY_COMPASS, "skill_book", Component.text("스킬트리 미리보기", NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("직업을 고르면 볼 수 있어요", NamedTextColor.GRAY)), false, "lobby:tree"));
        boolean r = ready.contains(p.getUniqueId());
        inv.setItem(4, SkillItems.icon(r ? Material.LIME_DYE : Material.GRAY_DYE, null,
                Component.text(r ? "✔ 준비 완료" : "준비하기", r ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(Component.text(r ? "다시 누르면 준비 취소" : "우클릭해서 준비", NamedTextColor.GRAY),
                        Component.text("모두 준비하면 10초 뒤 시작해요", NamedTextColor.DARK_GRAY)), r, "lobby:ready"));
        inv.setItem(7, guideBook());
        if (p.hasPermission("aetherwar.admin"))
            inv.setItem(8, SkillItems.icon(Material.NETHER_STAR, null, Component.text("바로 시작 (방장)", NamedTextColor.GOLD),
                    List.of(Component.text("준비 안 한 사람이 있어도 시작해요", NamedTextColor.GRAY), Component.text("쉬프트 + 우클릭", NamedTextColor.YELLOW)), true, "lobby:start"));
        inv.setHeldItemSlot(4);
    }

    private ItemStack guideBook() {
        ItemStack book = SkillItems.icon(Material.WRITTEN_BOOK, null, Component.text("안내서", NamedTextColor.AQUA),
                List.of(Component.text("우클릭해서 읽기", NamedTextColor.GRAY)), false, "lobby:book");
        BookMeta m = (BookMeta) book.getItemMeta();
        m.setTitle("Aether War 안내서");
        m.setAuthor("균열");
        m.addPages(
                page("Aether War", "자수정 노드를 3번 쳐서 에테르 결정을 캐요.\n\n결정을 들고 우리 코어(신호기) 옆에 서면 자동 납품!\n\n먼저 1000 에테르를 모으거나 90분 뒤 더 많은 팀이 이겨요."),
                page("페이즈", "1. 탐험 0~30분\nPvP 없음. 우리 기지 노드에서 채굴.\n\n2. 확장 30~60분\n중립 지역에서만 PvP. 점령 노드, 균열 폭주, 수호자.\n\n3. 결전 60~90분\n어디서나 PvP. 보스 등장, 적 코어 약탈."),
                page("직업", "전사: 싸움과 호위\n광부: 채굴과 운반\n연금술사: 정제, 치유, 물약\n건축가: 벽, 포탑, 점프대\n\n팀마다 같은 직업은 2명까지. 싸움을 못해도 광부, 연금술사, 건축가로 크게 활약할 수 있어요!"),
                page("스킬", "납품, 채굴, 처치로 기여도를 모으면 스킬 포인트!\n\n9번 칸 나침반: 스킬트리\n7, 8번 칸: 액티브 스킬 (우클릭)\n\n두 갈래에 2점씩 넣으면 교차 스킬이 열려요."),
                page("보스", "60분에 중앙 대균열에 균열의 군주가 나타나요.\n\n막타를 친 팀: 에테르 +120, 팀원 결정 +8, 3분 동안 힘과 신속.\n\n군주가 쓰러지면 대균열이 열려 결정이 2배!"));
        book.setItemMeta(m);
        return book;
    }

    private static Component page(String title, String body) {
        return Component.text(title + "\n\n", NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD)
                .append(Component.text(body, NamedTextColor.BLACK).decoration(TextDecoration.BOLD, false));
    }

    public void toggleReady(Player p) {
        if (game.isRunning()) return;
        if (game.teamOf(p) == null) autoTeam(p);
        if (!ready.remove(p.getUniqueId())) {
            ready.add(p.getUniqueId());
            Sfx.play(p, "capture", 0.8f, 1.4f);
            p.sendActionBar(Component.text("준비 완료! 모두 준비하면 시작해요", NamedTextColor.GREEN));
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            p.sendActionBar(Component.text("준비 취소", NamedTextColor.GRAY));
        }
        giveItems(p);
        refreshDisplays();
    }

    private void autoTeam(Player p) {
        TeamColor t = game.members(TeamColor.RED).size() <= game.members(TeamColor.BLUE).size() ? TeamColor.RED : TeamColor.BLUE;
        joinTeam(p, t, false);
    }

    /** Joins a team if it keeps the teams balanced (at most one apart). */
    public boolean joinTeam(Player p, TeamColor t, boolean announce) {
        if (game.isRunning() || game.teamOf(p) == t) return false;
        int mine = game.members(t).size(), other = game.members(t.other()).size();
        if (game.teamOf(p) == t.other()) other--;
        if (mine + 1 - other > 1) {
            p.sendActionBar(Component.text(t.displayName + " 인원이 너무 많아요", NamedTextColor.RED));
            return false;
        }
        game.joinTeam(p, t);
        // a class that is full on the new team is dropped
        PlayerClass c = game.skills.classOf(p);
        if (c != null && game.skills.countClass(t, c) > SkillManager.MAX_PER_CLASS) {
            game.skills.data(p).cls = null;
            p.sendMessage(Component.text(t.displayName + "에 " + c.displayName + " 자리가 없어서 직업을 다시 골라야 해요.", NamedTextColor.YELLOW));
        }
        if (announce) {
            for (Player o : Bukkit.getOnlinePlayers()) o.sendMessage(Component.text(p.getName() + " → " + t.displayName, t.textColor));
            Sfx.play(p, "capture", 1f, 1.2f);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4,
                    new Particle.DustOptions(t.armorColor, 1.4f));
        }
        giveItems(p);
        refreshDisplays();
        return true;
    }

    public void chooseClass(Player p, PlayerClass c) {
        if (game.teamOf(p) == null) autoTeam(p);
        String err = game.skills.chooseClass(p, c);
        if (err != null) {
            p.sendActionBar(Component.text(err, NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        Sfx.play(p, "capture", 0.8f, 1.2f);
        p.sendActionBar(Component.text("직업: " + c.displayName + " · " + c.role, c.color));
        giveItems(p);
        refreshDisplays();
    }

    public PlayerClass pedestal(Entity e) {
        return pedestalClass.get(e.getUniqueId());
    }

    /** Team pad check, called when a lobby player moves onto a new block. */
    public void stepped(Player p, Location to) {
        if (!inLobby(p)) return;
        Location c = arena.lobby;
        if (to.getY() < c.getY() - 12) {
            p.teleport(spawn());
            p.setFallDistance(0);
            return;
        }
        for (int side : new int[]{-1, 1}) {
            Location pad = LobbyBuilder.pad(c, side);
            if (Math.abs(to.getX() - pad.getX()) <= 2.5 && Math.abs(to.getZ() - pad.getZ()) <= 2.5 && Math.abs(to.getY() - pad.getY()) < 1.5) {
                TeamColor t = side < 0 ? TeamColor.RED : TeamColor.BLUE;
                if (game.teamOf(p) != t) joinTeam(p, t, true);
            }
        }
    }

    public void forceStart(Player by) {
        if (game.isRunning()) return;
        Bukkit.broadcast(Component.text(by.getName() + "님이 경기를 바로 시작해요.", NamedTextColor.GOLD));
        countdown = Math.min(countdown < 0 ? 5 : countdown, 5);
        ready.addAll(participants().stream().map(Player::getUniqueId).toList());
    }

    // ------------------------------------------------------------ tick

    private void tick() {
        ticks++;
        if (!exists()) return;
        if (game.isRunning()) { countdown = -1; return; }
        List<Player> ps = participants();
        ready.removeIf(id -> Bukkit.getPlayer(id) == null);
        boolean allReady = ps.size() >= 2 && ps.stream().allMatch(p -> ready.contains(p.getUniqueId()));
        if (countdown < 0 && allReady) countdown = plugin.getConfig().getInt("lobby.countdown-seconds", 10);
        else if (countdown >= 0 && !allReady) {
            countdown = -1;
            for (Player p : ps) p.sendActionBar(Component.text("누군가 준비를 취소해서 카운트다운이 멈췄어요", NamedTextColor.GRAY));
        }
        if (countdown >= 0) {
            if (countdown == 0) {
                countdown = -1;
                String err = game.start();
                if (err != null) Bukkit.broadcast(Component.text("시작할 수 없어요: " + err, NamedTextColor.RED));
                ready.clear();
                refreshDisplays();
                return;
            }
            if (countdown <= 5 || countdown % 5 == 0) {
                for (Player p : ps) {
                    p.showTitle(Title.title(Component.text(String.valueOf(countdown), countdown <= 3 ? NamedTextColor.GOLD : NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                            Component.text("곧 경기가 시작돼요", NamedTextColor.GRAY), Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, countdown <= 3 ? 1.6f : 1.0f);
                }
            }
            countdown--;
        }
        for (Player p : ps) {
            if (!inLobby(p)) continue;
            p.setFoodLevel(20);
            if (ticks % 2 == 0) {
                Component hint;
                if (game.teamOf(p) == null) hint = Component.text("빨간 발판이나 파란 발판을 밟아 팀을 골라요", NamedTextColor.YELLOW);
                else if (game.skills.classOf(p) == null) hint = Component.text("북쪽 받침대를 눌러 직업을 골라요 (안 고르면 자동)", NamedTextColor.YELLOW);
                else if (!ready.contains(p.getUniqueId())) hint = Component.text("5번 칸 염료를 우클릭하면 준비 완료", NamedTextColor.YELLOW);
                else hint = statusText();
                p.sendActionBar(hint);
            }
        }
        if (ticks % 3 == 0) {
            Location c = arena.lobby;
            c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(0.5, 4, 0.5), 6, 1.2, 1.5, 1.2, 0.01);
        }
        refreshDisplays();
    }

    /** Called by the game when a match ends: everyone comes back after a short pause. */
    public void returnAfter(int seconds) {
        if (!exists()) return;
        for (Player p : Bukkit.getOnlinePlayers())
            p.sendMessage(Component.text(seconds + "초 뒤 로비로 돌아가요. 같은 팀과 직업으로 바로 다시 할 수 있어요.", NamedTextColor.GRAY));
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!game.isRunning()) sendAll(); }, seconds * 20L);
    }
}
