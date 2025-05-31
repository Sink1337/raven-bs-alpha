package keystrokesmod.module.impl.movement;

import keystrokesmod.Raven;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.*;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.awt.*;

public class LongJump extends Module {
    private SliderSetting mode;

    private SliderSetting boostSetting;
    private SliderSetting verticalMotion;
    private SliderSetting motionDecay;

    private ButtonSetting manual;
    private ButtonSetting onlyWithVelocity;
    private KeySetting disableKey, flatKey;

    private ButtonSetting allowStrafe;
    private ButtonSetting invertYaw;
    private ButtonSetting stopMovement;
    private ButtonSetting hideExplosion;
    public ButtonSetting spoofItem;
    private ButtonSetting beginFlat;
    private ButtonSetting silentSwing;
    private ButtonSetting renderFloatProgress;

    private KeySetting verticalKey;
    private SliderSetting pitchVal;

    public String[] modes = new String[]{"Float", "Boost"};

    private boolean manualWasOn;

    private float yaw;
    private float pitch;

    private boolean notMoving;
    private boolean enabled, swapped;
    public static boolean function;

    private int boostTicks = -1;
    public int lastSlot = -1, spoofSlot = -1;
    private int stopTime;
    private int rotateTick;
    private double motionDecayVal;

    private long fireballTime;
    private long MAX_EXPLOSION_DIST_SQ = 9;
    private long FIREBALL_TIMEOUT = 750L;

    public static boolean stopVelocity;
    public static boolean stopModules;
    public static boolean slotReset;
    public static int slotResetTicks;

    private int firstSlot = -1;

    private int color = new Color(0, 187, 255, 255).getRGB();
    private float barWidth = 60;
    private float barHeight = 4;
    private float filledWidth;
    private float barX;
    private float barY;

    public LongJump() {
        super("Long Jump", category.movement);
        this.registerSetting(mode = new SliderSetting("Mode", 0, modes));

        this.registerSetting(manual = new ButtonSetting("Manual", false));
        this.registerSetting(onlyWithVelocity = new ButtonSetting("Only while velocity enabled", false));
        this.registerSetting(disableKey = new KeySetting("Disable key", Keyboard.KEY_SPACE));

        this.registerSetting(boostSetting = new SliderSetting("Horizontal boost", 1.7, 0.0, 2.0, 0.05));
        this.registerSetting(verticalMotion = new SliderSetting("Vertical motion", 0, 0.3, 1, 0.01));
        this.registerSetting(motionDecay = new SliderSetting("Motion decay", "%", 43, 1, 100, 1));
        this.registerSetting(allowStrafe = new ButtonSetting("Allow strafe", false));
        this.registerSetting(invertYaw = new ButtonSetting("Invert yaw", true));
        this.registerSetting(stopMovement = new ButtonSetting("Stop movement", false));
        this.registerSetting(hideExplosion = new ButtonSetting("Hide explosion", false));
        this.registerSetting(spoofItem = new ButtonSetting("Spoof item", false));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
        this.registerSetting(renderFloatProgress = new ButtonSetting("Render progress", false));

        this.registerSetting(beginFlat = new ButtonSetting("Begin flat", false));
        this.registerSetting(verticalKey = new KeySetting("Vertical key", Keyboard.KEY_SPACE));
        this.registerSetting(flatKey = new KeySetting("Flat key", Keyboard.KEY_SPACE));
    }

    public void guiUpdate() {
        this.onlyWithVelocity.setVisible(manual.isToggled(), this);
        this.disableKey.setVisible(manual.isToggled(), this);
        this.spoofItem.setVisible(!manual.isToggled(), this);
        this.silentSwing.setVisible(!manual.isToggled(), this);

        //this.renderFloatProgress.setVisible(mode.getInput() == 0, this);

        this.verticalMotion.setVisible(mode.getInput() == 0, this);
        this.motionDecay.setVisible(mode.getInput() == 0, this);
        this.beginFlat.setVisible(mode.getInput() == 0, this);
        this.verticalKey.setVisible(mode.getInput() == 0 && beginFlat.isToggled(), this);
        this.flatKey.setVisible(mode.getInput() == 0 && !beginFlat.isToggled(), this);
    }

