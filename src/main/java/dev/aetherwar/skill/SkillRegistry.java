package dev.aetherwar.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.aetherwar.skill.Active.*;
import static dev.aetherwar.skill.PlayerClass.*;
import static dev.aetherwar.skill.Stat.*;

/** Every skill in the game: 4 classes x 3 branches x 5 tiers x 2 choices, 3 cross skills per class, and rift augments. */
public final class SkillRegistry {
    private static final Map<String, Skill> BY_ID = new LinkedHashMap<>();
    private static final List<Skill> AUGMENTS = new ArrayList<>();

    private SkillRegistry() {}

    public static Skill get(String id) { return BY_ID.get(id); }
    public static List<Skill> augments() { return AUGMENTS; }

    public static Skill at(PlayerClass c, int branch, int tier, int choice) {
        for (Skill s : BY_ID.values()) if (s.cls == c && s.branch == branch && s.tier == tier && s.choice == choice) return s;
        return null;
    }

    public static Skill cross(PlayerClass c, int crossBranch) {
        for (Skill s : BY_ID.values()) if (s.cls == c && s.branch == crossBranch) return s;
        return null;
    }

    private static Skill s(PlayerClass c, int b, int t, int ch, String id, String name, String... lore) {
        Skill sk = new Skill(id, name, c, b, t, ch, List.of(lore));
        BY_ID.put(id, sk);
        return sk;
    }

    private static Skill aug(String id, String name, String... lore) {
        Skill sk = new Skill(id, name, null, Skill.AUGMENT, 0, 0, List.of(lore));
        BY_ID.put(id, sk);
        AUGMENTS.add(sk);
        return sk;
    }

