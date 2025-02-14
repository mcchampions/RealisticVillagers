package me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.stay;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19_r1.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class ResetStayStatus extends Behavior<Villager> {

    public ResetStayStatus() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return level.random.nextInt(20) == 0;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long game) {
        if (!(villager instanceof VillagerNPC npc)) return;
        if (!npc.isStayingInPlace()) return;

        if (level.getPlayerByUUID(npc.getInteractingWith()) == null) {
            npc.stopInteracting();
            npc.getBrain().eraseMemory(VillagerNPC.STAY_PLACE);
            npc.getBrain().setDefaultActivity(Activity.IDLE);
            npc.getBrain().updateActivityFromSchedule(npc.level.getDayTime(), npc.level.getGameTime());
        }
    }
}