package me.matsubara.realisticvillagers.entity.v1_19_r1.ai;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import me.matsubara.realisticvillagers.entity.v1_19_r1.VillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.SetLookAndInteractPlayer;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.ShowTradesToPlayer;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.core.GoToPotentialJobSite;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.core.GoToWantedItem;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.core.LookAtTargetSink;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.core.SetRaidStatus;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.core.VillagerPanicTrigger;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.core.*;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.fight.MeleeAttack;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.fight.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.fight.StopAttackingIfTargetInvalid;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.fight.*;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.hide.SetHiddenState;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.idle.InteractWithBreed;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.idle.VillagerMakeLove;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.meet.SocializeAtBell;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.rest.SleepInBed;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.stay.ResetStayStatus;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.work.StartFishing;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.work.WorkAtBarrel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.GateBehavior.OrderPolicy;
import net.minecraft.world.entity.ai.behavior.GateBehavior.RunningPolicy;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Optional;
import java.util.function.Predicate;

public class VillagerNPCGoalPackages {

    private final static float STROLL_SPEED_MODIFIER = 0.4f;
    private final static float SPEED_WHEN_STRAFING_BACK_FROM_TARGET = 0.75f;

    private final static int MIN_DESIRED_DIST_FROM_TARGET_WHEN_HOLDING_CROSSBOW = 5;
    private final static int GO_TO_WANTED_ITEM_DISTANCE = 10;

