package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

import java.awt.*;
import java.io.IOException;

public class HUD extends Module {
    public static SliderSetting theme;
    private static SliderSetting outline;
    public static ButtonSetting alphabeticalSort;
    private static SliderSetting backgroundAlpha;
    private static SliderSetting gap;
    private static ButtonSetting alignRight;
    private static ButtonSetting lowercase;
    private static ButtonSetting removeCloset;
    private static ButtonSetting removeRender;
    private static ButtonSetting removeScripts;
    public static ButtonSetting showInfo;
    public static int posX = 5;
    public static int posY = 70;
    private boolean isAlphabeticalSort;
    private boolean canShowInfo;
    private String[] outlineModes = new String[] { "None", "Full", "Side" };

    private static double gapv;
    private static int a;

    public HUD() {
        super("HUD", Module.category.render);
        this.registerSetting(new DescriptionSetting("Right click bind to hide modules."));
        this.registerSetting(theme = new SliderSetting("Theme", 0, Theme.themes));
        this.registerSetting(outline = new SliderSetting("Outline", 0, outlineModes));
        this.registerSetting(new ButtonSetting("Edit position", () -> {
            mc.displayGuiScreen(new EditScreen());
        }));
        this.registerSetting(alignRight = new ButtonSetting("Align right", false));
        this.registerSetting(alphabeticalSort = new ButtonSetting("Alphabetical sort", false));
        //this.registerSetting(drawBackground = new ButtonSetting("Draw background", false));
        this.registerSetting(backgroundAlpha = new SliderSetting("Background alpha", 50, 0, 100, 1));
        this.registerSetting(gap = new SliderSetting("Gap", 2, 0, 10, 0.5));
        this.registerSetting(lowercase = new ButtonSetting("Lowercase", false));
        this.registerSetting(removeCloset = new ButtonSetting("Remove closet modules", false));
        this.registerSetting(removeRender = new ButtonSetting("Remove render modules", false));
        this.registerSetting(removeScripts = new ButtonSetting("Remove scripts", false));
        this.registerSetting(showInfo = new ButtonSetting("Show module info", true));
    }

    public void onEnable() {
        ModuleManager.sort();
    }

