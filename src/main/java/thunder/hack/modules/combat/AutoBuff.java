package thunder.hack.modules.combat;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.potion.PotionUtil;
import net.minecraft.util.Hand;
import thunder.hack.core.impl.ModuleManager;
import thunder.hack.events.impl.EventAfterRotate;
import thunder.hack.events.impl.EventPostSync;
import thunder.hack.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanParent;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.PlayerUtility;

import javax.lang.model.element.ModuleElement;

public final class AutoBuff extends Module {
    private final Setting<Boolean> strength = new Setting<>("Strength", true);
    private final Setting<Boolean> speed = new Setting<>("SpeedPot", true);
    private final Setting<Boolean> fire = new Setting<>("FireRes", true);
    private final Setting<BooleanParent> heal = new Setting<>("Heal", new BooleanParent(true));
    private final Setting<Integer> healthH = new Setting<>("Health", 8, 0, 20).withParent(heal);
    private final Setting<BooleanParent> regen = new Setting<>("Regeneration", new BooleanParent(true));
    private final Setting<TriggerOn> triggerOn = new Setting<>("TriggerOn", TriggerOn.LackOfRegen).withParent(regen);
    private final Setting<Integer> healthR = new Setting<>("HP", 8, 0, 20, v-> triggerOn.is(TriggerOn.Health)).withParent(regen);
    private final Setting<Boolean> onDaGround = new Setting<>("Only while on ground", true);
    private final Setting<Boolean> pauseAura = new Setting<>("PauseAura", false);

    public Timer timer = new Timer();
    private static AutoBuff instance;
    private boolean spoofed = false;

    public AutoBuff() {
        super("AutoBuff", Category.COMBAT);
        instance = this;
    }

    public static int getPotionSlot(Potions potion) {
        for (int i = 0; i < 9; ++i)
            if (isStackPotion(mc.player.getInventory().getStack(i), potion))
                return i;
        return -1;
    }

    public static boolean isPotionOnHotBar(Potions potions) {
        return getPotionSlot(potions) != -1;
    }

    public static boolean isStackPotion(ItemStack stack, Potions potion) {
        if (stack == null)
            return false;

        if (stack.getItem() == Items.SPLASH_POTION) {
            StatusEffect id = null;

            switch (potion) {
                case STRENGTH -> id = StatusEffects.STRENGTH;
                case SPEED -> id = StatusEffects.SPEED;
                case FIRERES -> id = StatusEffects.FIRE_RESISTANCE;
                case HEAL -> id = StatusEffects.INSTANT_HEALTH;
                case REGEN -> id = StatusEffects.REGENERATION;
            }

            for (StatusEffectInstance effect : PotionUtil.getPotion(stack).getEffects()) {
                if (effect.getEffectType() == id) {
                    return true;
                }
            }
        }
        return false;
    }

    public static AutoBuff getInstance() {
        return instance;
    }

    @EventHandler
    public void onPostRotationSet(EventAfterRotate event) {
        if (Aura.target != null && mc.player.getAttackCooldownProgress(1) > 0.5f)
            return;
        if (mc.player.age > 80 && shouldThrow()) {
            mc.player.setPitch(90);
            spoofed = true;
        }
    }

    private boolean shouldThrow() {
        return (!mc.player.hasStatusEffect(StatusEffects.SPEED) && isPotionOnHotBar(Potions.SPEED) && speed.getValue())
                || (!mc.player.hasStatusEffect(StatusEffects.STRENGTH) && isPotionOnHotBar(Potions.STRENGTH) && strength.getValue())
                || (!mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) && isPotionOnHotBar(Potions.FIRERES) && fire.getValue())
                || (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthH.getValue() && isPotionOnHotBar(Potions.HEAL) && heal.getValue().isEnabled())
                || ((!mc.player.hasStatusEffect(StatusEffects.REGENERATION) && triggerOn.is(TriggerOn.LackOfRegen)) || (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthR.getValue() && triggerOn.is(TriggerOn.Health))
                && isPotionOnHotBar(Potions.FIRERES) && regen.getValue().isEnabled());
    }

    @EventHandler
    public void onPostSync(EventPostSync e) {
        if (Aura.target != null && mc.player.getAttackCooldownProgress(1) > 0.5f)
            return;

        if (onDaGround.getValue() && !mc.player.isOnGround())
            return;

        if (mc.player.age > 80 && shouldThrow() && timer.passedMs(1000) && spoofed) {
            if (!mc.player.hasStatusEffect(StatusEffects.SPEED) && isPotionOnHotBar(Potions.SPEED) && speed.getValue())
                throwPotion(Potions.SPEED);

            if (!mc.player.hasStatusEffect(StatusEffects.STRENGTH) && isPotionOnHotBar(Potions.STRENGTH) && strength.getValue())
                throwPotion(Potions.STRENGTH);

            if (!mc.player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE) && isPotionOnHotBar(Potions.FIRERES) && fire.getValue())
                throwPotion(Potions.FIRERES);

            if (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthH.getValue() && heal.getValue().isEnabled() && isPotionOnHotBar(Potions.HEAL))
                throwPotion(Potions.HEAL);

            if (((!mc.player.hasStatusEffect(StatusEffects.REGENERATION) && triggerOn.is(TriggerOn.LackOfRegen)) ||
                    (mc.player.getHealth() + mc.player.getAbsorptionAmount() < healthR.getValue() && triggerOn.is(TriggerOn.Health)))
                    && isPotionOnHotBar(Potions.REGEN) && regen.getValue().isEnabled())
                throwPotion(Potions.REGEN);

            sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
            timer.reset();
            spoofed = false;
        }
    }

    public void throwPotion(Potions potion) {
        if(pauseAura.getValue())
            ModuleManager.aura.pause();
        sendPacket(new UpdateSelectedSlotC2SPacket(getPotionSlot(potion)));
        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id));
    }

    public enum Potions {
        STRENGTH, SPEED, FIRERES, HEAL, REGEN
    }

    public enum TriggerOn {
        LackOfRegen, Health
    }
}