    private final static Predicate<Villager> SHOULD_HIDE = villager -> villager instanceof VillagerNPC npc && !npc.canAttack();

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getCorePackage(VillagerProfession profession) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8f)),
                Pair.of(0, new InteractWithDoor()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new VillagerPanicTrigger()),
                Pair.of(0, new WakeUp()),
                Pair.of(0, new ReactTo(MemoryModuleType.HEARD_BELL_TIME)),
                Pair.of(0, new ReactTo(VillagerNPC.HEARD_HORN_TIME)),
                Pair.of(0, new SetRaidStatus()),
                Pair.of(0, new ValidateNearbyPoi(profession.heldJobSite(), MemoryModuleType.JOB_SITE)),
                Pair.of(0, new ValidateNearbyPoi(profession.acquirableJobSite(), MemoryModuleType.POTENTIAL_JOB_SITE)),
                Pair.of(0, new Eat()),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(2, new PoiCompetitorScan(profession)),
                Pair.of(3, new LookAndFollowPlayerSink(Villager.SPEED_MODIFIER)),
                Pair.of(5, new GoToWantedItem(Villager.SPEED_MODIFIER, GO_TO_WANTED_ITEM_DISTANCE)),
                Pair.of(6, new AcquirePoi(
                        profession.acquirableJobSite(),
                        MemoryModuleType.JOB_SITE,
                        MemoryModuleType.POTENTIAL_JOB_SITE,
                        true,
                        Optional.empty())),
                Pair.of(7, new GoToPotentialJobSite(Villager.SPEED_MODIFIER)),
                Pair.of(8, new YieldJobSite(Villager.SPEED_MODIFIER)),
                Pair.of(10, new AcquirePoi(holder -> holder.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.of((byte) 14))),
                Pair.of(10, new AcquirePoi(holder -> holder.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14))),
                Pair.of(10, new AssignProfessionFromJobSite()),
                Pair.of(10, new ResetProfession()),
                Pair.of(10, new BackToStay()),
                Pair.of(10, new HealGolem(100, VillagerNPC.SPEED_MODIFIER)));
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getWorkPackage(VillagerProfession profession) {
        Behavior<Villager> behavior;
        if (profession == VillagerProfession.FARMER) {
            behavior = new WorkAtComposter();
        } else if (profession == VillagerProfession.FISHERMAN) {
            behavior = new WorkAtBarrel();
        } else {
            behavior = new WorkAtPoi();
        }

        return ImmutableList.of(
                getMinimalLookBehavior(),
                Pair.of(5, new RunOne<>(ImmutableList.of(
                        Pair.of(behavior, 7),
                        Pair.of(new StrollAroundPoi(MemoryModuleType.JOB_SITE, STROLL_SPEED_MODIFIER, 4), 2),
                        Pair.of(new StrollToPoi(MemoryModuleType.JOB_SITE, STROLL_SPEED_MODIFIER, 1, 10), 5),
                        Pair.of(new StrollToPoiList(MemoryModuleType.SECONDARY_JOB_SITE, Villager.SPEED_MODIFIER, 1, 6, MemoryModuleType.JOB_SITE), 5),
                        Pair.of(new HarvestFarmland(), profession == VillagerProfession.FARMER ? 2 : 5),
                        Pair.of(new StartFishing(), profession == VillagerProfession.FISHERMAN ? 2 : 5),
                        Pair.of(new UseBonemeal(), profession == VillagerProfession.FARMER ? 4 : 7)))),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, new SetLookAndInteractPlayer(4)),
                Pair.of(2, new SetWalkTargetFromBlockMemory(MemoryModuleType.JOB_SITE, Villager.SPEED_MODIFIER, 9, 100, 1200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getRestPackage() {
        return ImmutableList.of(
                Pair.of(2, new SetWalkTargetFromBlockMemory(MemoryModuleType.HOME, Villager.SPEED_MODIFIER, 1, 150, 1200)),
                Pair.of(3, new ValidateNearbyPoi(holder -> holder.is(PoiTypes.HOME), MemoryModuleType.HOME)),
                Pair.of(3, new SleepInBed()),
                Pair.of(5, new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                                Pair.of(new SetClosestHomeAsWalkTarget(Villager.SPEED_MODIFIER), 1),
                                Pair.of(new InsideBrownianWalk(Villager.SPEED_MODIFIER), 4),
                                Pair.of(new GoToClosestVillage(Villager.SPEED_MODIFIER, 4), 2),
                                Pair.of(new DoNothing(20, 40), 2)))),
                getMinimalLookBehavior(),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getMeetPackage() {
        return ImmutableList.of(
                Pair.of(2, new RunOne<>(ImmutableList.of(
                        Pair.of(new StrollAroundPoi(MemoryModuleType.MEETING_POINT, STROLL_SPEED_MODIFIER, 40), 2),
                        Pair.of(new SocializeAtBell(), 2)))),
                Pair.of(10, new ShowTradesToPlayer(400, 1600)),
                Pair.of(10, new SetLookAndInteractPlayer(4)),
                Pair.of(2, new SetWalkTargetFromBlockMemory(MemoryModuleType.MEETING_POINT, Villager.SPEED_MODIFIER, 6, 100, 200)),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, new ValidateNearbyPoi(holder -> holder.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT)),
                Pair.of(3, new GateBehavior<>(
                        ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        OrderPolicy.ORDERED,
                        RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new TradeWithVillager(), 1)))),
                getFullLookBehavior(),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getIdlePackage() {
        return ImmutableList.of(
                Pair.of(2, new RunOne<>(ImmutableList.of(
                        Pair.of(InteractWith.of(EntityType.VILLAGER, 8, MemoryModuleType.INTERACTION_TARGET, Villager.SPEED_MODIFIER, 2), 2),
                        Pair.of(new InteractWithBreed(8, Villager.SPEED_MODIFIER, 2), 1),
                        Pair.of(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, Villager.SPEED_MODIFIER, 2), 1),
                        Pair.of(new VillageBoundRandomStroll(Villager.SPEED_MODIFIER), 1),
                        Pair.of(new SetWalkTargetFromLookTarget(Villager.SPEED_MODIFIER, 2), 1),
                        Pair.of(new JumpOnBed(Villager.SPEED_MODIFIER), 1),
                        Pair.of(new DoNothing(30, 60), 1)))),
                Pair.of(3, new GiveGiftToHero(100)),
                Pair.of(3, new SetLookAndInteractPlayer(4)),
                Pair.of(3, new ShowTradesToPlayer(400, 1600)),
                Pair.of(3, new GateBehavior<>(
                        ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.INTERACTION_TARGET),
                        OrderPolicy.ORDERED,
                        RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new TradeWithVillager(), 1)))),
                Pair.of(3, new GateBehavior<>(ImmutableMap.of(),
                        ImmutableSet.of(MemoryModuleType.BREED_TARGET),
                        OrderPolicy.ORDERED, RunningPolicy.RUN_ONE,
                        ImmutableList.of(Pair.of(new VillagerMakeLove(), 1)))),
                getFullLookBehavior(),
                Pair.of(99, new UpdateActivityFromSchedule()));
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getRaidPackage() {
        return ImmutableList.of(
                Pair.of(0, new RunOne<>(ImmutableList.of(
                        Pair.of(new GoOutsideToCelebrate(Villager.SPEED_MODIFIER), 5),
                        Pair.of(new VictoryStroll(Villager.SPEED_MODIFIER * 1.1f), 2)))),
                Pair.of(0, new CelebrateVillagersSurvivedRaid(600, 600)),
                Pair.of(0, runIfShouldNotHide(new VillageBoundRandomStroll(Villager.SPEED_MODIFIER * 1.5f))),
                Pair.of(1, runIfShouldHide(new LocateHidingPlaceDuringRaid(24, Villager.SPEED_MODIFIER * 1.4f))),
                getMinimalLookBehavior(),
                Pair.of(99, new ResetRaidStatus()));
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getHidePackage() {
        return ImmutableList.of(
                Pair.of(0, new SetHiddenState(15, 3, MemoryModuleType.HEARD_BELL_TIME)),
                Pair.of(0, new SetHiddenState(15, 3, VillagerNPC.HEARD_HORN_TIME)),
                Pair.of(1, new LocateHidingPlace(32, Villager.SPEED_MODIFIER * 1.25f, 2)),
                getMinimalLookBehavior());
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getFightPackage() {
        return ImmutableList.of(
                Pair.of(0, new MeleeAttack()),
                Pair.of(0, new RangeWeaponAttack()),
                Pair.of(0, new BlockAttackWithShield()),
                Pair.of(1, runIf(
                        BlockAttackWithShield::notUsingShield,
                        new SetWalkTargetFromAttackTargetIfTargetOutOfReach(living -> Villager.SPEED_MODIFIER * 1.5f))),
                Pair.of(1, runIf(
                        villager -> villager instanceof VillagerNPC npc
                                && npc.isHoldingRangeWeapon()
                                && BlockAttackWithShield.notUsingShield(npc),
                        new BackUpIfTooClose<>(
                                MIN_DESIRED_DIST_FROM_TARGET_WHEN_HOLDING_CROSSBOW,
                                SPEED_WHEN_STRAFING_BACK_FROM_TARGET))),
                Pair.of(2, new StopAttackingIfTargetInvalid()));
    }

    public static ImmutableList<Pair<Integer, ? extends Behavior<? super Villager>>> getStayPackage() {
        Preconditions.checkArgument(VillagerNPC.STAY_PLACE != null);
        return ImmutableList.of(
                getFullLookBehavior(),
                Pair.of(0, new RunOne<>(ImmutableList.of(
                        Pair.of(new StrollAroundPoi(VillagerNPC.STAY_PLACE, STROLL_SPEED_MODIFIER, 3), 2),
                        Pair.of(new StrollToPoi(VillagerNPC.STAY_PLACE, STROLL_SPEED_MODIFIER, 1, 4), 5)))),
                Pair.of(2, new SetWalkTargetFromBlockMemory(VillagerNPC.STAY_PLACE, Villager.SPEED_MODIFIER, 5, 100, 1200)),
                Pair.of(99, new ResetStayStatus()));
    }

    private static Pair<Integer, Behavior<LivingEntity>> getFullLookBehavior() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(new SetEntityLookTarget(EntityType.CAT, 8.0f), 8),
                Pair.of(new SetEntityLookTarget(EntityType.VILLAGER, 8.0f), 2),
                Pair.of(new SetEntityLookTarget(EntityType.PLAYER, 8.0f), 2),
                Pair.of(new SetEntityLookTarget(MobCategory.CREATURE, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.WATER_CREATURE, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.AXOLOTLS, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.UNDERGROUND_WATER_CREATURE, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.WATER_AMBIENT, 8.0f), 1),
                Pair.of(new SetEntityLookTarget(MobCategory.MONSTER, 8.0f), 1),
                Pair.of(new DoNothing(30, 60), 2))));
    }

    private static Pair<Integer, Behavior<LivingEntity>> getMinimalLookBehavior() {
        return Pair.of(5, new RunOne<>(ImmutableList.of(
                Pair.of(new SetEntityLookTarget(EntityType.VILLAGER, 8.0f), 2),
                Pair.of(new SetEntityLookTarget(EntityType.PLAYER, 8.0f), 2),
                Pair.of(new DoNothing(30, 60), 8))));
    }

    private static Behavior<? super Villager> runIf(Predicate<Villager> predicate, Behavior<? super Villager> behavior) {
        return new RunIf<>(predicate, behavior);
    }

    private static Behavior<? super Villager> runIfShouldHide(Behavior<? super Villager> behavior) {
        return runIf(SHOULD_HIDE, behavior);
    }

    private static Behavior<? super Villager> runIfShouldNotHide(Behavior<? super Villager> behavior) {
        return runIf(SHOULD_HIDE.negate(), behavior);
    }
}