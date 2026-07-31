package me.allync.blockregen.util;

import com.gmail.nossr50.api.ExperienceAPI;
import org.bukkit.entity.Player;

public final class McMMOHelper {

    private McMMOHelper() {}

    public static boolean addXp(Player player, String skillName, int expAmount) {
        try {
            if (ExperienceAPI.isValidSkillType(skillName)) {
                ExperienceAPI.addXP(player, skillName, expAmount);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