    public void onEnable() {
        if (ModuleUtils.profileTicks <= 1) {
            return;
        }
        if (!manual.isToggled()) {
            if (Utils.getTotalHealth(mc.thePlayer) <= 3) {
                Utils.sendMessage("&cPrevented throwing fireball due to low health");
                disable();
                return;
            }
            enabled();
        }
        filledWidth = 0;
        final ScaledResolution scaledResolution = new ScaledResolution(mc);
        int[] disp = {scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight(), scaledResolution.getScaleFactor()};
        barX = disp[0] / 2 - barWidth / 2;
        barY = disp[1] / 2 + 12;
    }

    public void onDisable() {
        disabled();
    }

    /*public boolean onChat(String chatMessage) {
        String msg = util.strip(chatMessage);

        if (msg.equals("Build height limit reached!")) {
            client.print("fb fly build height");
            modules.disable(scriptName);
            return false;
        }
        return true;
    }*/

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (manual.isToggled()) {
            manualWasOn = true;
        }
        else {
            if (manualWasOn) {
                disabled();
            }
            manualWasOn = false;
        }

        if (manual.isToggled() && disableKey.isPressed() && Utils.jumpDown()) {
            function = false;
            disabled();
        }

        if (spoofItem.isToggled() && lastSlot != -1 && !manual.isToggled()) {
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
        }

        if (swapped && rotateTick == 0) {
            resetSlot();
            swapped = false;
        }

        if (!function) {
            if (manual.isToggled() && !enabled && (!onlyWithVelocity.isToggled() || onlyWithVelocity.isToggled() && ModuleManager.velocity.isEnabled())) {
                if (ModuleUtils.threwFireballLow) {
                    ModuleManager.velocity.disableVelo = true;
                    enabled();
                }
            }
            return;
        }

        if (enabled) {
            if (!Utils.isMoving() && mode.getInput() == 0) notMoving = true;
            if (boostSetting.getInput() == 0 && verticalMotion.getInput() == 0) {
                Utils.modulePrint("&cValues are set to 0!");
                disabled();
                return;
            }
            int fireballSlot = setupFireballSlot(true);
            if (fireballSlot != -1) {
                if (!manual.isToggled()) {
                    lastSlot = spoofSlot = mc.thePlayer.inventory.currentItem;
                    if (mc.thePlayer.inventory.currentItem != fireballSlot) {
                        mc.thePlayer.inventory.currentItem = fireballSlot;
                        swapped = true;
                    }

                }
                //("Set fireball slot");
                rotateTick = 1;
                if (stopMovement.isToggled()) {
                    stopTime = 1;
                }
            } // auto disables if -1
            enabled = false;
        }

        if (notMoving) {
            motionDecayVal = 21;
        } else {
            motionDecayVal = (motionDecay.getInput() / 2.5);
        }
        if (stopTime == -1 && ++boostTicks > (!verticalKey() ? 33/*flat motion ticks*/ : (!notMoving ? 32/*normal motion ticks*/ : 33/*vertical motion ticks*/))) {
            disabled();
            return;
        }

        if (fireballTime > 0 && (System.currentTimeMillis() - fireballTime) > FIREBALL_TIMEOUT) {
            Utils.modulePrint("&cFireball timed out.");
            disabled();
            return;
        }
        if (boostTicks > 0) {
            if (mode.getInput() == 0) {
                modifyVertical(); // has to be onPreUpdate
            }
            if (allowStrafe.isToggled() && boostTicks < 32) {
                Utils.setSpeed(Utils.getHorizontalSpeed(mc.thePlayer));
            }
        }

        filledWidth = (barWidth * boostTicks / (!notMoving ? 32 : 33));

        if (stopMovement.isToggled() && !notMoving) {
            if (stopTime > 0) {
                ++stopTime;
            }
        }