    static {
        // ============================================================ 전사
        // 돌격
        s(WARRIOR, 0, 0, 0, "w_blade", "날카로운 칼날", "근접 피해 +15%").stat(MELEE_DMG, 0.15);
        s(WARRIOR, 0, 0, 1, "w_sprint", "질주", "항상 신속 I").stat(SPEED, 1);
        s(WARRIOR, 0, 1, 0, "w_dash", "돌진", "바라보는 방향으로 8칸 돌진").act(DASH, 12, 1.6, 0, 0);
        s(WARRIOR, 0, 1, 1, "w_hook", "갈고리", "바라보는 적을 끌어온다 (15칸)").act(HOOK, 18, 1.0, 15, 0);
        s(WARRIOR, 0, 2, 0, "w_raider", "약탈자", "코어 약탈량 +50%").stat(RAID_MULT, 0.5);
        s(WARRIOR, 0, 2, 1, "w_execute", "처형", "체력 30% 이하인 적에게 피해 +30%").stat(EXECUTE_DMG, 0.3);
        s(WARRIOR, 0, 3, 0, "w_bloodlust", "피의 갈증", "적 처치 시 체력 6 회복").stat(KILL_HEAL, 6);
        s(WARRIOR, 0, 3, 1, "w_berserk", "광전사", "체력 50% 이하일 때 피해 +25%").stat(LOW_HP_DMG, 0.25);
        s(WARRIOR, 0, 4, 0, "w_slam", "대지 강타", "주변 5칸 적을 띄우고 피해 8").act(GROUND_SLAM, 40, 8, 5, 0);
        s(WARRIOR, 0, 4, 1, "w_warcry", "전장의 함성", "10칸 아군에게 힘 I, 신속 I 10초").act(WAR_CRY, 50, 0, 10, 10);
        // 수호
        s(WARRIOR, 1, 0, 0, "w_skin", "단단한 피부", "받는 피해 -10%").stat(DMG_TAKEN_REDUCE, 0.10);
        s(WARRIOR, 1, 0, 1, "w_vigor", "활력", "최대 체력 +4").stat(MAX_HP, 4);
        s(WARRIOR, 1, 1, 0, "w_wall", "방패 벽", "앞에 5칸 방패 벽을 8초간 세운다").act(WALL, 25, 5, 0, 8);
        s(WARRIOR, 1, 1, 1, "w_taunt", "도발", "8칸 적을 발광, 나약함 6초").act(TAUNT, 20, 0, 8, 6);
        s(WARRIOR, 1, 2, 0, "w_keeper", "코어 수호자", "자기 기지 안에서 받는 피해 추가 -15%").stat(BASE_DMG_TAKEN_REDUCE, 0.15);
        s(WARRIOR, 1, 2, 1, "w_regen", "재생", "항상 재생 I").stat(REGEN, 1);
        s(WARRIOR, 1, 3, 0, "w_undying", "불굴", "부활 대기 -4초").stat(RESPAWN_REDUCE, 4);
        s(WARRIOR, 1, 3, 1, "w_anchor", "닻", "넉백 저항 +60%").stat(KB_RESIST, 0.6);
        s(WARRIOR, 1, 4, 0, "w_ward", "수호 결계", "6칸 아군 저항 II 6초").act(WARD, 45, 0, 6, 6);
        s(WARRIOR, 1, 4, 1, "w_golem", "코어 골렘", "골렘을 30초간 소환한다").act(GOLEM, 60, 0, 0, 30);
        // 사냥꾼
        s(WARRIOR, 2, 0, 0, "w_slayer", "수호자 사냥", "균열 수호자에게 피해 +40%").stat(GUARDIAN_DMG, 0.4);
        s(WARRIOR, 2, 0, 1, "w_bounty_g", "사냥 보상", "수호자 처치 기여도 +50%").stat(GUARDIAN_CONTRIB, 0.5);
        s(WARRIOR, 2, 1, 0, "w_track", "추적", "20칸 적을 8초간 발광").act(SCAN, 25, 0, 20, 8);
        s(WARRIOR, 2, 1, 1, "w_archer", "명사수", "활 피해 +25%").stat(BOW_DMG, 0.25);
        s(WARRIOR, 2, 2, 0, "w_loot", "전리품", "적 처치 시 결정 +1").stat(KILL_CRYSTALS, 1);
        s(WARRIOR, 2, 2, 1, "w_capture", "깃발 사냥꾼", "노드 점령 속도 +50%").stat(CAPTURE_SPEED, 0.5);
        s(WARRIOR, 2, 3, 0, "w_stealth", "그림자 걸음", "6초간 투명해진다").act(STEALTH, 30, 0, 0, 6);
        s(WARRIOR, 2, 3, 1, "w_sniper", "저격", "활 피해 +40%").stat(BOW_DMG, 0.4);
        s(WARRIOR, 2, 4, 0, "w_hunt", "사냥 개시", "25칸 적 발광 10초, 아군 신속 I").act(HUNT, 50, 0, 25, 10);
        s(WARRIOR, 2, 4, 1, "w_bounty", "현상금", "처치 시 기여도 +60, 결정 +2").stat(KILL_CONTRIB, 60).stat(KILL_CRYSTALS, 2);
        // 교차
        s(WARRIOR, Skill.CROSS_AB, 0, 0, "w_x_shielddash", "방패 돌진", "돌진하며 부딪힌 적을 밀치고 피해 4").act(SHIELD_DASH, 14, 4, 2.5, 0);
        s(WARRIOR, Skill.CROSS_BC, 0, 0, "w_x_alarm", "경계 태세", "적이 우리 기지에 들어오면 알림 + 발광").stat(BASE_ALARM, 1);
        s(WARRIOR, Skill.CROSS_AC, 0, 0, "w_x_chaser", "추격자", "발광한 적에게 피해 +20%").stat(MARKED_DMG, 0.2);

        // ============================================================ 광부
        // 숙련
        s(MINER, 0, 0, 0, "m_quick", "빠른 손", "노드 채굴 타수 -1").stat(MINE_HITS_REDUCE, 1);
        s(MINER, 0, 0, 1, "m_pick", "단단한 곡괭이", "내가 캔 노드 재생 -20%").stat(NODE_CD_REDUCE, 0.2);
        s(MINER, 0, 1, 0, "m_double", "더블 드롭", "25% 확률로 결정 2배").stat(DOUBLE_DROP, 0.25);
        s(MINER, 0, 1, 1, "m_lucky", "행운의 손", "10% 확률로 정제 결정").stat(REFINED_CHANCE, 0.10);
        s(MINER, 0, 2, 0, "m_regrow", "재생 가속", "내가 캔 노드 재생 -30%").stat(NODE_CD_REDUCE, 0.3);
        s(MINER, 0, 2, 1, "m_appraise", "결정 감별", "채굴 기여도 2배").stat(MINE_CONTRIB_MULT, 1.0);
        s(MINER, 0, 3, 0, "m_resonance", "광맥 공명", "10칸 안의 노드를 즉시 재생").act(NODE_RESONANCE, 60, 0, 10, 0);
        s(MINER, 0, 3, 1, "m_precise", "정밀 채굴", "노드 채굴 타수 -1").stat(MINE_HITS_REDUCE, 1);
        s(MINER, 0, 4, 0, "m_shatter", "대지 분쇄", "8칸 안의 노드를 전부 즉시 수확").act(EARTH_SHATTER, 90, 0, 8, 0);
        s(MINER, 0, 4, 1, "m_absorb", "균열 흡수", "점령 노드와 대균열에서 결정 +1").stat(RICH_NODE_BONUS, 1);
        // 짐꾼
        s(MINER, 1, 0, 0, "m_bag", "넓은 가방", "죽을 때 떨구는 결정 50% → 30%").stat(DEATH_DROP_REDUCE, 0.2);
        s(MINER, 1, 0, 1, "m_light", "가벼운 발", "항상 신속 I").stat(SPEED, 1);
        s(MINER, 1, 1, 0, "m_tight", "흘리지 않기", "죽을 때 떨구는 결정 -20%p").stat(DEATH_DROP_REDUCE, 0.2);
        s(MINER, 1, 1, 1, "m_recall", "비상 귀환", "3초 뒤 기지로 귀환").act(RECALL, 60, 0, 0, 3);
        s(MINER, 1, 2, 0, "m_convoy", "운반 대열", "결정을 들고 있으면 근처 아군 신속 I").stat(CARRY_AURA, 1);
        s(MINER, 1, 2, 1, "m_bonus", "납품 수당", "납품 에테르 +15%").stat(DELIVER_MULT, 0.15);
        s(MINER, 1, 3, 0, "m_back", "튼튼한 등", "결정 10개 이상 들면 받는 피해 -15%").stat(CARRY_ARMOR, 0.15);
        s(MINER, 1, 3, 1, "m_rush", "질주 납품", "납품 직후 신속 II 8초").stat(DELIVER_SPEED, 1);
        s(MINER, 1, 4, 0, "m_portal", "결정 포탈", "들고 있는 결정을 즉시 납품").act(CRYSTAL_PORTAL, 75, 0, 0, 0);
        s(MINER, 1, 4, 1, "m_caravan", "수레 행렬", "납품 에테르 +35%").stat(DELIVER_MULT, 0.35);
        // 탐지
        s(MINER, 2, 0, 0, "m_compass", "노드 감지", "가장 가까운 캘 수 있는 노드 방향 표시").stat(NODE_COMPASS, 1);
        s(MINER, 2, 0, 1, "m_sense", "발소리 감지", "12칸 안에 적이 오면 경고").stat(ENEMY_SENSE, 1);
        s(MINER, 2, 1, 0, "m_mark", "적 표식", "20칸 적을 6초간 발광").act(SCAN, 25, 0, 20, 6);
        s(MINER, 2, 1, 1, "m_jackpot", "숨은 광맥", "채굴 시 5% 확률로 결정 3개").stat(JACKPOT_CHANCE, 0.05);
        s(MINER, 2, 2, 0, "m_explorer", "탐험가", "중립 지역에서 신속 I").stat(NEUTRAL_SPEED, 1);
        s(MINER, 2, 2, 1, "m_wide", "넓은 시야", "발광 스킬 범위 +50%").stat(SCAN_RANGE_MULT, 0.5);
        s(MINER, 2, 3, 0, "m_pulse", "투시 파동", "35칸 적을 4초간 발광").act(SCAN, 40, 0, 35, 4);
        s(MINER, 2, 3, 1, "m_trapsense", "함정 감지", "적 함정과 지뢰에 피해를 받지 않는다").stat(TRAP_IMMUNE, 1);
        s(MINER, 2, 4, 0, "m_wave", "감지 파동", "맵 전체 적을 5초간 발광").act(SCAN, 80, 0, 400, 5);
        s(MINER, 2, 4, 1, "m_eye", "균열의 눈", "수호자 피해 +50%, 점령 노드/대균열 결정 +1").stat(GUARDIAN_DMG, 0.5).stat(RICH_NODE_BONUS, 1);
        // 교차
        s(MINER, Skill.CROSS_AB, 0, 0, "m_x_cart", "광산 수레", "채굴한 결정이 10% 확률로 바로 납품").stat(INSTANT_DELIVER_CHANCE, 0.10);
        s(MINER, Skill.CROSS_BC, 0, 0, "m_x_smuggle", "밀수 경로", "결정을 들고 있으면 발광 스킬에 걸리지 않는다").stat(SMUGGLER, 1);
        s(MINER, Skill.CROSS_AC, 0, 0, "m_x_blast", "광맥 폭파", "바라보는 노드를 터뜨려 결정 3개 (노드 40초 비활성)").act(VEIN_BLAST, 90, 3, 6, 40);

        // ============================================================ 연금술사
        // 정제
        s(ALCHEMIST, 0, 0, 0, "a_refine", "정제", "결정 2개 → 정제 결정 1개 (15 에테르)").act(REFINE, 3, 0, 0, 0);
        s(ALCHEMIST, 0, 0, 1, "a_massrefine", "대량 정제", "가진 결정을 전부 2:1로 정제").act(MASS_REFINE, 20, 0, 0, 0);
        s(ALCHEMIST, 0, 1, 0, "a_purity", "순도", "정제 결정 가치 +3").stat(REFINED_VALUE_BONUS, 3);
        s(ALCHEMIST, 0, 1, 1, "a_catalyst", "촉매", "정제할 때 20% 확률로 결정 1개 돌려받음").stat(REFINE_REFUND, 0.2);
        s(ALCHEMIST, 0, 2, 0, "a_auto", "휴대 정제", "납품할 때 결정을 자동으로 2:1 정제").stat(AUTO_REFINE, 1);
        s(ALCHEMIST, 0, 2, 1, "a_transmute", "결정 연성", "30초마다 결정 1개 생성").stat(CRYSTAL_GEN, 30);
        s(ALCHEMIST, 0, 3, 0, "a_stone", "현자의 돌", "정제 결정 가치 +5").stat(REFINED_VALUE_BONUS, 5);
        s(ALCHEMIST, 0, 3, 1, "a_amplify", "연금 증폭", "납품 기여도 +50%").stat(DELIVER_CONTRIB_MULT, 0.5);
        s(ALCHEMIST, 0, 4, 0, "a_gold", "황금 변환", "가진 결정을 1:1로 전부 정제").act(TRANSMUTE, 90, 0, 0, 0);
        s(ALCHEMIST, 0, 4, 1, "a_spring", "에테르 샘", "15초마다 결정 1개 생성").stat(CRYSTAL_GEN, 15);
        // 치유
        s(ALCHEMIST, 1, 0, 0, "a_hand", "치유의 손", "바라보는 아군 체력 6 회복").act(HEAL_TARGET, 8, 6, 12, 0);
        s(ALCHEMIST, 1, 0, 1, "a_aura", "힐 오라", "6칸 아군 3초마다 체력 1 회복").stat(HEAL_AURA, 1);
        s(ALCHEMIST, 1, 1, 0, "a_regenpot", "재생 물약", "6칸 아군 재생 II 6초").act(REGEN_BURST, 20, 0, 6, 6);
        s(ALCHEMIST, 1, 1, 1, "a_tough", "강인함", "최대 체력 +4").stat(MAX_HP, 4);
        s(ALCHEMIST, 1, 2, 0, "a_revive", "부활 가속", "우리 팀 부활 대기 -3초").stat(TEAM_RESPAWN_REDUCE, 3);
        s(ALCHEMIST, 1, 2, 1, "a_link", "생명의 연결", "아군을 치유하면 나도 절반 회복").stat(HEAL_LINK, 0.5);
        s(ALCHEMIST, 1, 3, 0, "a_shield", "보호막 물약", "6칸 아군 흡수 II 10초").act(ABSORB_BURST, 30, 0, 6, 10);
        s(ALCHEMIST, 1, 3, 1, "a_focus", "정신 집중", "액티브 재사용 대기 -20%").stat(COOLDOWN_REDUCE, 0.2);
        s(ALCHEMIST, 1, 4, 0, "a_teamheal", "생명의 비", "모든 아군 체력을 가득 채운다").act(TEAM_HEAL, 90, 0, 0, 0);
        s(ALCHEMIST, 1, 4, 1, "a_phoenix", "불사조", "페이즈마다 한 번, 죽는 대신 체력 10으로 버틴다").stat(PHOENIX, 1);
        // 혼돈
        s(ALCHEMIST, 2, 0, 0, "a_slow", "둔화 물약", "투척: 5칸 적 구속 II 5초").act(POTION_SLOW, 12, 0, 5, 5);
        s(ALCHEMIST, 2, 0, 1, "a_poison", "독 물약", "투척: 5칸 적 독 5초").act(POTION_POISON, 14, 0, 5, 5);
        s(ALCHEMIST, 2, 1, 0, "a_smoke", "연막탄", "투척: 6칸 적 실명 4초").act(SMOKE, 25, 0, 6, 4);
        s(ALCHEMIST, 2, 1, 1, "a_weak", "약화 물약", "투척: 5칸 적 나약함 8초").act(POTION_WEAKNESS, 18, 0, 5, 8);
        s(ALCHEMIST, 2, 2, 0, "a_linger", "잔류 효과", "투척 물약 지속 +50%").stat(POTION_DURATION_MULT, 0.5);
        s(ALCHEMIST, 2, 2, 1, "a_splash", "넓은 범위", "투척 물약 범위 +50%").stat(POTION_RADIUS_MULT, 0.5);
        s(ALCHEMIST, 2, 3, 0, "a_gravity", "중력 역전", "투척: 6칸 적 공중부양 3초").act(LEVITATE, 30, 0, 6, 3);
        s(ALCHEMIST, 2, 3, 1, "a_acid", "산성", "투척 물약이 피해 3 추가").stat(POTION_EXTRA_DMG, 3);
        s(ALCHEMIST, 2, 4, 0, "a_cloud", "혼돈의 구름", "8칸 독+구속 장판 8초").act(CHAOS_CLOUD, 60, 0, 8, 8);
        s(ALCHEMIST, 2, 4, 1, "a_timewarp", "시간 왜곡", "10칸 적 구속 IV 4초").act(TIME_WARP, 60, 0, 10, 4);
        // 교차
        s(ALCHEMIST, Skill.CROSS_AB, 0, 0, "a_x_lifecrystal", "생명 결정", "정제할 때마다 6칸 아군 체력 2 회복").stat(REFINE_HEAL, 2);
        s(ALCHEMIST, Skill.CROSS_BC, 0, 0, "a_x_drain", "생명 흡수 안개", "물약에 맞은 적 하나당 나와 근처 아군 체력 2 회복").stat(POTION_LIFESTEAL, 2);
        s(ALCHEMIST, Skill.CROSS_AC, 0, 0, "a_x_bomb", "폭발 결정", "결정 3개를 던져 폭발 피해 8").act(EXPLOSIVE_CRYSTAL, 25, 8, 4, 0);

        // ============================================================ 건축가
        // 요새
        s(BUILDER, 0, 0, 0, "b_wall", "방벽", "앞에 3x2 돌벽 15초").act(WALL, 15, 3, 0, 15);
        s(BUILDER, 0, 0, 1, "b_sandbag", "모래주머니", "자기 기지 안에서 받는 피해 추가 -10%").stat(BASE_DMG_TAKEN_REDUCE, 0.10);
        s(BUILDER, 0, 1, 0, "b_bigwall", "강화 성벽", "벽이 5x3으로 커지고 두 배 오래 간다").stat(WALL_BIG, 1);
        s(BUILDER, 0, 1, 1, "b_shieldext", "보호막 연장", "우리 코어 보호막 +30초").stat(TEAM_SHIELD_EXTRA, 30);
        s(BUILDER, 0, 2, 0, "b_repair", "수리공", "코어 근처 아군 재생 I").stat(REPAIR_AURA, 1);
        s(BUILDER, 0, 2, 1, "b_barricade", "바리케이드", "앞에 유리 벽 6초 (짧은 재사용)").act(BARRICADE, 8, 3, 0, 6);
        s(BUILDER, 0, 3, 0, "b_fortify", "요새화", "우리 코어 약탈량 -30%").stat(TEAM_RAID_TAKEN_REDUCE, 0.3);
        s(BUILDER, 0, 3, 1, "b_alarm", "경보 장치", "적이 우리 기지에 들어오면 알림 + 발광").stat(BASE_ALARM, 1);
        s(BUILDER, 0, 4, 0, "b_citadel", "성채", "내 주위에 원형 벽 10초").act(CITADEL, 60, 4, 0, 10);
        s(BUILDER, 0, 4, 1, "b_coreward", "코어 결계", "우리 코어에 즉시 보호막 20초").act(CORE_WARD, 120, 0, 0, 20);
        // 기계
        s(BUILDER, 1, 0, 0, "b_turret", "포탑", "적에게 화살을 쏘는 포탑 20초").act(TURRET, 30, 4, 14, 20);
        s(BUILDER, 1, 0, 1, "b_arrows", "자동 화살", "30초마다 화살 8개").stat(ARROW_SUPPLY, 8);
        s(BUILDER, 1, 1, 0, "b_strongturret", "강화 포탑", "포탑 지속 +50%, 피해 +50%").stat(TURRET_DURATION, 0.5).stat(TURRET_DMG, 0.5);
        s(BUILDER, 1, 1, 1, "b_rapid", "연사 포탑", "포탑 발사 속도 2배").stat(TURRET_RATE, 1);
        s(BUILDER, 1, 2, 0, "b_trap", "함정", "밟은 적 구속 III + 피해 4 (최대 3개)").act(TRAP, 10, 4, 1.5, 60);
        s(BUILDER, 1, 2, 1, "b_mine", "지뢰", "밟으면 폭발 피해 6").act(MINE, 20, 6, 1.5, 90);
        s(BUILDER, 1, 3, 0, "b_double", "이중 배치", "포탑을 2개까지 세울 수 있다").stat(TURRET_MAX, 1);
        s(BUILDER, 1, 3, 1, "b_hunterturret", "수호자 사냥 포탑", "포탑이 수호자도 공격, 수호자 피해 2배").stat(TURRET_GUARDIAN, 1);
        s(BUILDER, 1, 4, 0, "b_cannon", "대포탑", "폭발 화살 포탑 40초").act(BIG_TURRET, 90, 6, 18, 40);
        s(BUILDER, 1, 4, 1, "b_swarm", "기계 군단", "포탑 3개를 15초간 동시에").act(TURRET_SWARM, 90, 3, 12, 15);
        // 길잡이
        s(BUILDER, 2, 0, 0, "b_pad", "점프대", "30초짜리 점프대 설치").act(JUMP_PAD, 20, 1.4, 0, 30);
        s(BUILDER, 2, 0, 1, "b_rail", "가속 길", "흙길 위에서 신속 II").stat(PATH_SPEED, 2);
        s(BUILDER, 2, 1, 0, "b_beacon", "귀환 비콘", "2초 뒤 기지로 귀환").act(RECALL, 45, 0, 0, 2);
        s(BUILDER, 2, 1, 1, "b_bridge", "다리 놓기", "바라보는 방향 12칸 임시 다리 20초").act(BRIDGE, 25, 12, 0, 20);
        s(BUILDER, 2, 2, 0, "b_highpad", "넓은 점프대", "점프대 높이 2배").stat(PAD_HEIGHT, 1);
        s(BUILDER, 2, 2, 1, "b_padspeed", "질주의 길", "점프대 쓴 아군 신속 II 6초").stat(PAD_SPEED, 1);
        s(BUILDER, 2, 3, 0, "b_outpost", "전진 기지", "지금 위치를 팀 부활 지점으로 (60초)").act(FORWARD_SPAWN, 120, 0, 0, 60);
        s(BUILDER, 2, 3, 1, "b_quick", "빠른 건설", "건설 액티브 재사용 대기 -30%").stat(BUILD_CD_REDUCE, 0.3);
        s(BUILDER, 2, 4, 0, "b_rally", "집결 신호", "3초 뒤 아군 전원을 내 위치로 소환").act(RALLY, 120, 0, 0, 3);
        s(BUILDER, 2, 4, 1, "b_skyway", "하늘길", "점프대 재사용 -50%, 지속 2배").stat(BUILD_CD_REDUCE, 0.2).stat(PAD_HEIGHT, 0.5);
        // 교차
        s(BUILDER, Skill.CROSS_AB, 0, 0, "b_x_wallturret", "포탑 성벽", "벽을 세우면 작은 포탑도 10초간 함께").stat(WALL_TURRET, 1);
        s(BUILDER, Skill.CROSS_BC, 0, 0, "b_x_launch", "포탑 발사대", "포탑을 바라보는 곳 20칸 앞에 떨어뜨린다").act(TURRET_LAUNCH, 30, 4, 14, 20);
        s(BUILDER, Skill.CROSS_AC, 0, 0, "b_x_mobile", "이동 요새", "점프대 쓴 아군 흡수 I 8초").stat(PAD_ABSORB, 1);

        // ============================================================ 균열 각인 (경기 중 무작위 3개 중 1개)
        aug("g_glass", "유리대포", "최대 체력 -8, 모든 피해 +40%").stat(MAX_HP, -8).stat(MELEE_DMG, 0.4).stat(BOW_DMG, 0.4);
        aug("g_courier", "배달부", "납품 에테르 +25%").stat(DELIVER_MULT, 0.25);
        aug("g_swift", "바람걸음", "항상 신속 I").stat(SPEED, 1);
        aug("g_iron", "강철 심장", "최대 체력 +6").stat(MAX_HP, 6);
        aug("g_miner", "광맥 직감", "노드 채굴 타수 -1").stat(MINE_HITS_REDUCE, 1);
        aug("g_lucky", "행운", "채굴 시 20% 확률로 결정 2배").stat(DOUBLE_DROP, 0.2);
        aug("g_vampire", "흡혈", "적 처치 시 체력 8 회복").stat(KILL_HEAL, 8);
        aug("g_quick", "가속 회로", "액티브 재사용 대기 -25%").stat(COOLDOWN_REDUCE, 0.25);
        aug("g_tank", "돌가죽", "받는 피해 -15%").stat(DMG_TAKEN_REDUCE, 0.15);
        aug("g_hunter", "수호자 학살자", "수호자 피해 +60%, 처치 기여도 +50%").stat(GUARDIAN_DMG, 0.6).stat(GUARDIAN_CONTRIB, 0.5);
        aug("g_phoenix", "두 번째 기회", "페이즈마다 한 번 죽음을 버틴다").stat(PHOENIX, 1);
        aug("g_spring", "작은 샘", "40초마다 결정 1개 생성").stat(CRYSTAL_GEN, 40);
        aug("g_rich", "노다지", "점령 노드와 대균열에서 결정 +1").stat(RICH_NODE_BONUS, 1);
        aug("g_quickrevive", "빠른 부활", "부활 대기 -5초").stat(RESPAWN_REDUCE, 5);
        aug("g_bounty", "현상 수배", "적 처치 시 결정 +2").stat(KILL_CRYSTALS, 2);
        aug("g_capture", "깃발꾼", "노드 점령 속도 2배").stat(CAPTURE_SPEED, 1.0);
    }
}