    public void guiButtonToggled(ButtonSetting b) {
        if (b == alphabeticalSort || b == showInfo) {
            ModuleManager.sort();
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }
        gapv = gap.getInput();
        a = (int) (backgroundAlpha.getInput() * 2.55);
        int alpha = new Color(0, 0, 0, a).getRGB();
        if (isAlphabeticalSort != alphabeticalSort.isToggled()) {
            isAlphabeticalSort = alphabeticalSort.isToggled();
            ModuleManager.sort();
        }
        if (canShowInfo != showInfo.isToggled()) {
            canShowInfo = showInfo.isToggled();
            ModuleManager.sort();
        }
        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }
        for (Module module : ModuleManager.organizedModules) {
            module.getInfoUpdate();
            if (Module.sort) {
                break;
            }
        }
        if (Module.sort) {
            ModuleManager.sort();
        }
        Module.sort = false;
        int yPos = posY;
        double n2 = 0.0;
        String previousModule = "";
        int lastXPos = 0;
        try {
            for (Module module : ModuleManager.organizedModules) {
                if (module.isEnabled() && module != this) {
                    if (module.isHidden()) {
                        continue;
                    }
                    if (module == ModuleManager.commandLine) {
                        continue;
                    }
                    if (removeRender.isToggled() && module.moduleCategory() == category.render) {
                        continue;
                    }
                    if (removeScripts.isToggled() && module.moduleCategory() == category.scripts) {
                        continue;
                    }
                    if (removeCloset.isToggled() && module.closetModule) {
                        continue;
                    }
                    String moduleName = module.getNameInHud();
                    if (showInfo.isToggled() && !module.getInfo().isEmpty()) {
                        moduleName += " §7" + module.getInfo();
                    }
                    if (lowercase.isToggled()) {
                        moduleName = moduleName.toLowerCase();
                    }
                    int color = Theme.getGradient((int) theme.getInput(), n2);
                    int xPos = posX;
                    if (alignRight.isToggled()) {
                        xPos -= mc.fontRendererObj.getStringWidth(moduleName);
                    }
                    if (backgroundAlpha.getInput() != 0) {
                        RenderUtils.drawRect(xPos - 1, yPos - 1, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 0.5, yPos + mc.fontRendererObj.FONT_HEIGHT + 1, alpha);
                    }
                    if (outline.getInput() == 1 && n2 == 0.0) { // top
                        RenderUtils.drawRect(xPos - 2, yPos - 2, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 1.5, yPos - 1, color);
                    }
                    if (theme.getInput() == 0) {
                        n2 -= 120;
                    } else {
                        n2 -= 12;
                    }
                    if (n2 != 0 && outline.getInput() == 1) { // between
                        double difference = mc.fontRendererObj.getStringWidth(previousModule) - mc.fontRendererObj.getStringWidth(moduleName);
                        if (alphabeticalSort.isToggled() && difference < 0) {
                            RenderUtils.drawRect(xPos - 2, yPos - 2, xPos - difference - 2, yPos - 1, color);
                        }
                        else if (alignRight.isToggled()) {
                            RenderUtils.drawRect(xPos - difference - 2, yPos - 2, xPos - 1, yPos - 1, color);
                        }
                        else {
                            RenderUtils.drawRect(xPos + mc.fontRendererObj.getStringWidth(moduleName) + 0.5, yPos - 2, xPos + difference + mc.fontRendererObj.getStringWidth(moduleName) + 1.5, yPos - 1, color);
                        }
                    }
                    if (outline.getInput() > 0) { // sides
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(xPos + mc.fontRendererObj.getStringWidth(moduleName) + 0.5, yPos - 1, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 1.5, yPos + mc.fontRendererObj.FONT_HEIGHT + 1, color);
                        }
                        else {
                            RenderUtils.drawRect(xPos - 2, yPos - 1, xPos - 1, yPos + mc.fontRendererObj.FONT_HEIGHT + 1, color);
                        }
                    }
                    if (outline.getInput() == 1) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(xPos - 2, yPos - 1, xPos - 1, yPos + mc.fontRendererObj.FONT_HEIGHT + 1, color);
                        }
                        else {
                            RenderUtils.drawRect(xPos + mc.fontRendererObj.getStringWidth(moduleName) + 0.5, yPos - 1, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 1.5, yPos + mc.fontRendererObj.FONT_HEIGHT + (gapv / 2), color);
                        }
                    }
                    mc.fontRendererObj.drawString(moduleName, xPos, (float) yPos, color, true);
                    previousModule = moduleName;
                    lastXPos = xPos;
                    yPos += mc.fontRendererObj.FONT_HEIGHT + gapv;
                }
            }
        }
        catch (Exception e) {
            Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
            e.printStackTrace();
        }
        if (outline.getInput() == 1) { // bottom
            RenderUtils.drawRect(lastXPos - 2, yPos - 1, lastXPos + mc.fontRendererObj.getStringWidth(previousModule) + 1.5, yPos, Theme.getGradient((int) theme.getInput(), n2));
        }
    }

    public static int getLongestModule(FontRenderer fr) {
        int length = 0;

        for (Module module : ModuleManager.organizedModules) {
            if (module.isEnabled()) {
                String moduleName = module.getName();
                if (showInfo.isToggled() && !module.getInfo().isEmpty()) {
                    moduleName += " §7" + module.getInfo();
                }
                if (lowercase.isToggled()) {
                    moduleName = moduleName.toLowerCase();
                }
                if (fr.getStringWidth(moduleName) > length) {
                    length = fr.getStringWidth(moduleName);
                }
            }
        }
        return length;
    }

    static class EditScreen extends GuiScreen {
        final String example = "This is an-Example-HUD";
        GuiButtonExt resetPosition;
        boolean d = false;
        int miX = 0;
        int miY = 0;
        int maX = 0;
        int maY = 0;
        int aX = 5;
        int aY = 70;
        int laX = 0;
        int laY = 0;
        int lmX = 0;
        int lmY = 0;
        int clickMinX = 0;

        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            this.aX = HUD.posX;
            this.aY = HUD.posY;
        }

        public void drawScreen(int mX, int mY, float pt) {
            drawRect(0, 0, this.width, this.height, -1308622848);
            int miX = this.aX;
            int miY = this.aY;
            int maX = miX + 50;
            int maY = miY + 32;
            int[] clickPos = this.d(this.mc.fontRendererObj, this.example);
            this.miX = miX;
            this.miY = miY;
            if (clickPos == null) {
                this.maX = maX;
                this.maY = maY;
                this.clickMinX = miX;
            }
            else {
                this.maX = clickPos[0];
                this.maY = clickPos[1];
                this.clickMinX = clickPos[2];
            }
            HUD.posX = miX;
            HUD.posY = miY;
            ScaledResolution res = new ScaledResolution(this.mc);
            int x = res.getScaledWidth() / 2 - 84;
            int y = res.getScaledHeight() / 2 - 20;
            RenderUtils.drawColoredString("Edit the HUD position by dragging.", '-', x, y, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            } catch (IOException var12) {
            }

            super.drawScreen(mX, mY, pt);
        }

        private int[] d(FontRenderer fr, String t) {
            if (empty()) {
                int x = this.miX;
                int y = this.miY;
                String[] var5 = t.split("-");

                for (String s : var5) {
                    if (HUD.alignRight.isToggled()) {
                        x += mc.fontRendererObj.getStringWidth(var5[0]) - mc.fontRendererObj.getStringWidth(s);
                    }
                    fr.drawString(s, (float) x, (float) y, Color.white.getRGB(), true);
                    y += fr.FONT_HEIGHT + 2;
                }
            }
            else {
                int longestModule = getLongestModule(mc.fontRendererObj);
                int n = this.miY;
                double n2 = 0.0;
                String previousModule = "";
                int lastXPos = 0;
                try {
                    for (Module module : ModuleManager.organizedModules) {
                        if (module.isEnabled() && !(module instanceof HUD)) {
                            if (module.isHidden()) {
                                continue;
                            }
                            if (module == ModuleManager.commandLine) {
                                continue;
                            }
                            if (removeRender.isToggled() && module.moduleCategory() == category.render) {
                                continue;
                            }
                            if (removeScripts.isToggled() && module.moduleCategory() == category.scripts) {
                                continue;
                            }
                            if (removeCloset.isToggled() && module.closetModule) {
                                continue;
                            }
                            String moduleName = module.getNameInHud();
                            if (showInfo.isToggled() && !module.getInfo().isEmpty()) {
                                moduleName += " §7" + module.getInfo();
                            }
                            if (lowercase.isToggled()) {
                                moduleName = moduleName.toLowerCase();
                            }
                            int color = Theme.getGradient((int) theme.getInput(), n2);
                            int xPos = posX;
                            if (alignRight.isToggled()) {
                                xPos -= mc.fontRendererObj.getStringWidth(moduleName);
                            }
                            if (outline.getInput() == 1 && n2 == 0.0) { // top
                                RenderUtils.drawRect(xPos - 2, n - 2, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 1.5, n - 1, color);
                            }
                            if (n2 != 0 && outline.getInput() == 1) { // between
                                double difference = mc.fontRendererObj.getStringWidth(previousModule) - mc.fontRendererObj.getStringWidth(moduleName);
                                RenderUtils.drawRect(xPos - difference - 2, n - 2, xPos - 1, n - 1, color);
                            }
                            if (theme.getInput() == 0) {
                                n2 -= 120;
                            } else {
                                n2 -= 12;
                            }
                            if (backgroundAlpha.getInput() != 0) {
                                RenderUtils.drawRect(xPos - 1, n - 1, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 0.5, n + mc.fontRendererObj.FONT_HEIGHT + (gapv / 2), a);
                            }
                            if (n2 != 0 && outline.getInput() == 1) { // between
                                double difference = mc.fontRendererObj.getStringWidth(previousModule) - mc.fontRendererObj.getStringWidth(moduleName);
                                if (alphabeticalSort.isToggled() && difference < 0) {
                                    RenderUtils.drawRect(xPos - 2, n - 2, xPos - difference - 2, n - 1, color);
                                }
                                else if (alignRight.isToggled()) {
                                    RenderUtils.drawRect(xPos - difference - 2, n - 2, xPos - 1, n - 1, color);
                                }
                                else {
                                    RenderUtils.drawRect(xPos + mc.fontRendererObj.getStringWidth(moduleName), n - 2, xPos - 1 + difference + mc.fontRendererObj.getStringWidth(moduleName), n - 1, color);
                                }
                            }
                            if (outline.getInput() > 0) { // sides
                                if (alignRight.isToggled()) {
                                    RenderUtils.drawRect(xPos + mc.fontRendererObj.getStringWidth(moduleName) + 0.5, n - 1, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 1.5, n + mc.fontRendererObj.FONT_HEIGHT + 1, color);
                                }
                                else {
                                    RenderUtils.drawRect(xPos - 2, n - 1, xPos - 1, n + mc.fontRendererObj.FONT_HEIGHT + 1, color);
                                }
                            }
                            if (outline.getInput() == 1) {
                                if (alignRight.isToggled()) {
                                    RenderUtils.drawRect(xPos - 2, n - 1, xPos - 1, n + mc.fontRendererObj.FONT_HEIGHT + 1, color);
                                }
                                else {
                                    RenderUtils.drawRect(xPos + mc.fontRendererObj.getStringWidth(moduleName) + 0.5, n - 1, xPos + mc.fontRendererObj.getStringWidth(moduleName) + 1.5, n + mc.fontRendererObj.FONT_HEIGHT + 1, color);
                                }
                            }
                            mc.fontRendererObj.drawString(moduleName, xPos, (float) n, color, true);
                            previousModule = moduleName;
                            lastXPos = xPos;
                            n += mc.fontRendererObj.FONT_HEIGHT + 2;
                        }
                    }
                }
                catch (Exception e) {
                    Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
                    e.printStackTrace();
                }
                if (outline.getInput() == 1) { // bottom
                    RenderUtils.drawRect(lastXPos - 2, n - 1, lastXPos + mc.fontRendererObj.getStringWidth(previousModule) + 1.5, n, Theme.getGradient((int) theme.getInput(), n2));
                }
                return new int[]{this.miX + longestModule, n, this.miX - longestModule};
            }
            return null;
        }

        protected void mouseClickMove(int mX, int mY, int b, long t) {
            super.mouseClickMove(mX, mY, b, t);
            if (b == 0) {
                if (this.d) {
                    this.aX = this.laX + (mX - this.lmX);
                    this.aY = this.laY + (mY - this.lmY);
                } else if (mX > this.clickMinX && mX < this.maX && mY > this.miY && mY < this.maY) {
                    this.d = true;
                    this.lmX = mX;
                    this.lmY = mY;
                    this.laX = this.aX;
                    this.laY = this.aY;
                }

            }
        }

        protected void mouseReleased(int mX, int mY, int s) {
            super.mouseReleased(mX, mY, s);
            if (s == 0) {
                this.d = false;
            }

        }

        public void actionPerformed(GuiButton b) {
            if (b == this.resetPosition) {
                this.aX = HUD.posX = 5;
                this.aY = HUD.posY = 70;
            }

        }

        public boolean doesGuiPauseGame() {
            return false;
        }

        private boolean empty() {
            for (Module module : ModuleManager.organizedModules) {
                if (module.isEnabled() && !module.getName().equals("HUD")) {
                    if (module.isHidden()) {
                        continue;
                    }
                    if (module == ModuleManager.commandLine) {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        }
    }
}
