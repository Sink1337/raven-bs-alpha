package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.script.ScriptDefaults;
import keystrokesmod.script.model.NetworkPlayer;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.utility.BlinkHandler;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Objects;

public class LagRange extends Module {

    public SliderSetting latency;
    private SliderSetting activationDist;
    private SliderSetting hurttime;
    private ButtonSetting ignoreTeammates, weaponOnly;
    public ButtonSetting renderTimer, initialPosition;

    private int disableTicks;
    private Vec3 lastPosition;
    private double closest;
    private double startFallHeight;
    private boolean function;

    public boolean blink;

    private long delay;

    public LagRange() {
        super("LagRange", category.combat, 0);

        this.registerSetting(latency = new SliderSetting("Latency", "ms", 300, 10, 500, 10));
        this.registerSetting(activationDist = new SliderSetting("Activation Distance", " blocks", 7, 0, 20, 1));
        this.registerSetting(hurttime = new SliderSetting("Hurttime", 2, 0, 10, 1));
        this.registerSetting(initialPosition = new ButtonSetting("Show initial position", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public String getInfo() {
        return (int) latency.getInput() + "ms";
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        disableTicks--;
        double boxSize = activationDist.getInput();

        Vec3 myPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        boolean onGround = mc.thePlayer.onGround;

        if (ModuleUtils.isBreaking) {
            disableTicks = 1;
            function = false;
        }

        if (Utils.getHorizontalSpeed() > 0.4) {
            disableTicks = 5;
            function = false;
        }

        if (Utils.holdingFishingRod() && ModuleUtils.rcTick == 1) {
            disableTicks = 1;
            function = false;
        }

        if (Utils.isReplay() || Utils.isLobby()) {
            disableTicks = 1;
            function = false;
        }

        a(boxSize, myPosition);

        boolean correctHeldItem = !weaponOnly.isToggled();
        if (!correctHeldItem) {
            boolean holdingWeapon = false;
            holdingWeapon = Utils.holdingWeapon(); // Weapon check
            correctHeldItem = holdingWeapon;
        }

        function = correctHeldItem && disableTicks < 0 && closest != -1 && closest < boxSize * boxSize;

        if (lastPosition != null && !onGround && lastPosition.y > myPosition.y && myPosition.y > startFallHeight) {
            startFallHeight = myPosition.y;
        } else if (onGround && myPosition.y < startFallHeight) {
            if (startFallHeight - myPosition.y > 3 && !mc.thePlayer.capabilities.allowFlying) { // Check if you took fall damage
                disableTicks = 5;
                function = false;
            }
            startFallHeight = -Double.MAX_VALUE;
        }
        lastPosition = myPosition;
    }

    private void a(double boxSize, Vec3 myPosition) {
        closest = -1;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == null || entity == mc.thePlayer || entity.isDead) {
                continue;
            }
            if (entity instanceof EntityPlayer) {
                if (Utils.isFriended((EntityPlayer) entity)) {
                    continue;
                }
                if (((EntityPlayer) entity).deathTime != 0) {
                    continue;
                }
                if (AntiBot.isBot(entity) || (Utils.isTeammate(entity) && ignoreTeammates.isToggled())) {
                    continue;
                }
            }
            else {
                continue;
            }
            double maxRange = activationDist.getInput();
            if (mc.thePlayer.getDistanceToEntity(entity) < maxRange + maxRange / 3) { // simple distance check
                Vec3 position = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                double distanceSq = position.distanceToSq(myPosition);
                if (closest == -1 || distanceSq < closest) {
                    closest = distanceSq;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity c02 = (C02PacketUseEntity) e.getPacket();
            keystrokesmod.script.model.Entity enemy = keystrokesmod.script.model.Entity.convert(c02.getEntityFromWorld(mc.theWorld));
            if (enemy != ScriptDefaults.client.getPlayer()) {
                return;
            }
            int enemyHT = enemy.getHurtTime();
            if (Objects.equals(String.valueOf(c02.getAction()), "ATTACK")) {
                if (enemyHT <= hurttime.getInput()) {
                    disableTicks = 1;
                    function = false;
                }
                /*Vec3 ps = new Vec3(c02.getEntityFromWorld(mc.theWorld).posX, c02.getEntityFromWorld(mc.theWorld).posY, c02.getEntityFromWorld(mc.theWorld).posZ);
                Vec3 p2 = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                if (checkDistance.isToggled() && ps.distanceToSq(p2)) {

                }*/
            }
        }
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (function) {
            if (delay == -1) {
                delay = Utils.time();
                blink = true;
            }
            if (delay > 0 && (Utils.time() - delay) >= latency.getInput()) {
                delay = -1;
                blink = false;
            }
        }
        else {
            blink = false;
            delay = -1;
        }
    }

}