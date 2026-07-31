package me.allync.blockregen.util;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import dev.aurelium.auraskills.api.stat.Stats;
import org.bukkit.entity.Player;

public final class AuraSkillsHelper {

    private AuraSkillsHelper() {}

    public static double getLuckLevel(Player player) {
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null && user.isLoaded()) {
                return user.getStatLevel(Stats.LUCK);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static boolean addXp(Player player, String skillName, int expAmount) {
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null && user.isLoaded()) {
                Skills skill = Skills.valueOf(skillName.toUpperCase());
                user.addSkillXp(skill, expAmount);
                return true;
            }
        } catch (IllegalArgumentException ex) {
            // Invalid skill name
        } catch (Throwable ignored) {}
        return false;
    }
}