        if (mc.thePlayer.onGround && boostTicks > 2) {
            disabled();
        }

        if (firstSlot != -1) {
            mc.thePlayer.inventory.currentItem = firstSlot;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent ev) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (ev.phase == TickEvent.Phase.END) {
            if (mc.currentScreen != null || !renderFloatProgress.isToggled() || mode.getInput() != 0 || !function) {
                return;
            }
        }
        color = Theme.getGradient((int) HUD.theme.getInput(), 0);
        RenderUtils.drawRoundedRectangle(barX, barY, barX + barWidth, barY + barHeight, 3, 0xFF555555);
        RenderUtils.drawRoundedRectangle(barX, barY, barX + filledWidth, barY + barHeight, 3, color);
    }

    @SubscribeEvent
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (lastSlot != -1) {
            spoofSlot = e.slot;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPreMotion(PreMotionEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (rotateTick >= 3) {
            rotateTick = 0;
        }
        if (rotateTick >= 1) {
            if ((invertYaw.isToggled() || stopMovement.isToggled()) && !notMoving) {
                if (!stopMovement.isToggled()) {
                    yaw = mc.thePlayer.rotationYaw - 180f;
                    pitch = 90f;
                } else {
                    yaw = mc.thePlayer.rotationYaw - 180f;
                    pitch = 66.3f;//(float) pitchVal.getInput();
                }
            } else {
                yaw = mc.thePlayer.rotationYaw;
                pitch = 90f;
            }
            e.setRotations(yaw, pitch);
        }
        if (rotateTick > 0 && ++rotateTick >= 3) {
            int fireballSlot = setupFireballSlot(false);
            if (fireballSlot != -1) {
                fireballTime = System.currentTimeMillis();
                if (!manual.isToggled()) {
                    mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                    if (silentSwing.isToggled()) {
                        mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
                    }
                    else {
                        mc.thePlayer.swingItem();
                        if (!spoofItem.isToggled()) {
                            mc.getItemRenderer().resetEquippedProgress();
                        }
                    }
                }
                stopVelocity = true;
            }
        }
        if (boostTicks == 1) {
            modifyHorizontal();
            stopVelocity = false;
            if (!manual.isToggled() && !allowStrafe.isToggled() && mode.getInput() == 1) {
                disabled();
            }
        }
    }

    @SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        if (!function) {
            return;
        }
        mc.thePlayer.movementInput.jump = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) // called last in order to apply fix
    public void onMoveInput(PrePlayerInputEvent e) {
        if (!function) {
            return;
        }
        if (rotateTick > 0 || fireballTime > 0) {
            if (Utils.isMoving()) e.setForward(1);
            e.setStrafe(0);
        }
        if (notMoving && boostTicks < 3) {
            e.setForward(0);
            e.setStrafe(0);
            Utils.setSpeed(0);
        }
        if (stopMovement.isToggled() && !notMoving && stopTime >= 1) {
            e.setForward(0);
            e.setStrafe(0);
            Utils.setSpeed(0);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!function) {
            return;
        }
        Packet packet = e.getPacket();
        if (packet instanceof S27PacketExplosion && boostTicks == -1) {
            S27PacketExplosion s27 = (S27PacketExplosion) packet;
            if (fireballTime == 0 || mc.thePlayer.getPosition().distanceSq(s27.getX(), s27.getY(), s27.getZ()) > MAX_EXPLOSION_DIST_SQ) {
                e.setCanceled(true);
            }
            if (!mc.thePlayer.onGround) {
                disabled();
            }

            stopTime = -1;
            fireballTime = 0;
            resetSlot();
            boostTicks = 0; // +1 on next pre update
        } else if (packet instanceof S08PacketPlayerPosLook) {
            Utils.modulePrint("&cReceived setback, disabling.");
            disabled();
        }

        if (hideExplosion.isToggled() && fireballTime != 0 && (packet instanceof S0EPacketSpawnObject || packet instanceof S2APacketParticles || packet instanceof S29PacketSoundEffect)) {
            e.setCanceled(true);
        }
    }

    private int getFireballSlot() {
        int n = -1;
        for (int i = 0; i < 9; ++i) {
            final ItemStack getStackInSlot = mc.thePlayer.inventory.getStackInSlot(i);
            if (getStackInSlot != null && getStackInSlot.getItem() == Items.fire_charge) {
                n = i;
                break;
            }
        }
        return n;
    }

    private void enabled() {
        slotReset = false;
        slotResetTicks = 0;
        enabled = function = true;

        stopModules = true;
    }

    private void disabled() {
        fireballTime = rotateTick = stopTime = 0;
        boostTicks = -1;
        resetSlot();
        enabled = function = notMoving = stopVelocity = stopModules = swapped = false;
        filledWidth = 0;
        if (!manual.isToggled()) {
            disable();
        }
    }

    private int setupFireballSlot(boolean pre) {
        // only cancel bad packet right click on the tick we are sending it
        int fireballSlot = getFireballSlot();
        if (fireballSlot == -1) {
            Utils.modulePrint("&cFireball not found.");
            disabled();
        } else if ((pre && Utils.distanceToGround(mc.thePlayer) > 3)/* || (!pre && !PacketUtil.canRightClickItem())*/) { //needs porting
            Utils.modulePrint("&cCan't throw fireball right now.");
            disabled();
            fireballSlot = -1;
        }
        return fireballSlot;
    }

    private void resetSlot() {
        if (lastSlot != -1 && !manual.isToggled()) {
            mc.thePlayer.inventory.currentItem = lastSlot;
            lastSlot = -1;
            spoofSlot = -1;
            firstSlot = -1;
            if (spoofItem.isToggled()) {
                ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(false);
                ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(false);
            }
        }
        slotReset = true;
        stopModules = false;
    }

    private int getSpeedLevel() {
        for (PotionEffect potionEffect : mc.thePlayer.getActivePotionEffects()) {
            if (potionEffect.getEffectName().equals("potion.moveSpeed")) {
                return potionEffect.getAmplifier() + 1;
            }
            return 0;
        }
        return 0;
    }

    // only apply horizontal boost once
    void modifyHorizontal() {
        if (boostSetting.getInput() != 0) {

            double speed = boostSetting.getInput() - Utils.randomizeDouble(0.0001, 0);
            if (Utils.isMoving()) {
                Utils.setSpeed(speed);
            }
        }
    }

    private void modifyVertical() {
        if (verticalMotion.getInput() != 0) {
            double ver = ((!notMoving ? verticalMotion.getInput() : 1.16 /*vertical*/) * (1.0 / (1.0 + (0.05 * getSpeedLevel())))) + Utils.randomizeDouble(0.0001, 0.1);
            double decay = motionDecayVal / 1000;
            if (mode.getInput() == 0) {
                if (boostTicks > 1 && !verticalKey()) {
                    if (boostTicks > 1 || boostTicks <= (!notMoving ? 32/*horizontal motion ticks*/ : 33/*vertical motion ticks*/)) {
                        mc.thePlayer.motionY = Utils.randomizeDouble(0.0101, 0.01);
                    }
                } else {
                    if (boostTicks >= 1 && boostTicks <= (!notMoving ? 32/*horizontal motion ticks*/ : 33/*vertical motion ticks*/)) {
                        mc.thePlayer.motionY = ver - boostTicks * decay;
                    } else if (boostTicks >= (!notMoving ? 32/*horizontal motion ticks*/ : 33/*vertical motion ticks*/) + 3) {
                        mc.thePlayer.motionY = mc.thePlayer.motionY + 0.028;
                        Utils.modulePrint("If you get this clip it & send in the raven bs v2 discord");
                    }
                }

            }
        }
    }

    private boolean verticalKey() {
        if (notMoving) return true;
        return beginFlat.isToggled() ? verticalKey.isPressed() : !flatKey.isPressed();
    }
}