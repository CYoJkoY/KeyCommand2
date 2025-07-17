// KeyCommandMod.java

package com.keycommand2.keycommand2mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Mod(modid = KeyCommandMod.MODID, name = KeyCommandMod.NAME, version = KeyCommandMod.VERSION)
public class KeyCommandMod {
    public static final String MODID = "keycommandmod";
    public static final String NAME = "Key Command Mod";
    public static final String VERSION = "1.0";

    public static final Logger LOGGER = LogManager.getLogger(KeyCommandMod.class);
    public static KeyCommandMod instance;

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final KeyBinding guiKey = new KeyBinding("快捷菜单", Keyboard.KEY_F, "key.categories.keycommand");
    
    public static boolean showCoordinates = false;
    
    public static final GuiInventory.PathSequenceManager pathSequenceManager = new GuiInventory.PathSequenceManager();
    
    static {
        GuiInventory.initializePathSequences();
    }
    
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
        
        ClientRegistry.registerKeyBinding(guiKey);

        // 注册事件监听器
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Key Command Mod initialized!");
        
        MinecraftForge.EVENT_BUS.register(new AutoLoopHandler());
        MinecraftForge.EVENT_BUS.register(GlobalEventListener .instance);
        
        GuiInventory.initializePathSequences();
        GuiInventory.loadDisplaySettings();
    }
    
    public static void tryAutoStartLoop() {
        try {
            Path path = Paths.get("config/KeyCommand/keycommandmod_autorun.json");
            LOGGER.info("尝试读取配置文件: " + path.toAbsolutePath());
            if (!Files.exists(path)) {
                LOGGER.info("配置文件不存在，跳过自动执行");
                return;
            }

            String s = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            // 简单解析
            boolean autoLoop = s.contains("\"autoLoop\":true");
            String seq = "";
            int loopCount = 1;
            int idx = s.indexOf("\"loopSequence\":\"");
            if (idx != -1) {
                int start = idx + "\"loopSequence\":\"".length();
                int end = s.indexOf("\"", start);
                if (end > start) seq = s.substring(start, end);
            }
            idx = s.indexOf("\"loopCount\":");
            if (idx != -1) {
                int start = idx + "\"loopCount\":".length();
                int end = s.indexOf("}", start);
                if (end == -1) end = s.length();
                try {
                    loopCount = Integer.parseInt(s.substring(start, end).replaceAll("[^\\d\\-]", ""));
                } catch (Exception ignore) {}
            }
            
            if (autoLoop && !seq.isEmpty() && loopCount == -1) {
                LOGGER.info("检测到需要自动无限循环执行：" + seq);
                final String fSeq = seq;
                final int fLoopCount = loopCount;
                
                // 添加玩家实体检查
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().world == null) {
                        LOGGER.error("无法启动自动循环：玩家或世界未初始化");
                        return;
                    }
                    
                    try {
                        GuiInventory.loopCount = fLoopCount;
                        GuiInventory.loopCounter = 0;
                        GuiInventory.isLooping = true;
                        GuiInventory.runPathSequence(fSeq);
                    } catch (Exception e) {
                        LOGGER.error("自动循环执行失败", e);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("读取自动循环配置失败", e);
        }
    }
    
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && showCoordinates) {
            EntityPlayerSP player = mc.player;
            if (player != null) {
                // 获取鼠标屏幕坐标
                ScaledResolution sr = new ScaledResolution(mc);
                int scaledMouseX = (int) (Mouse.getX() * sr.getScaledWidth() / mc.displayWidth * 4 * GuiInventory.currentScaleX);
                int scaledMouseY = (int) ((sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1) * 4 * GuiInventory.currentScaleY);
                
                String coordText = String.format("鼠标坐标: %d, %d", scaledMouseX, scaledMouseY);
                
                // 使用Minecraft实例获取字体渲染器和屏幕尺寸
                int screenWidth = event.getResolution().getScaledWidth();
                int screenHeight = event.getResolution().getScaledHeight();
                int textWidth = mc.fontRenderer.getStringWidth(coordText);
                
                // 在屏幕右下角绘制
                int xPos = screenWidth - textWidth - 20;
                int yPos = screenHeight - 20; // 底部留20像素边距
                mc.fontRenderer.drawStringWithShadow(coordText, xPos, yPos, 0x55FF55);
            }
        }
    }
    
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKeyState()) { // Check if the key is pressed down
            if (guiKey.isKeyDown()) {
                mc.displayGuiScreen(new GuiInventory());
                LOGGER.info("Opened custom inventory GUI.");
            }
        } else {
            LOGGER.debug("Key released.");
        }
    }
    
    public static class GlobalEventListener {
    	public static final GlobalEventListener instance = new GlobalEventListener();
    	
    	@SubscribeEvent
        public void onClientTick(ClientTickEvent event) { // 全局客户端Tick
    		
            // 在 tick 结束阶段更新自动技能
            if (event.phase == TickEvent.Phase.END) {
            	
                // 空值检查
                if (mc == null || mc.player == null || mc.playerController == null || mc.world == null) {
                    // LOGGER.warn("关键组件未初始化，跳过本tick处理");
                    return;
                }
            		
            	try {
            		GuiInventory.updateAutoSkills();
            	} catch (Exception e) {
            		LOGGER.error("更新自动技能时出错", e);
            	}
            	
                try {
                    // 调用自动进食检查方法
                	GuiInventory.checkAutoEat(mc.player);
                } catch (Exception e) {
                    LOGGER.error("执行自动进食检查时出错", e);
                }
            	
            }
   
    	}
    }
    
    public static class GuiInventory extends GuiScreen {
        private static final Minecraft mc = Minecraft.getMinecraft();
        // 添加静态变量保存上次的状态
        private static String sLastCategory = "自动操作";
        private static int sLastPage = 0;
        private static final Map<String, Integer> CATEGORY_PAGE_MAP = new HashMap<>();
        
        private int currentPage = sLastPage;
        private String currentCategory = sLastCategory;
        private final List<String> categories = Arrays.asList("设置","自动操作");
        private final Map<String, List<String>> categoryItems = new HashMap<>();
        private final Map<String, List<String>> categoryItemNames = new HashMap<>();
        
        // 路径序列管理器
        public static final PathSequenceManager pathSequenceManager = new PathSequenceManager();

        private static int loopCount = 1; // 默认循环1次
        private static int loopCounter = 0; // 当前运行次数计数
        private static boolean isLooping = false; // 是否正在循环中

        // 新增自动进食相关字段
        private static final File CONFIG_FILE = new File("config/KeyCommand/keycommand_autoeat.json");
        private static boolean autoEatEnabled = false;
        public static boolean isEating = false;
        public static int originalHotbarSlot = -1;
        public static ItemStack swappedItem = ItemStack.EMPTY;
        
        // 模拟点击所需参数
        private static int customScreenWidth = 1920;
        private static int customScreenHeight = 1200;
        private static int customScreenScale = 125;
        public static double currentScaleX = (double)customScreenWidth / 1920.0;
        public static double currentScaleY = (double)customScreenHeight / 1200.0;
        
        // 自动丢弃设置
        private static final File FILTER_CONFIG_FILE = new File("config/KeyCommand/filter_config.json");
        private static List<String> blacklistFilters = new ArrayList<>();
        private static List<String> whitelistFilters = new ArrayList<>();
        
        // 自动技能配置文件
        private static final File SKILL_CONFIG_FILE = new File("config/KeyCommand/keycommand_skills.json");
        private static boolean autoSkillEnabled = false;
        private static long[] skillCooldowns = new long[] {60, 60, 60, 60}; // 默认冷却时间 60 秒
        private static long[] skillCounts = new long[] {0, 0, 0, 0}; // 技能单独计时
        private static final int[] SKILL_KEYS = {Keyboard.KEY_R, Keyboard.KEY_Z, Keyboard.KEY_X, Keyboard.KEY_C};
        private static boolean[] autoSkillIndividualEnabled = new boolean[] {false, false, false, false};
        
        // 自动精炼配置
        private static final File REFINE_CONFIG_FILE = new File("config/KeyCommand/refine_config.json");
        private static List<String> blacklistRefines = new ArrayList<>();
        private static List<String> whitelistRefines = new ArrayList<>();
        
        static {
        	loadAutoEatConfig(); // 自动进食配置加载
        	loadFilterConfig(); // 自动丢弃配置加载
        	loadSkillConfig(); // 自动技能配置加载
        	loadRefineConfig(); // 自动精炼配置加载
        }
        
        public static boolean isGuiOpen() {	return mc.currentScreen != null; } // 检测是否有GUI打开
        
        // 自动技能加载配置方法
        private static void loadSkillConfig() {
            try {
                if (SKILL_CONFIG_FILE.exists()) {
                	String jsonContent = new String(Files.readAllBytes(SKILL_CONFIG_FILE.toPath()), StandardCharsets.UTF_8); // UTF-8读取文件
                	JsonObject json = new JsonParser().parse(jsonContent).getAsJsonObject();
                	JsonArray cooldownArray = json.getAsJsonArray("cooldowns");
                    for (int i = 0; i < 4; i++) {
                        skillCooldowns[i] = cooldownArray.get(i).getAsLong();
                    }

                    // 加载每个技能是否启用的状态
                    if (json.has("individualEnabled")) {
                        JsonArray individualEnabledArray = json.getAsJsonArray("individualEnabled");
                        for (int i = 0; i < 4; i++) {
                            autoSkillIndividualEnabled[i] = individualEnabledArray.get(i).getAsBoolean();
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("加载自动技能配置失败", e);
            }
        }

        public static void saveSkillConfig() {
            try {
                JsonObject json = new JsonObject();
                JsonArray cooldownArray = new JsonArray();
                for (long cooldown : skillCooldowns) {
                    cooldownArray.add(cooldown);
                }
                json.add("cooldowns", cooldownArray);
                
                // 保存每个技能是否启用的状态
                JsonArray individualEnabledArray = new JsonArray();
                for (boolean enabled : autoSkillIndividualEnabled) {
                    individualEnabledArray.add(enabled);
                }
                json.add("individualEnabled", individualEnabledArray);
                
                // 创建父目录（如果不存在）
                if (!SKILL_CONFIG_FILE.getParentFile().exists()) {
                    SKILL_CONFIG_FILE.getParentFile().mkdirs();
                }
                
                // 使用原子写入方式
                Path tempFile = Files.createTempFile(SKILL_CONFIG_FILE.getParentFile().toPath(), "skillconfig", ".tmp");
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                    writer.write(json.toString());
                }
                Files.move(tempFile, SKILL_CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                LOGGER.error("保存自动技能配置失败", e);
            }
        }
        
        private static void updateAutoSkills() {
            if (!autoSkillEnabled || skillCooldowns == null) return; 
            for (int i = 0; i < 4; i++) {
                final int currentIndex = i; // 创建final副本
                
                // 仅当技能启用时才执行
                if (!autoSkillIndividualEnabled[currentIndex]) continue;

                skillCounts[currentIndex]++;
                if (isGuiOpen()) continue; 
                
                //在GUI打开时继续计时，超过冷却时间且未在GUI界面则立刻施放
                if (skillCounts[currentIndex] >= skillCooldowns[currentIndex]*20 + 3) {
                    int key = SKILL_KEYS[currentIndex];
                    new DelayAction(3 * currentIndex, () -> {
                        simulateKey(key, true);
                        skillCounts[currentIndex] = 0; // 重置独立计时器
                        new DelayAction(2, () -> {
                            simulateKey(key, false);
                        }).accept(mc.player); 
                    }).accept(mc.player);   
                }
            }
        }
        
        private static void loadAutoEatConfig() {
            try {
                if (CONFIG_FILE.exists()) {
                    JsonObject json = new JsonParser().parse(new FileReader(CONFIG_FILE)).getAsJsonObject();
                    autoEatEnabled = json.get("enabled").getAsBoolean();
                }
            } catch (Exception e) {
                LOGGER.error("加载自动进食配置失败", e);
            }
        }
        
        public static void saveAutoEatConfig() {
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                JsonObject json = new JsonObject();
                json.addProperty("enabled", autoEatEnabled);
                writer.write(json.toString());
            } catch (Exception e) {
                LOGGER.error("保存自动进食配置失败", e);
            }
        }
        
        public GuiInventory() {
            // 恢复当前分类和页码
            if (CATEGORY_PAGE_MAP.containsKey(currentCategory)) {
                currentPage = CATEGORY_PAGE_MAP.get(currentCategory);
            } else {
                currentPage = sLastPage;
            }
			// 初始化路径序列管理器
            initializePathSequences();
            
            // 初始化分类物品

            // 设置分类
            List<String> SetItems = new ArrayList<>();List<String> SetItemNames = new ArrayList<>();
            SetItems.add("togglecoords"); SetItemNames.add("坐标显示");
            SetItems.add("autoeat"); SetItemNames.add("自动进食");
            SetItems.add("setresolution");SetItemNames.add("分辨率");
            SetItems.add("filterconfig"); SetItemNames.add("丢弃设置");
            SetItems.add("autoskill"); SetItemNames.add("自动技能");
            SetItems.add("refineconfig"); SetItemNames.add("精炼设置");
            
            categoryItems.put("设置", SetItems);categoryItemNames.put("设置", SetItemNames);
            
            // 自动操作分类
            List<String> AutoItems = new ArrayList<>();List<String> AutoItemNames = new ArrayList<>();
            AutoItems.add("setloop"); AutoItemNames.add("循环次数");
            AutoItems.add("stop"); AutoItemNames.add("停止运行");
            
            AutoItems.add("path:每日"); AutoItemNames.add("签到");
            AutoItems.add("path:在线"); AutoItemNames.add("在线");
            AutoItems.add("path:收集"); AutoItemNames.add("开收集");
            AutoItems.add("path:邮件√"); AutoItemNames.add("拿邮件");
            AutoItems.add("path:邮件×"); AutoItemNames.add("删邮件");
            AutoItems.add("path:精炼"); AutoItemNames.add("自动精炼");
            
            AutoItems.add("path:停机坪"); AutoItemNames.add("停机坪");
            AutoItems.add("path:沙漠宫"); AutoItemNames.add("沙漠宫");
            AutoItems.add("path:雪中转"); AutoItemNames.add("雪中转");
            AutoItems.add("path:雪旅馆"); AutoItemNames.add("雪旅馆");
            AutoItems.add("path:瞭望台"); AutoItemNames.add("瞭望台");
            AutoItems.add("path:悬崖屋"); AutoItemNames.add("悬崖屋");
            AutoItems.add("path:监测站"); AutoItemNames.add("监测站");
            
            categoryItems.put("自动操作", AutoItems);categoryItemNames.put("自动操作", AutoItemNames);
        }

        // 初始化路径序列管理器 - 支持多步操作
        public static void initializePathSequences() {
            
        	// 每日签到路径序列
            PathSequence DailySequence = new PathSequence("每日");
            
            PathStep Daily1 = new PathStep(new double[]{Double.NaN, Double.NaN, Double.NaN});
            Daily1.addAction(player -> sendChatCommand("/ove as"));
            Daily1.addAction(new DelayAction(12));
            Daily1.addAction(player -> GuiInventory.simulateMouseClick(935, 778, true));
            Daily1.addAction(player -> GuiInventory.simulateMouseClick(940, 790, true));
            
            Daily1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,true));
            Daily1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,false));
            
            DailySequence.addStep(Daily1);
            
            pathSequenceManager.addSequence(DailySequence);
            
            // 在线礼包路径序列
            PathSequence AtSequence = new PathSequence("在线");
            
            PathStep At1 = new PathStep(new double[]{Double.NaN, Double.NaN, Double.NaN});
            At1.addAction(player -> sendChatCommand("/ove at"));
            At1.addAction(new DelayAction(12));
            At1.addAction(player -> GuiInventory.simulateMouseClick(1231, 405, true)); // 1
            At1.addAction(new DelayAction(6));
            At1.addAction(player -> GuiInventory.simulateMouseClick(1240, 420, true)); // 1
            
            At1.addAction(player -> GuiInventory.simulateMouseClick(1200, 485, true)); // 2
            At1.addAction(player -> GuiInventory.simulateMouseClick(1204, 570, true)); // 3
            At1.addAction(player -> GuiInventory.simulateMouseClick(1195, 654, true)); // 4
            At1.addAction(player -> GuiInventory.simulateMouseClick(1205, 736, true)); // 5
            At1.addAction(player -> GuiInventory.simulateMouseClick(1195, 824, true)); // 6
            At1.addAction(player -> GuiInventory.simulateMouseClick(1196, 910, true)); // 7

            At1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,true));
            At1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,false));
            
            AtSequence.addStep(At1);
            
            pathSequenceManager.addSequence(AtSequence);
        	
            // 开收集路径序列
            PathSequence CollectSequence = new PathSequence("收集");
            
            PathStep Collect1 = new PathStep(new double[]{Double.NaN, Double.NaN, Double.NaN});
            Collect1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_U,true));
            Collect1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_U,false));
            Collect1.addAction(new DelayAction(12));
            Collect1.addAction(player -> autoChestClick(player, 5));
            Collect1.addAction(new DelayAction(6));
            Collect1.addAction(player -> autoChestClick(player, 12));
            
            Collect1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,true));
            Collect1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,false));
            
            CollectSequence.addStep(Collect1);
            
            pathSequenceManager.addSequence(CollectSequence);
            
            // 拿邮件路径序列
            PathSequence GetEmailSequence = new PathSequence("邮件√");
            
            PathStep GetEmail1 = new PathStep(new double[]{Double.NaN, Double.NaN, Double.NaN});
            GetEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_Y,true));
            GetEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_Y,false));
            GetEmail1.addAction(new DelayAction(12));
            GetEmail1.addAction(player -> GuiInventory.simulateMouseClick(533, 355, true)); // 第一个邮件
            GetEmail1.addAction(new DelayAction(6));
            GetEmail1.addAction(player -> GuiInventory.simulateMouseClick(750, 355, true)); // 第一个邮件
            GetEmail1.addAction(new DelayAction(6));
            GetEmail1.addAction(player -> GuiInventory.simulateMouseClick(1340, 1000, true)); // 领取附件
            GetEmail1.addAction(new DelayAction(6));
            GetEmail1.addAction(player -> GuiInventory.simulateMouseClick(960, 618, true)); // 领取并删除
            
            GetEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,true));
            GetEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,false));
            
            GetEmailSequence.addStep(GetEmail1);
            
            pathSequenceManager.addSequence(GetEmailSequence);
            
            // 删邮件路径序列
            PathSequence DelEmailSequence = new PathSequence("邮件×");
            
            PathStep DelEmail1 = new PathStep(new double[]{Double.NaN, Double.NaN, Double.NaN});
            DelEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_Y,true));
            DelEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_Y,false));
            DelEmail1.addAction(new DelayAction(12));
            DelEmail1.addAction(player -> GuiInventory.simulateMouseClick(533, 355, true)); // 第一个邮件
            DelEmail1.addAction(player -> GuiInventory.simulateMouseClick(750, 355, true)); // 第一个邮件
            DelEmail1.addAction(new DelayAction(6));
            DelEmail1.addAction(player -> GuiInventory.simulateMouseClick(805, 405, true)); // 删除
            DelEmail1.addAction(new DelayAction(6));
            DelEmail1.addAction(player -> GuiInventory.simulateMouseClick(965, 625, true)); // 确认删除

            DelEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,true));
            DelEmail1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,false));
            
            DelEmailSequence.addStep(DelEmail1);
            
            pathSequenceManager.addSequence(DelEmailSequence);
            
            // 停机坪路径序列
            PathSequence TJPSequence = new PathSequence("停机坪");
            
            PathStep TJP1 = new PathStep(new double[]{-1729, 51, -2046});
            TJP1.addAction(new DelayAction(16));
            TJP1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1729, 49, -2047)));
            TJP1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP1.addAction(new DelayAction(48));
            
            PathStep TJP2 = new PathStep(new double[]{-1726, 51, -2033});
            TJP2.addAction(new DelayAction(16));
            TJP2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1726, 51, -2032)));
            TJP2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP2.addAction(new DelayAction(48));
            
            PathStep TJP3 = new PathStep(new double[]{-1741, 51, -2032});
            TJP3.addAction(new DelayAction(16));
            TJP3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1738, 50, -2030)));
            TJP3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP3.addAction(new DelayAction(48));
            TJP3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1740, 50, -2030)));
            TJP3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP3.addAction(new DelayAction(48));
            TJP3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1740, 50, -2034)));
            TJP3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP3.addAction(new DelayAction(48));
            TJP3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1738, 50, -2034)));
            TJP3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP3.addAction(new DelayAction(48));
            
            PathStep TJP4 = new PathStep(new double[]{-1757, 51, -2043});
            TJP4.addAction(new DelayAction(16));
            TJP4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1755, 51, -2042)));
            TJP4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP4.addAction(new DelayAction(48));
            TJP4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1759, 51, -2042)));
            TJP4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP4.addAction(new DelayAction(48));
            
            PathStep TJP5 = new PathStep(new double[]{-1781, 51, -2020});
            TJP5.addAction(new DelayAction(16));
            TJP5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1779, 51, -2019)));
            TJP5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP5.addAction(new DelayAction(48));
            
            PathStep TJP6 = new PathStep(new double[]{-1786, 51, -2019});
            TJP6.addAction(new DelayAction(16));
            TJP6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1788, 51, -2019)));
            TJP6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP6.addAction(new DelayAction(48));
            
            PathStep TJP7 = new PathStep(new double[]{-1807, 51, -2025});
            TJP7.addAction(new DelayAction(16));
            TJP7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1807, 51, -2023)));
            TJP7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP7.addAction(new DelayAction(48));
            TJP7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1808, 51, -2024)));
            TJP7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP7.addAction(new DelayAction(48));
            TJP7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1809, 51, -2025)));
            TJP7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP7.addAction(new DelayAction(48));
            
            PathStep TJP8 = new PathStep(new double[]{-1826, 51, -2026});
            TJP8.addAction(new DelayAction(16));
            TJP8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1824, 51, -2025)));
            TJP8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP8.addAction(new DelayAction(48));
            TJP8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1825, 51, -2024)));
            TJP8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP8.addAction(new DelayAction(48));
            
            PathStep TJP9 = new PathStep(new double[]{-1830, 51, -2046});
            TJP9.addAction(new DelayAction(16));
            TJP9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1827, 54, -2047)));
            TJP9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP9.addAction(new DelayAction(48));
            
            PathStep TJP10 = new PathStep(new double[]{-1852, 49, -2046});
            TJP10.addAction(new DelayAction(16));
            TJP10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1853, 49, -2045)));
            TJP10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP10.addAction(new DelayAction(48));
            TJP10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1854, 49, -2047)));
            TJP10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP10.addAction(new DelayAction(48));
            TJP10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1851, 49, -2048)));
            TJP10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP10.addAction(new DelayAction(48));
            
            PathStep TJP11 = new PathStep(new double[]{-1852, 49, -2026});
            TJP11.addAction(new DelayAction(16));
            TJP11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1851, 49, -2024)));
            TJP11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP11.addAction(new DelayAction(48));
            TJP11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1854, 49, -2025)));
            TJP11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP11.addAction(new DelayAction(48));
            TJP11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1856, 51, -2026)));
            TJP11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP11.addAction(new DelayAction(48));
            
            PathStep TJP12 = new PathStep(new double[]{-1870, 51, -2028});
            TJP12.addAction(new DelayAction(16));
            TJP12.addAction(player -> rightClickOnBlock(player, new BlockPos(-1870, 51, -2026)));
            TJP12.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP12.addAction(new DelayAction(48));
            
            PathStep TJP13 = new PathStep(new double[]{-1884, 51, -2028});
            TJP13.addAction(new DelayAction(16));
            TJP13.addAction(player -> rightClickOnBlock(player, new BlockPos(-1886, 51, -2026)));
            TJP13.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP13.addAction(new DelayAction(48));
            
            PathStep TJP14 = new PathStep(new double[]{-1886, 51, -2040});
            TJP14.addAction(new DelayAction(16));
            TJP14.addAction(player -> rightClickOnBlock(player, new BlockPos(-1886, 51, -2036)));
            TJP14.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP14.addAction(new DelayAction(48));
            TJP14.addAction(player -> rightClickOnBlock(player, new BlockPos(-1886, 51, -2044)));
            TJP14.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP14.addAction(new DelayAction(48));
            
            PathStep TJP15 = new PathStep(new double[]{-1883, 51, -2052});
            TJP15.addAction(new DelayAction(16));
            TJP15.addAction(player -> rightClickOnBlock(player, new BlockPos(-1885, 51, -2053)));
            TJP15.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP15.addAction(new DelayAction(48));
            
            PathStep TJP16 = new PathStep(new double[]{-1874, 51, -2052});
            TJP16.addAction(new DelayAction(16));
            TJP16.addAction(player -> rightClickOnBlock(player, new BlockPos(-1874, 51, -2054)));
            TJP16.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP16.addAction(new DelayAction(48));
            
            PathStep TJP17 = new PathStep(new double[]{-1865, 51, -2054});
            TJP17.addAction(new DelayAction(16));
            TJP17.addAction(player -> rightClickOnBlock(player, new BlockPos(-1865, 49, -2057)));
            TJP17.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP17.addAction(new DelayAction(48));
            
            PathStep TJP18 = new PathStep(new double[]{-1856, 51, -2054});
            TJP18.addAction(new DelayAction(16));
            TJP18.addAction(player -> rightClickOnBlock(player, new BlockPos(-1855, 51, -2054)));
            TJP18.addAction(player -> GuiInventory.takeAllItemsFromChest());
            TJP18.addAction(new DelayAction(48));
            TJP18.addAction(player -> setPlayerViewAngles(player, -90.0f, -20.0f));
            TJP18.addAction(new DelayAction(5));
            TJP18.addAction(player -> GuiInventory.dropItemsByNameFilter());
            TJP18.addAction(new DelayAction(180));
            
            TJPSequence.addStep(TJP1);
            TJPSequence.addStep(TJP2);
            TJPSequence.addStep(TJP3);
            TJPSequence.addStep(TJP4);
            TJPSequence.addStep(TJP5);
            TJPSequence.addStep(TJP6);
            TJPSequence.addStep(TJP7);
            TJPSequence.addStep(TJP8);
            TJPSequence.addStep(TJP9);
            TJPSequence.addStep(TJP10);
            TJPSequence.addStep(TJP11);
            TJPSequence.addStep(TJP12);
            TJPSequence.addStep(TJP13);
            TJPSequence.addStep(TJP14);
            TJPSequence.addStep(TJP15);
            TJPSequence.addStep(TJP16);
            TJPSequence.addStep(TJP17);
            TJPSequence.addStep(TJP18);
            
            pathSequenceManager.addSequence(TJPSequence);
            
            // 沙漠宫殿路径序列
            PathSequence SMGDSequence = new PathSequence("沙漠宫");
            
            PathStep SMGD1 = new PathStep(new double[]{-1598, 106, -2444});
            SMGD1.addAction(new DelayAction(16));
            SMGD1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1596, 106, -2444)));
            SMGD1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD1.addAction(new DelayAction(48));
            
            PathStep SMGD2 = new PathStep(new double[]{-1590, 106, -2434});
            SMGD2.addAction(new DelayAction(16));
            SMGD2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1588, 106, -2434)));
            SMGD2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD2.addAction(new DelayAction(48));
            
            PathStep SMGD3 = new PathStep(new double[]{-1618, 106, -2436});
            SMGD3.addAction(new DelayAction(16));
            SMGD3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1620, 106, -2434)));
            SMGD3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD3.addAction(new DelayAction(48));
            SMGD3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1620, 106, -2438)));
            SMGD3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD3.addAction(new DelayAction(48));

            PathStep SMGD4 = new PathStep(new double[]{-1611, 106, -2445});
            SMGD4.addAction(new DelayAction(16));
            SMGD4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1612, 106, -2446)));
            SMGD4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD4.addAction(new DelayAction(48));
            
            PathStep SMGD5 = new PathStep(new double[]{-1610, 106, -2454});
            SMGD5.addAction(new DelayAction(16));
            SMGD5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1612, 106, -2452)));
            SMGD5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD5.addAction(new DelayAction(48));
            
            PathStep SMGD6 = new PathStep(new double[]{-1610, 106, -2468});
            SMGD6.addAction(new DelayAction(16));
            SMGD6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1612, 106, -2469)));
            SMGD6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD6.addAction(new DelayAction(48));
            
            PathStep SMGD7 = new PathStep(new double[]{-1610, 106, -2478});
            SMGD7.addAction(new DelayAction(16));
            SMGD7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1612, 106, -2476)));
            SMGD7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD7.addAction(new DelayAction(48));
            
            PathStep SMGD8 = new PathStep(new double[]{-1618, 106, -2487});
            SMGD8.addAction(new DelayAction(16));
            SMGD8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1620, 106, -2488)));
            SMGD8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD8.addAction(new DelayAction(48));
            
            PathStep SMGD9 = new PathStep(new double[]{-1590, 106, -2487});
            SMGD9.addAction(new DelayAction(16));
            SMGD9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1588, 106, -2486)));
            SMGD9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD9.addAction(new DelayAction(48));
            
            PathStep SMGD10 = new PathStep(new double[]{-1597, 106, -2478});
            SMGD10.addAction(new DelayAction(16));
            SMGD10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1596, 106, -2477)));
            SMGD10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD10.addAction(new DelayAction(48));
            
            PathStep SMGD11 = new PathStep(new double[]{-1597, 106, -2469});
            SMGD11.addAction(new DelayAction(16));
            SMGD11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1596, 106, -2470)));
            SMGD11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD11.addAction(new DelayAction(48));
            
            PathStep SMGD12 = new PathStep(new double[]{-1611, 114, -2465});
            SMGD12.addAction(new DelayAction(16));
            SMGD12.addAction(player -> rightClickOnBlock(player, new BlockPos(-1612, 114, -2465)));
            SMGD12.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD12.addAction(new DelayAction(48));
            
            PathStep SMGD13 = new PathStep(new double[]{-1594, 114, -2461});
            SMGD13.addAction(new DelayAction(16));
            SMGD13.addAction(player -> rightClickOnBlock(player, new BlockPos(-1593, 114, -2464)));
            SMGD13.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD13.addAction(new DelayAction(48));
            SMGD13.addAction(player -> rightClickOnBlock(player, new BlockPos(-1593, 114, -2458)));
            SMGD13.addAction(player -> GuiInventory.takeAllItemsFromChest());
            SMGD13.addAction(new DelayAction(48));
            SMGD13.addAction(player -> GuiInventory.dropItemsByNameFilter());
            SMGD13.addAction(new DelayAction(180));
            
            SMGDSequence.addStep(SMGD1);
            SMGDSequence.addStep(SMGD2);
            SMGDSequence.addStep(SMGD3);
            SMGDSequence.addStep(SMGD4);
            SMGDSequence.addStep(SMGD5);
            SMGDSequence.addStep(SMGD6);
            SMGDSequence.addStep(SMGD7);
            SMGDSequence.addStep(SMGD8);
            SMGDSequence.addStep(SMGD9);
            SMGDSequence.addStep(SMGD10);
            SMGDSequence.addStep(SMGD11);
            SMGDSequence.addStep(SMGD12);
            SMGDSequence.addStep(SMGD13);
            
            pathSequenceManager.addSequence(SMGDSequence);
            
            // 雪山中转站路径序列
            PathSequence XSZZZSequence = new PathSequence("雪中转");
            
            PathStep XSZZZ1 = new PathStep(new double[]{-1299, 103, -2275});
            XSZZZ1.addAction(new DelayAction(16));
            XSZZZ1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1298, 103, -2276)));
            XSZZZ1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ1.addAction(new DelayAction(48));
            
            PathStep XSZZZ2 = new PathStep(new double[]{-1313, 104, -2247});
            XSZZZ2.addAction(new DelayAction(16));
            XSZZZ2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1310, 104, -2246)));
            XSZZZ2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ2.addAction(new DelayAction(48));
            XSZZZ2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1311, 104, -2245)));
            XSZZZ2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ2.addAction(new DelayAction(48));
            XSZZZ2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1315, 104, -2245)));
            XSZZZ2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ2.addAction(new DelayAction(48));
            XSZZZ2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1316, 104, -2246)));
            XSZZZ2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ2.addAction(new DelayAction(48));
            
            PathStep XSZZZ3 = new PathStep(new double[]{-1293, 104, -2246});
            XSZZZ3.addAction(new DelayAction(16));
            XSZZZ3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1292, 104, -2248)));
            XSZZZ3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ3.addAction(new DelayAction(48));
            XSZZZ3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1291, 104, -2247)));
            XSZZZ3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ3.addAction(new DelayAction(48));
            
            PathStep XSZZZ4 = new PathStep(new double[]{-1292, 108, -2238});
            XSZZZ4.addAction(new DelayAction(16));
            XSZZZ4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1291, 108, -2236)));
            XSZZZ4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ4.addAction(new DelayAction(48));
            
            PathStep XSZZZ5 = new PathStep(new double[]{-1296, 108, -2238});
            XSZZZ5.addAction(new DelayAction(16));
            XSZZZ5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1296, 108, -2236)));
            XSZZZ5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ5.addAction(new DelayAction(48));
            
            PathStep XSZZZ6 = new PathStep(new double[]{-1332, 104, -2271});
            XSZZZ6.addAction(new DelayAction(16));
            XSZZZ6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1333, 104, -2269)));
            XSZZZ6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ6.addAction(new DelayAction(48));
            
            PathStep XSZZZ7 = new PathStep(new double[]{-1339, 100, -2272});
            XSZZZ7.addAction(new DelayAction(16));
            XSZZZ7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1339, 100, -2271)));
            XSZZZ7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ7.addAction(new DelayAction(48));
            
            PathStep XSZZZ8 = new PathStep(new double[]{-1343, 88, -2271});
            XSZZZ8.addAction(new DelayAction(16));
            XSZZZ8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1342, 88, -2269)));
            XSZZZ8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ8.addAction(new DelayAction(48));
            
            PathStep XSZZZ9 = new PathStep(new double[]{-1360, 80, -2278});
            XSZZZ9.addAction(new DelayAction(16));
            XSZZZ9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1361, 80, -2277)));
            XSZZZ9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ9.addAction(new DelayAction(48));
            
            PathStep XSZZZ10 = new PathStep(new double[]{-1330, 79, -2323});
            XSZZZ10.addAction(new DelayAction(16));
            XSZZZ10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1328, 79, -2325)));
            XSZZZ10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ10.addAction(new DelayAction(48));
            
            PathStep XSZZZ11 = new PathStep(new double[]{-1329, 83, -2332});
            XSZZZ11.addAction(new DelayAction(16));
            XSZZZ11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1328, 83, -2336)));
            XSZZZ11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ11.addAction(new DelayAction(48));
            XSZZZ11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1325, 83, -2332)));
            XSZZZ11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSZZZ11.addAction(new DelayAction(48));
            XSZZZ11.addAction(player -> GuiInventory.dropItemsByNameFilter());
            XSZZZ11.addAction(new DelayAction(180));
            
            XSZZZSequence.addStep(XSZZZ1);
            XSZZZSequence.addStep(XSZZZ2);
            XSZZZSequence.addStep(XSZZZ3);
            XSZZZSequence.addStep(XSZZZ4);
            XSZZZSequence.addStep(XSZZZ5);
            XSZZZSequence.addStep(XSZZZ6);
            XSZZZSequence.addStep(XSZZZ7);
            XSZZZSequence.addStep(XSZZZ8);
            XSZZZSequence.addStep(XSZZZ9);
            XSZZZSequence.addStep(XSZZZ10);
            XSZZZSequence.addStep(XSZZZ11);
            
            pathSequenceManager.addSequence(XSZZZSequence);
            
            // 雪山旅馆路径序列
            PathSequence XSLGSequence = new PathSequence("雪旅馆");
            

            
            PathStep XSLG1 = new PathStep(new double[]{-1310, 103, -2392});
            XSLG1.addAction(new DelayAction(16));
            XSLG1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1308, 103, -2392)));
            XSLG1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG1.addAction(new DelayAction(48));
            
            PathStep XSLG2 = new PathStep(new double[]{-1315, 103, -2398});
            XSLG2.addAction(new DelayAction(16));
            XSLG2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1317, 103, -2399)));
            XSLG2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG2.addAction(new DelayAction(48));
            XSLG2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1316, 103, -2400)));
            XSLG2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG2.addAction(new DelayAction(48));
            
            PathStep XSLG3 = new PathStep(new double[]{-1338, 103, -2390});
            XSLG3.addAction(new DelayAction(16));
            XSLG3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1341, 103, -2392)));
            XSLG3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG3.addAction(new DelayAction(48));
            XSLG3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1334, 103, -2392)));
            XSLG3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG3.addAction(new DelayAction(48));
            
            PathStep XSLG4 = new PathStep(new double[]{-1353, 103, -2389});
            XSLG4.addAction(new DelayAction(16));
            XSLG4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1355, 103, -2387)));
            XSLG4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG4.addAction(new DelayAction(48));
            
            PathStep XSLG5 = new PathStep(new double[]{-1353, 103, -2417});
            XSLG5.addAction(new DelayAction(16));
            XSLG5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1351, 103, -2420)));
            XSLG5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG5.addAction(new DelayAction(48));
            XSLG5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1351, 103, -2414)));
            XSLG5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG5.addAction(new DelayAction(48));
            
            PathStep XSLG6 = new PathStep(new double[]{-1344, 110, -2402});
            XSLG6.addAction(new DelayAction(16));
            XSLG6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1344, 110, -2404)));
            XSLG6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG6.addAction(new DelayAction(48));
            
            PathStep XSLG7 = new PathStep(new double[]{-1331, 110, -2404});
            XSLG7.addAction(new DelayAction(16));
            XSLG7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1330, 110, -2402)));
            XSLG7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG7.addAction(new DelayAction(48));
            
            PathStep XSLG8 = new PathStep(new double[]{-1342, 110, -2418});
            XSLG8.addAction(new DelayAction(16));
            XSLG8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1344, 110, -2420)));
            XSLG8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG8.addAction(new DelayAction(48));
            
            PathStep XSLG9 = new PathStep(new double[]{-1366, 76, -2382});
            XSLG9.addAction(new DelayAction(16));
            XSLG9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1366, 76, -2381)));
            XSLG9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG9.addAction(new DelayAction(48));
            
            PathStep XSLG10 = new PathStep(new double[]{-1366, 73, -2375});
            XSLG10.addAction(new DelayAction(16));
            XSLG10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1366, 72, -2375)));
            XSLG10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG10.addAction(new DelayAction(48));
            
            PathStep XSLG11 = new PathStep(new double[]{-1397, 69, -2434});
            XSLG11.addAction(new DelayAction(16));
            XSLG11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1394, 68, -2432)));
            XSLG11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG11.addAction(new DelayAction(48));
            
            PathStep XSLG12 = new PathStep(new double[]{-1405, 93, -2450});
            XSLG12.addAction(new DelayAction(16));
            XSLG12.addAction(player -> rightClickOnBlock(player, new BlockPos(-1406, 91, -2450)));
            XSLG12.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG12.addAction(new DelayAction(48));
            
            PathStep XSLG13 = new PathStep(new double[]{-1340, 102, -2441});
            XSLG13.addAction(new DelayAction(16));
            XSLG13.addAction(player -> rightClickOnBlock(player, new BlockPos(-1340, 102, -2443)));
            XSLG13.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XSLG13.addAction(new DelayAction(48));
            XSLG13.addAction(player -> setPlayerViewAngles(player, -170.0f, -20.0f));
            XSLG13.addAction(new DelayAction(5));
            XSLG13.addAction(player -> GuiInventory.dropItemsByNameFilter());
            XSLG13.addAction(new DelayAction(200));
            
            XSLGSequence.addStep(XSLG1);
            XSLGSequence.addStep(XSLG2);
            XSLGSequence.addStep(XSLG3);
            XSLGSequence.addStep(XSLG4);
            XSLGSequence.addStep(XSLG5);
            XSLGSequence.addStep(XSLG6);
            XSLGSequence.addStep(XSLG7);
            XSLGSequence.addStep(XSLG8);
            XSLGSequence.addStep(XSLG9);
            XSLGSequence.addStep(XSLG10);
            XSLGSequence.addStep(XSLG11);
            XSLGSequence.addStep(XSLG12);
            XSLGSequence.addStep(XSLG13);
            
            pathSequenceManager.addSequence(XSLGSequence);
            
            // 瞭望台路径序列
            PathSequence LWTSequence = new PathSequence("瞭望台");
            
            PathStep LWT1 = new PathStep(new double[]{-1597, 107, -2271});
            LWT1.addAction(new DelayAction(16));
            LWT1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1599, 108, -2269)));
            LWT1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT1.addAction(new DelayAction(48));
            
            PathStep LWT2 = new PathStep(new double[]{-1634, 110, -2278});
            LWT2.addAction(new DelayAction(16));
            LWT2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1635, 109, -2276)));
            LWT2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT2.addAction(new DelayAction(48));
            
            PathStep LWT3 = new PathStep(new double[]{-1667, 123, -2269});
            LWT3.addAction(new DelayAction(16));
            LWT3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1670, 122, -2269)));
            LWT3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT3.addAction(new DelayAction(48));
            
            PathStep LWT4 = new PathStep(new double[]{-1656, 119, -2305});
            LWT4.addAction(new DelayAction(16));
            LWT4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1658, 119, -2307)));
            LWT4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT4.addAction(new DelayAction(48));
            
            PathStep LWT5 = new PathStep(new double[]{-1615, 122, -2304});
            LWT5.addAction(new DelayAction(16));
            LWT5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1614, 122, -2305)));
            LWT5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT5.addAction(new DelayAction(48));
            
            PathStep LWT6 = new PathStep(new double[]{-1608, 124, -2306});
            LWT6.addAction(new DelayAction(16));
            LWT6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1608, 124, -2304)));
            LWT6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT6.addAction(new DelayAction(48));
            LWT6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1609, 124, -2305)));
            LWT6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT6.addAction(new DelayAction(48));
            
            PathStep LWT7 = new PathStep(new double[]{-1589, 122, -2295});
            LWT7.addAction(new DelayAction(16));
            LWT7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1588, 122, -2294)));
            LWT7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT7.addAction(new DelayAction(48));
            
            PathStep LWT8 = new PathStep(new double[]{-1609, 130, -2322});
            LWT8.addAction(new DelayAction(16));
            LWT8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1611, 130, -2322)));
            LWT8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT8.addAction(new DelayAction(48));
            LWT8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1609, 130, -2323)));
            LWT8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT8.addAction(new DelayAction(48));
            LWT8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1607, 130, -2322)));
            LWT8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT8.addAction(new DelayAction(48));
            
            PathStep LWT9 = new PathStep(new double[]{-1606, 132, -2331});
            LWT9.addAction(new DelayAction(16));
            LWT9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1607, 133, -2333)));
            LWT9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT9.addAction(new DelayAction(48));
            LWT9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1604, 133, -2333)));
            LWT9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT9.addAction(new DelayAction(48));
            
            PathStep LWT10 = new PathStep(new double[]{-1600, 132, -2333});
            LWT10.addAction(new DelayAction(16));
            LWT10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1601, 132, -2334)));
            LWT10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT10.addAction(new DelayAction(48));
            
            PathStep LWT11 = new PathStep(new double[]{-1578, 118, -2350});
            LWT11.addAction(new DelayAction(16));
            LWT11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1578, 117, -2352)));
            LWT11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT11.addAction(new DelayAction(48));
            
            PathStep LWT12 = new PathStep(new double[]{-1556, 107, -2341});
            LWT12.addAction(new DelayAction(16));
            LWT12.addAction(player -> rightClickOnBlock(player, new BlockPos(-1556, 107, -2343)));
            LWT12.addAction(player -> GuiInventory.takeAllItemsFromChest());
            LWT12.addAction(new DelayAction(48));
            LWT12.addAction(player -> setPlayerViewAngles(player, -90.0f, -20.0f));
            LWT12.addAction(new DelayAction(5));
            LWT12.addAction(player -> GuiInventory.dropItemsByNameFilter());
            LWT12.addAction(new DelayAction(180));
            
            LWTSequence.addStep(LWT1);
            LWTSequence.addStep(LWT2);
            LWTSequence.addStep(LWT3);
            LWTSequence.addStep(LWT4);
            LWTSequence.addStep(LWT5);
            LWTSequence.addStep(LWT6);
            LWTSequence.addStep(LWT7);
            LWTSequence.addStep(LWT8);
            LWTSequence.addStep(LWT9);
            LWTSequence.addStep(LWT10);
            LWTSequence.addStep(LWT11);
            LWTSequence.addStep(LWT12);
            
            pathSequenceManager.addSequence(LWTSequence);
            
            // 悬崖小屋路径序列
            PathSequence XYXWSequence = new PathSequence("悬崖屋");
            
            PathStep XYXW1 = new PathStep(new double[]{-1405, 84, -2575});
            XYXW1.addAction(new DelayAction(16));
            XYXW1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1406, 83, -2575)));
            XYXW1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW1.addAction(new DelayAction(48));
            
            PathStep XYXW2 = new PathStep(new double[]{-1399, 90, -2585});
            XYXW2.addAction(new DelayAction(16));
            XYXW2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1399, 90, -2583)));
            XYXW2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW2.addAction(new DelayAction(48));
            
            PathStep XYXW3 = new PathStep(new double[]{-1394, 96, -2595});
            XYXW3.addAction(new DelayAction(16));
            XYXW3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1394, 96, -2597)));
            XYXW3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW3.addAction(new DelayAction(48));
            
            PathStep XYXW4 = new PathStep(new double[]{-1392, 98, -2597});
            XYXW4.addAction(new DelayAction(16));
            XYXW4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1392, 98, -2598)));
            XYXW4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW4.addAction(new DelayAction(48));
            
            PathStep XYXW5 = new PathStep(new double[]{-1388, 99, -2604});
            XYXW5.addAction(new DelayAction(16));
            XYXW5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1389, 99, -2605)));
            XYXW5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW5.addAction(new DelayAction(48));
            XYXW5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1386, 99, -2605)));
            XYXW5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW5.addAction(new DelayAction(48));
            
            PathStep XYXW6 = new PathStep(new double[]{-1389, 99, -2609});
            XYXW6.addAction(new DelayAction(16));
            XYXW6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1389, 99, -2611)));
            XYXW6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW6.addAction(new DelayAction(48));
            
            PathStep XYXW7 = new PathStep(new double[]{-1396, 99, -2605});
            XYXW7.addAction(new DelayAction(16));
            XYXW7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1397, 99, -2604)));
            XYXW7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW7.addAction(new DelayAction(48));
            
            PathStep XYXW8 = new PathStep(new double[]{-1398, 99, -2609});
            XYXW8.addAction(new DelayAction(16));
            XYXW8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1400, 99, -2610)));
            XYXW8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW8.addAction(new DelayAction(48));
            XYXW8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1397, 99, -2610)));
            XYXW8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW8.addAction(new DelayAction(48));
            
            PathStep XYXW9 = new PathStep(new double[]{-1369, 96, -2614});
            XYXW9.addAction(new DelayAction(16));
            XYXW9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1369, 96, -2616)));
            XYXW9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW9.addAction(new DelayAction(48));
            
            PathStep XYXW10 = new PathStep(new double[]{-1355, 97, -2597});
            XYXW10.addAction(new DelayAction(16));
            XYXW10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1354, 97, -2600)));
            XYXW10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW10.addAction(new DelayAction(48));
            
            PathStep XYXW11 = new PathStep(new double[]{-1346, 93, -2575});
            XYXW11.addAction(new DelayAction(16));
            XYXW11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1344, 93, -2576)));
            XYXW11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW11.addAction(new DelayAction(48));
            
            PathStep XYXW12 = new PathStep(new double[]{-1367, 104, -2543});
            XYXW12.addAction(new DelayAction(16));
            XYXW12.addAction(player -> rightClickOnBlock(player, new BlockPos(-1367, 104, -2541)));
            XYXW12.addAction(player -> GuiInventory.takeAllItemsFromChest());
            XYXW12.addAction(new DelayAction(48));
            XYXW12.addAction(player -> setPlayerViewAngles(player, 1.0f, -20.0f));
            XYXW12.addAction(new DelayAction(5));
            XYXW12.addAction(player -> GuiInventory.dropItemsByNameFilter());
            XYXW12.addAction(new DelayAction(180));
            
            XYXWSequence.addStep(XYXW1);
            XYXWSequence.addStep(XYXW2);
            XYXWSequence.addStep(XYXW3);
            XYXWSequence.addStep(XYXW4);
            XYXWSequence.addStep(XYXW5);
            XYXWSequence.addStep(XYXW6);
            XYXWSequence.addStep(XYXW7);
            XYXWSequence.addStep(XYXW8);
            XYXWSequence.addStep(XYXW9);
            XYXWSequence.addStep(XYXW10);
            XYXWSequence.addStep(XYXW11);
            XYXWSequence.addStep(XYXW12);
            
            pathSequenceManager.addSequence(XYXWSequence);
            
            // 天空监测站路径序列
            PathSequence JCZSequence = new PathSequence("监测站");
            
            PathStep JCZ1 = new PathStep(new double[]{-1342, 138, -2526});
            JCZ1.addAction(new DelayAction(16));
            JCZ1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1345, 138, -2529)));
            JCZ1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ1.addAction(new DelayAction(48));
            JCZ1.addAction(player -> rightClickOnBlock(player, new BlockPos(-1340, 138, -2523)));
            JCZ1.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ1.addAction(new DelayAction(48));
            
            PathStep JCZ2 = new PathStep(new double[]{-1356, 138, -2531});
            JCZ2.addAction(new DelayAction(16));
            JCZ2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1352, 141, -2531)));
            JCZ2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ2.addAction(new DelayAction(48));
            JCZ2.addAction(player -> rightClickOnBlock(player, new BlockPos(-1358, 138, -2532)));
            JCZ2.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ2.addAction(new DelayAction(48));
            
            PathStep JCZ3 = new PathStep(new double[]{-1357, 138, -2522});
            JCZ3.addAction(new DelayAction(16));
            JCZ3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1358, 138, -2525)));
            JCZ3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ3.addAction(new DelayAction(48));
            JCZ3.addAction(player -> rightClickOnBlock(player, new BlockPos(-1359, 138, -2520)));
            JCZ3.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ3.addAction(new DelayAction(48));
            
            PathStep JCZ4 = new PathStep(new double[]{-1357, 138, -2509});
            JCZ4.addAction(new DelayAction(16));
            JCZ4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1357, 138, -2513)));
            JCZ4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ4.addAction(new DelayAction(48));
            JCZ4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1355, 138, -2509)));
            JCZ4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ4.addAction(new DelayAction(48));
            JCZ4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1357, 138, -2507)));
            JCZ4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ4.addAction(new DelayAction(48));
            JCZ4.addAction(player -> rightClickOnBlock(player, new BlockPos(-1354, 138, -2506)));
            JCZ4.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ4.addAction(new DelayAction(48));
            
            PathStep JCZ5 = new PathStep(new double[]{-1344, 138, -2509});
            JCZ5.addAction(new DelayAction(16));
            JCZ5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1344, 138, -2506)));
            JCZ5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ5.addAction(new DelayAction(48));
            JCZ5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1341, 138, -2510)));
            JCZ5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ5.addAction(new DelayAction(48));
            JCZ5.addAction(player -> rightClickOnBlock(player, new BlockPos(-1343, 138, -2512)));
            JCZ5.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ5.addAction(new DelayAction(48));
            JCZ5.addAction(player -> setPlayerViewAngles(player, -58.0f, 5.0f));
            JCZ5.addAction(new DelayAction(5));
            JCZ5.addAction(player -> GuiInventory.dropItemsByNameFilter());
            JCZ5.addAction(new DelayAction(180));
            
            PathStep JCZ6 = new PathStep(new double[]{-1336, 138, -2512});
            JCZ6.addAction(new DelayAction(16));
            JCZ6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1336, 138, -2510)));
            JCZ6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ6.addAction(new DelayAction(48));
            JCZ6.addAction(player -> rightClickOnBlock(player, new BlockPos(-1332, 138, -2514)));
            JCZ6.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ6.addAction(new DelayAction(48));
            
            PathStep JCZ7 = new PathStep(new double[]{-1336, 139, -2518});
            JCZ7.addAction(new DelayAction(16));
            JCZ7.addAction(player -> rightClickOnBlock(player, new BlockPos(-1334, 139, -2517)));
            JCZ7.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ7.addAction(new DelayAction(48));
            
            PathStep JCZ8 = new PathStep(new double[]{-1333, 139, -2526});
            JCZ8.addAction(new DelayAction(16));
            JCZ8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1334, 139, -2524)));
            JCZ8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ8.addAction(new DelayAction(48));
            JCZ8.addAction(player -> rightClickOnBlock(player, new BlockPos(-1332, 139, -2524)));
            JCZ8.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ8.addAction(new DelayAction(48));
            
            PathStep JCZ9 = new PathStep(new double[]{-1351, 118, -2523});
            JCZ9.addAction(new DelayAction(16));
            JCZ9.addAction(player -> rightClickOnBlock(player, new BlockPos(-1351, 118, -2521)));
            JCZ9.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ9.addAction(new DelayAction(48));
            
            PathStep JCZ10 = new PathStep(new double[]{-1334, 101, -2492});
            JCZ10.addAction(new DelayAction(16));
            JCZ10.addAction(player -> rightClickOnBlock(player, new BlockPos(-1333, 101, -2491)));
            JCZ10.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ10.addAction(new DelayAction(48));
            
            PathStep JCZ11 = new PathStep(new double[]{-1322, 91, -2501});
            JCZ11.addAction(new DelayAction(16));
            JCZ11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1324, 91, -2504)));
            JCZ11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ11.addAction(new DelayAction(48));
            JCZ11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1322, 91, -2503)));
            JCZ11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ11.addAction(new DelayAction(48));
            JCZ11.addAction(player -> rightClickOnBlock(player, new BlockPos(-1319, 91, -2500)));
            JCZ11.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ11.addAction(new DelayAction(48));
            
            PathStep JCZ12= new PathStep(new double[]{-1311, 93, -2472});
            JCZ12.addAction(new DelayAction(16));
            JCZ12.addAction(player -> rightClickOnBlock(player, new BlockPos(-1310, 93, -2471)));
            JCZ12.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ12.addAction(new DelayAction(48));
            
            PathStep JCZ13= new PathStep(new double[]{-1352, 95, -2489});
            JCZ13.addAction(new DelayAction(16));
            JCZ13.addAction(player -> rightClickOnBlock(player, new BlockPos(-1351, 95, -2490)));
            JCZ13.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ13.addAction(new DelayAction(48));
            
            PathStep JCZ14= new PathStep(new double[]{-1373, 98, -2508});
            JCZ14.addAction(new DelayAction(16));
            JCZ14.addAction(player -> rightClickOnBlock(player, new BlockPos(-1375, 98, -2507)));
            JCZ14.addAction(player -> GuiInventory.takeAllItemsFromChest());
            JCZ14.addAction(new DelayAction(48));
            JCZ14.addAction(player -> setPlayerViewAngles(player, 90.0f, -20.0f));
            JCZ14.addAction(new DelayAction(5));
            JCZ14.addAction(player -> GuiInventory.dropItemsByNameFilter());
            JCZ14.addAction(new DelayAction(180));
            
            JCZSequence.addStep(JCZ1);
            JCZSequence.addStep(JCZ2);
            JCZSequence.addStep(JCZ3);
            JCZSequence.addStep(JCZ4);
            JCZSequence.addStep(JCZ5);
            JCZSequence.addStep(JCZ6);
            JCZSequence.addStep(JCZ7);
            JCZSequence.addStep(JCZ8);
            JCZSequence.addStep(JCZ9);
            JCZSequence.addStep(JCZ10);
            JCZSequence.addStep(JCZ11);
            JCZSequence.addStep(JCZ12);
            JCZSequence.addStep(JCZ13);
            JCZSequence.addStep(JCZ14);
            
            pathSequenceManager.addSequence(JCZSequence);
            
            // 自动精炼路径序列
            PathSequence RefineSequence = new PathSequence("精炼");
            
            PathStep Refine1 = new PathStep(new double[]{263, 11, -529});
            Refine1.addAction(player -> rightClickOnNearestEntity(player, new BlockPos(265, 11, -530), 1.0)); // 精炼NPC
            Refine1.addAction(new DelayAction(10));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(1125, 835, true)); // 精炼物品
            Refine1.addAction(new DelayAction(6));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(1120, 840, true)); // 精炼物品
            
            Refine1.addAction(new DelayAction(12));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(730, 440, true)); // 第一格
            Refine1.addAction(new DelayAction(6));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(730, 440, true)); // 第一格
            
            Refine1.addAction(new DelayAction(6));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(1100, 820, false)); // 开始精炼
            Refine1.addAction(new DelayAction(6));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(1135, 475, true)); // 取出物品
            Refine1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,true));
            Refine1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,false));
            
            Refine1.addAction(player -> rightClickOnNearestEntity(player, new BlockPos(265, 11, -528), 1.0)); // 分解NPC
            Refine1.addAction(new DelayAction(10));
            Refine1.addAction(player -> GuiInventory.clickRefineItemsInInventory());
            Refine1.addAction(new DelayAction(10));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(955, 595, true)); // 开始分解
            Refine1.addAction(new DelayAction(6));
            Refine1.addAction(player -> GuiInventory.simulateMouseClick(955, 595, true)); // 开始分解
            
            Refine1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,true));
            Refine1.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_ESCAPE,false));
            
            RefineSequence.addStep(Refine1);
            
            pathSequenceManager.addSequence(RefineSequence);
        }
        
        // 设置角度（与游戏中对应） xxx.addAction(player -> setPlayerViewAngles(player, 66.5f, -46.0f));
        // 发送聊天内容（可用于发送指令） xxx.addAction(player -> sendChatCommand("/jump"));
        // 指定坐标方块右键 xxx.addAction(player -> rightClickOnBlock(player, new BlockPos(190, 8, -488)));
        // 手动添加延迟ticks（20tick = 1s） xxx.addAction(new DelayAction(12));
        // 指定坐标范围实体右键 xxx.addAction(player -> rightClickOnNearestEntity(player, new BlockPos(100, 65, 200), 3.0));
        // 自动村民交易（第1个交易2次） xxx.addAction(player -> autoVillagerTradeFull(player, 0, 2));
        // 自动箱子GUI点击（第31格） xxx.addAction(player -> autoChestClick(player, 30));
        // 模拟按下（单键） xxx.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_F3,true));
        // 模拟松开（单键） xxx.addAction(player -> GuiInventory.simulateKey(Keyboard.KEY_F3, false));
        // 模拟点击指定屏幕坐标处 xxx.addAction(player -> GuiInventory.simulateMouseClick(1222, 356, true));
        // 丢弃包含 “原石” 或 “额” 的物品，但包含 “一个原石” 会被保留 xxx.addAction(player -> GuiInventory.dropItemsByNameFilter("[原石,额],[一个原石]"));
        // 一键取出箱子里面的所有物品 xxx.addAction(player -> GuiInventory.takeAllItemsFromChest());
        // （精炼）一键点击相应物品 xxx.addAction(player -> GuiInventory.clickRefineItemsInInventory());
        
        // 新增单次命令检查方法（不会循环的）
        private static boolean isSingleUseCommand(String command) {
            String[] forbiddenCommands = {"收集", "每日", "在线", "邮件√", "邮件×"};
            for (String cmd : forbiddenCommands) {
                if (command.contains(cmd)) {
                    return true;
                }
            }
            return false;
        }
        
        // 点击后会关闭GUI的序列
        private static final List<String> CLOSE_GUI_SEQUENCES = Arrays.asList(
        	    "收集", "邮件√", "邮件×", "精炼"
        	);
        
        // 设置玩家视角角度
        private static void setPlayerViewAngles(EntityPlayerSP player, float yaw, float pitch) {
            // 添加视角范围限制
            yaw = yaw % 360.0F;
            pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
            
            // 强制更新所有旋转参数
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            player.rotationYawHead = yaw;
            player.prevRotationYaw = yaw;
            player.prevRotationPitch = pitch;
            player.renderYawOffset = yaw;
            
            LOGGER.info("Set player view angles: yaw={}, pitch={}", yaw, pitch);
        }
        
        // 发送聊天命令
        private static void sendChatCommand(String command) {
            if (mc.player != null && !mc.player.isSpectator()) {
                mc.player.sendChatMessage(command);
                LOGGER.info("Sent command: " + command);
            }
        }
        
     // 右键点击方块（增加射线追踪验证）
        private static void rightClickOnBlock(EntityPlayerSP player, BlockPos pos) {
            // 使用射线追踪获取精确点击信息
            Vec3d startVec = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
            Vec3d endVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            RayTraceResult rayTrace = player.world.rayTraceBlocks(startVec, endVec);
                // 使用射线追踪结果获取精确点击面
                EnumFacing facing = rayTrace.sideHit;
                Vec3d hitVec = rayTrace.hitVec;
                
             // 计算目标点中心坐标
                double targetX = pos.getX() + 0.5;
                double targetY = pos.getY() + 0.5;
                double targetZ = pos.getZ() + 0.5;
                
                // 计算玩家到目标的水平方向角度（yaw）
                double dx = targetX - player.posX;
                double dz = targetZ - player.posZ;
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
                
                // 计算垂直方向角度（pitch）
                double dy = targetY - (player.posY + player.getEyeHeight());
                double distance = Math.sqrt(dx*dx + dz*dz);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, distance));
                
             // 设置玩家视角后立即同步到服务端
                setPlayerViewAngles(player, yaw, pitch);
                player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, player.onGround));
                
                // 添加延迟确保服务器识别角度
                new DelayAction(3, () -> {
                    // 执行右键点击
                    mc.playerController.processRightClickBlock(
                        player,
                        mc.world,
                        pos,
                        facing,
                        hitVec,
                        EnumHand.MAIN_HAND
                    );
                    player.swingArm(EnumHand.MAIN_HAND);
                    LOGGER.info("精确点击方块: {} 面: {}", pos, facing);
                }).accept(player);
            }
        
        
        // 右键点击实体
        private static void rightClickOnNearestEntity(EntityPlayerSP player, BlockPos pos, double range) {
            Minecraft mc = Minecraft.getMinecraft();
            double px = pos.getX() + 0.5;
            double py = pos.getY() + 0.5;
            double pz = pos.getZ() + 0.5;

            // 查找附近所有实体（不包括玩家自己）
            Entity nearest = null;
            double minDistSq = Double.MAX_VALUE;
            for (Entity entity : mc.world.getEntitiesWithinAABB(
                    Entity.class,
                    new AxisAlignedBB(
                        px - range, py - range, pz - range,
                        px + range, py + range, pz + range
                    ))) {
                if (entity == player) continue;
                double distSq = entity.getDistanceSq(px, py, pz);
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearest = entity;
                }
            }
            if (nearest != null) {
                mc.playerController.interactWithEntity(player, nearest, EnumHand.MAIN_HAND);
                player.swingArm(EnumHand.MAIN_HAND);
                LOGGER.info("Right clicked entity {} at {}", nearest.getName(), pos);
            } else {
                LOGGER.warn("No entity found near: " + pos);
            }
        }
        
        // 延迟动作类
        private static class DelayAction implements Consumer<EntityPlayerSP> {
            private final int delayTicks;
            private final Runnable action;
            private int remainingTicks;
        
            public DelayAction(int delayTicks) {
                this(delayTicks, null);
            }
        
            public DelayAction(int delayTicks, Runnable action) {
                this.delayTicks = delayTicks;
                this.action = action;
                this.remainingTicks = delayTicks;
            }
        
            public int getDelayTicks() {
                return delayTicks;
            }
        
            @Override
            public void accept(EntityPlayerSP player) {
                if (remainingTicks > 0) {
                    MinecraftForge.EVENT_BUS.register(new Object() {
                        @SubscribeEvent
                        public void onTick(TickEvent.ClientTickEvent event) {
                            if (event.phase == TickEvent.Phase.START) {
                                remainingTicks--;
                                if (remainingTicks <= 0) {
                                    if (action != null) {
                                        action.run();
                                    }
                                    MinecraftForge.EVENT_BUS.unregister(this);
                                }
                            }
                        }
                    });
                } else {
                    if (action != null) {
                        action.run();
                    }
                }
            }
        }
        
        // 自动村民交易类
        /**
         * 自动执行指定村民交易，自动补全输入物品并领取交易物品，支持NBT精确匹配
         * @param tradeIndex    村民交易序号（从0开始）
         * @param tradeCount    执行多少次该交易
         */
        public static void autoVillagerTradeFull(EntityPlayerSP player, int tradeIndex, int tradeCount) {
            LOGGER.info("当前GUI: " + mc.currentScreen.getClass().getName());
            LOGGER.info("当前容器: " + player.openContainer.getClass().getName());
            if (!(mc.currentScreen instanceof GuiMerchant) || tradeCount <= 0) return;
            GuiMerchant gui = (GuiMerchant) mc.currentScreen;
            MerchantRecipeList recipes = gui.getMerchant().getRecipes(player);

            if (recipes == null || tradeIndex < 0 || tradeIndex >= recipes.size()) return;
            MerchantRecipe recipe = recipes.get(tradeIndex);
            if (recipe == null || recipe.isRecipeDisabled()) return;

            // 反射设置GuiMerchant的currentRecipeIndex（适配开发版和混淆版）
            try {
                java.lang.reflect.Field field;
                try {
                    // 开发环境名
                    field = GuiMerchant.class.getDeclaredField("currentRecipeIndex");
                } catch (NoSuchFieldException e) {
                    // 混淆名
                    field = GuiMerchant.class.getDeclaredField("field_147041_z");
                }
                field.setAccessible(true);
                field.setInt(gui, tradeIndex);
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (int t = 0; t < tradeCount; t++) {
                // 补全输入物品
                boolean inputOk = fillMerchantInputsWithNBT(gui, recipe);
                if (!inputOk) {
                    LOGGER.warn("背包中缺少交易所需物品（含NBT），无法继续交易");
                    break;
                }
                // 尝试点击输出槽以完成交易
                Slot outputSlot = gui.inventorySlots.getSlot(2);
                if (outputSlot != null && outputSlot.getHasStack()) {
                    int emptySlot = findFirstEmptyInventorySlot(gui);
                    if (emptySlot >= 0) {
                        mc.playerController.windowClick(gui.inventorySlots.windowId, 2, 0, ClickType.PICKUP, player);
                        mc.playerController.windowClick(gui.inventorySlots.windowId, emptySlot, 0, ClickType.PICKUP, player);
                    } else {
                        LOGGER.warn("背包已满，无法领取交易物品！");
                        break;
                    }
                } else {
                    // 没有输出物品时，再点击一次触发交易
                    mc.playerController.windowClick(gui.inventorySlots.windowId, 2, 0, ClickType.PICKUP, player);
                }
            }
            LOGGER.info("自动完成村民交易（含NBT精确匹配），交易序号: " + tradeIndex + "，次数: " + tradeCount);
            
            // 交易完成后强制清理两个输入槽
            clearMerchantInputSlot(gui, 0);
            clearMerchantInputSlot(gui, 1);
            
            // 额外执行一次清空操作（防止残留）
            mc.playerController.windowClick(gui.inventorySlots.windowId, 0, 0, ClickType.QUICK_MOVE, player);
            mc.playerController.windowClick(gui.inventorySlots.windowId, 1, 0, ClickType.QUICK_MOVE, player);
        }

        /**
         * 补全村民交易输入物品（支持1或2输入物品，且匹配NBT标签）
         * @return 是否成功放入所需数量的输入物品
         */
        private static boolean fillMerchantInputsWithNBT(GuiMerchant gui, MerchantRecipe recipe) {
            // 1. 先清空输入槽（slot 0, slot 1）
            clearMerchantInputSlot(gui, 0);
            clearMerchantInputSlot(gui, 1);

            // 2. 放入输入1
            boolean ok1 = moveItemToInputWithNBT(gui, recipe.getItemToBuy(), 0, recipe.getItemToBuy().getCount());
            // 3. 放入输入2（如果有）
            boolean ok2 = true;
            if (!recipe.getSecondItemToBuy().isEmpty()) {
                ok2 = moveItemToInputWithNBT(gui, recipe.getSecondItemToBuy(), 1, recipe.getSecondItemToBuy().getCount());
            }
            return ok1 && ok2;
        }

        /**
         * 从背包移动指定数量物品（含精确NBT）到村民输入槽
         * @param gui GuiMerchant
         * @param targetStack 目标物品
         * @param inputSlot 输入槽号（0或1）
         * @param neededCount 需要的数量
         * @return 是否足量成功
         */
        private static boolean moveItemToInputWithNBT(GuiMerchant gui, ItemStack targetStack, int inputSlot, int neededCount) {
            int moved = 0;
            for (int i = 0; i <= 35; i++) {
                Slot slot = gui.inventorySlots.getSlot(i);
                if (slot != null && slot.getHasStack()) {
                    ItemStack stack = slot.getStack();
                    if (itemStackNBTEquals(stack, targetStack)) {
                        int toMove = Math.min(stack.getCount(), neededCount - moved);
                        for (int j = 0; j < toMove; j++) {
                            mc.playerController.windowClick(gui.inventorySlots.windowId, i, 0, ClickType.PICKUP, mc.player);
                            mc.playerController.windowClick(gui.inventorySlots.windowId, inputSlot, 0, ClickType.PICKUP, mc.player);
                            moved++;
                            stack = slot.getStack();
                            if (stack == null || stack.isEmpty()) break;
                        }
                    }
                    if (moved >= neededCount) break;
                }
            }
            return moved >= neededCount;
        }

        /**
         * 精确比较两个ItemStack，包括NBT
         */
        private static boolean itemStackNBTEquals(ItemStack a, ItemStack b) {
            if (a == null || b == null) return false;
            NBTTagCompound nbtA = a.getTagCompound();
            NBTTagCompound nbtB = b.getTagCompound();
            if (nbtA == null && nbtB == null) return true;
            if (nbtA == null || nbtB == null) return false;
            return nbtA.equals(nbtB);
        }

        /**
         * 查找玩家背包中的第一个空槽位
         * @param gui 当前GuiMerchant
         * @return 槽位索引（GUI里的），没有空位返回-1
         */
        private static int findFirstEmptyInventorySlot(GuiMerchant gui) {
            for (int i = 0; i <= 35; i++) {
                Slot slot = gui.inventorySlots.getSlot(i);
                if (slot != null && !slot.getHasStack()) {
                    return i;
                }
            }
            return -1;
        }

        // 清空村民交易输入槽
        private static void clearMerchantInputSlot(GuiMerchant gui, int slotId) {
        	if (gui.inventorySlots == null) return; // 添加空指针检查
            Slot slot = gui.inventorySlots.getSlot(slotId);
            if (slot != null && slot.getHasStack()) {
                // 使用快速移动模式清空槽位（按住Shift点击）
                mc.playerController.windowClick(
                    gui.inventorySlots.windowId, 
                    slotId, 
                    0, 
                    ClickType.QUICK_MOVE,  // 修改为快速移动
                    mc.player
                );
                
                // 二次清理确保槽位清空（针对无法快速移动的情况）
                if (slot.getHasStack()) {
                    mc.playerController.windowClick(
                        gui.inventorySlots.windowId, 
                        slotId, 
                        1,  // 使用右键点击逐个移除
                        ClickType.PICKUP, 
                        mc.player
                    );
                }
            }
        }
        
        // 自动箱子GUI点击类
        /**
         * 自动点击箱子或大箱子GUI的指定格子
         * @param chestSlotIndex 格子编号（大箱子0-53，小箱子0-26）
         */
        public static void autoChestClick(EntityPlayerSP player, int chestSlotIndex) {
            if (mc.currentScreen instanceof GuiChest) {
                GuiChest gui = (GuiChest) mc.currentScreen;
                if (chestSlotIndex >= 0 && chestSlotIndex < gui.inventorySlots.inventorySlots.size()) {
                    mc.playerController.windowClick(gui.inventorySlots.windowId, chestSlotIndex, 0, ClickType.PICKUP, player);
                    LOGGER.info("自动点击箱子格子: " + chestSlotIndex);
                }
            }
        }
        
        /**
         * 点击当前背包内包含白名单内容但不包含黑名单内容的物品
         */
        public static void clickRefineItemsInInventory() {
            EntityPlayerSP player = mc.player;
            if (player == null) {
                return;
            }

            // 使用加载的配置
            List<String> blacklist = blacklistRefines;
            List<String> whitelist = whitelistRefines;
            
            Container container = mc.player.openContainer; // 当前容器GUI
            if (container == null) {
                return;
            }

            // 假设这是每个槽位的坐标，需要根据实际情况调整
            int[][] slotCoordinates = {
                //  9~44 每个槽位对应的 (x, y) 坐标
            		{670,710}, {745,710}, {815,710}, {890,710}, {960,710}, {1030,710}, {1100,710}, {1175,710}, {1250,710}, 
            		{670,780}, {745,780}, {815,780}, {815,780}, {960,780}, {1030,780}, {1100,780}, {1175,780}, {1250,780}, 
            		{670,855}, {745,855}, {815,855}, {815,855}, {960,855}, {1030,855}, {1100,855}, {1175,855}, {1250,855}, 
            		{670,945}, {745,945}, {815,945}, {815,945}, {960,945}, {1030,945}, {1100,945}, {1175,945}, {1250,945}
            };

            for (int i = 9; i <= 44; i++) { // 只处理 9~44 槽位
                if (i >= container.inventorySlots.size()) {
                    continue;
                }
                Slot slot = container.getSlot(i);
                if (slot == null) {
                    continue;
                }
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) {
                    continue;
                }

                String itemName = stack.getDisplayName().trim();
                boolean containsBlacklist = blacklist.stream().anyMatch(itemName::contains);
                boolean containsWhitelist = whitelist.stream().anyMatch(itemName::contains);

                if (containsWhitelist && !containsBlacklist) {
                    try {
                        int[] coords = slotCoordinates[i - 9]; // 索引从 0 开始
                        int x = coords[0];
                        int y = coords[1];
                        simulateMouseClick(x, y, true);
                        new DelayAction(3, () -> {
                        	simulateMouseClick(x + 10, y, true);
                        }).accept(player);
                        
                    } catch (Exception e) {
                        LOGGER.error("模拟鼠标点击失败，槽位: {}", i, e);
                    }
                }
            }
        }
    
     // 修改加载显示设置方法
        private static void loadDisplaySettings() {
            try {
                Path configPath = Paths.get("config/KeyCommand/keycommand_resolution.json");
                if (Files.exists(configPath)) {
                    String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                    JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
                    
                    // 添加默认值处理
                    customScreenWidth = obj.has("width") ? obj.get("width").getAsInt() : -1;
                    customScreenHeight = obj.has("height") ? obj.get("height").getAsInt() : -1;
                    customScreenScale = obj.has("scale") ? obj.get("scale").getAsInt() : -1;
                    LOGGER.info("已加载自定义分辨率: {}x{} ({}%)", customScreenWidth, customScreenHeight, customScreenScale);
                }
            } catch (Exception e) {
                LOGGER.error("加载屏幕配置失败", e);
                // 重置为默认值
                customScreenWidth = -1;
                customScreenHeight = -1;
                customScreenScale = -1;
            }
        }
        
        private static void saveDisplaySettings() {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("width", customScreenWidth);
                json.addProperty("height", customScreenHeight);
                json.addProperty("scale", customScreenScale);
                
                Path configDir = Paths.get("config/KeyCommand");
                if (!Files.exists(configDir)) Files.createDirectories(configDir);
                
                // 使用原子写入方式并添加文件同步
                Path tempFile = Files.createTempFile(configDir, "resolution", ".tmp");
                try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) { // 改为FileOutputStream
                    fos.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                    fos.getFD().sync(); // 现在可以正确调用getFD()
                }
                Files.move(tempFile, configDir.resolve("keycommand_resolution.json"), 
                    StandardCopyOption.REPLACE_EXISTING);
                
                LOGGER.info("分辨率设置已保存: {}x{}({}%)", customScreenWidth, customScreenHeight, customScreenScale);
            } catch (Exception e) {
                LOGGER.error("保存屏幕配置失败", e);
                // 失败时恢复默认值
                customScreenWidth = 1920;
                customScreenHeight = 1200;
                customScreenScale = 125;
            }
        }
        
        // 分辨率输入GUI
        public static class ResolutionInputGui extends GuiScreen {
            private GuiTextField widthField;
            private GuiTextField heightField;
            private GuiTextField scaleField;
            
            @Override
            public void initGui() {
                this.buttonList.add(new GuiButton(0, width/2 - 100, height/2 + 90, 200, 20, "保存配置"));
                
                widthField = new GuiTextField(1, this.fontRenderer, 
                    width/2 - 100, height/2 - 65, 200, 20);
                widthField.setMaxStringLength(4);
                widthField.setText(customScreenWidth > 0 ? String.valueOf(customScreenWidth) : "");
                
                heightField = new GuiTextField(2, this.fontRenderer,
                    width/2 - 100, height/2 - 15, 200, 20);
                heightField.setMaxStringLength(4);
                heightField.setText(customScreenHeight > 0 ? String.valueOf(customScreenHeight) : "");
                
                scaleField = new GuiTextField(3, this.fontRenderer,
                        width/2 - 100, height/2 + 35, 200, 20);
                scaleField.setMaxStringLength(4);
                scaleField.setText(customScreenScale > 0 ? String.valueOf(customScreenScale) : "");
            }
            
            @Override
            protected void actionPerformed(GuiButton button) {
                if (button.id == 0) { // 保存按钮
                    try {
                        customScreenWidth = Integer.parseInt(widthField.getText());
                        customScreenHeight = Integer.parseInt(heightField.getText());
                        customScreenScale = Integer.parseInt(scaleField.getText());
                        saveDisplaySettings();
                        
                        // 强制刷新显示和配置
                        mc.renderGlobal.loadRenderers();
                        mc.gameSettings.saveOptions(); // 保存游戏设置
                        mc.gameSettings.loadOptions(); // 重新加载设置
                        mc.displayGuiScreen(null);
                    } catch (NumberFormatException e) {
                        LOGGER.error("无效的分辨率输入");
                        // 输入错误时恢复已保存的值
                        loadDisplaySettings();
                    }
                }
            }
            
            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                drawDefaultBackground();
                drawCenteredString(fontRenderer, "请输入屏幕分辨率", width/2, height/2 - 100, 0x632B30); // 红酒色
                drawString(fontRenderer, "宽度:", width/2 - 100, height/2 - 80, 0xE6AF2E); // 黄澄色
                drawString(fontRenderer, "高度:", width/2 - 100, height/2 - 30, 0xE6AF2E);
                drawString(fontRenderer, "缩放%:", width/2 - 100, height/2 + 20, 0xE6AF2E);
                widthField.drawTextBox();
                heightField.drawTextBox();
                scaleField.drawTextBox();
                super.drawScreen(mouseX, mouseY, partialTicks);
            }
            
            @Override
            protected void keyTyped(char typedChar, int keyCode) throws IOException {
                super.keyTyped(typedChar, keyCode);
                widthField.textboxKeyTyped(typedChar, keyCode);
                heightField.textboxKeyTyped(typedChar, keyCode);
                scaleField.textboxKeyTyped(typedChar, keyCode);
            }
            
            @Override
            protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
                super.mouseClicked(mouseX, mouseY, mouseButton);
                widthField.mouseClicked(mouseX, mouseY, mouseButton);
                heightField.mouseClicked(mouseX, mouseY, mouseButton);
                scaleField.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
        
        // 新增精炼配置保存/加载方法
        private static void saveRefineConfig() {
            try {
                JsonObject json = new JsonObject();
                json.add("blacklist", new Gson().toJsonTree(blacklistRefines));
                json.add("whitelist", new Gson().toJsonTree(whitelistRefines));
                
                Path configDir = Paths.get("config/KeyCommand");
                if (!Files.exists(configDir)) Files.createDirectories(configDir);
                
                Files.write(configDir.resolve("refine_config.json"), 
                    json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOGGER.error("保存过滤配置失败", e);
            }
        }

        private static void loadRefineConfig() {
            try {
            	if (REFINE_CONFIG_FILE.exists()) {
            		// 修改读取方式，明确使用UTF-8编码
                    String jsonContent = new String(
                        Files.readAllBytes(REFINE_CONFIG_FILE.toPath()), 
                        StandardCharsets.UTF_8
                    );	
                    JsonObject json = new JsonParser().parse(jsonContent).getAsJsonObject();
                    
                    blacklistRefines = new Gson().fromJson(
                        json.get("blacklist"), new TypeToken<List<String>>(){}.getType());
                    whitelistRefines = new Gson().fromJson(
                        json.get("whitelist"), new TypeToken<List<String>>(){}.getType());
                }
            } catch (Exception e) {
                LOGGER.error("加载过滤配置失败", e);
            }
        }

        
        // 精炼设置GUI
        public static class ItemRefineConfigGui extends GuiScreen {
            private GuiTextField blacklistField;
            private GuiTextField whitelistField;
            private String errorMessage = "";
            
            @Override
            public void initGui() {
                this.buttonList.add(new GuiButton(0, width/2 - 100, height/2 + 60, 200, 20, "保存配置"));
                
                // 黑名单输入框带提示文本
                blacklistField = new GuiTextField(1, this.fontRenderer, 
                    width/2 - 100, height/2 - 30, 200, 20);
                blacklistField.setMaxStringLength(255);
                blacklistField.setText(String.join(", ", blacklistRefines));
                blacklistField.setValidator(s -> s.matches("[^;]*")); // 禁止输入分号
                
                // 白名单输入框带提示文本
                whitelistField = new GuiTextField(2, this.fontRenderer,
                    width/2 - 100, height/2 + 20, 200, 20);
                whitelistField.setMaxStringLength(255);
                whitelistField.setText(String.join(", ", whitelistRefines));
                whitelistField.setValidator(s -> s.matches("[^;]*"));
            }
            
            @Override
            protected void actionPerformed(GuiButton button) {
                if (button.id == 0) {
                    try {
                        // 清理输入并分割（新增中文逗号替换）
                        String blacklistInput = blacklistField.getText().replace("，", ",");  // 替换中文逗号为英文
                        String whitelistInput = whitelistField.getText().replace("，", ",");  // 替换中文逗号为英文
                        
                        blacklistRefines = Arrays.stream(blacklistInput.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        
                        whitelistRefines = Arrays.stream(whitelistInput.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        
                        saveRefineConfig();
                        errorMessage = "配置保存成功！";
                        
                        // 3秒后自动关闭提示
                        new Timer().schedule(new TimerTask() {
                            public void run() {
                                Minecraft.getMinecraft().addScheduledTask(() -> {
                                    errorMessage = "";
                                    mc.displayGuiScreen(null);
                                });
                            }
                        }, 3000);
                        
                    } catch (Exception e) {
                        errorMessage = "保存失败：" + e.getMessage();
                        LOGGER.error("保存过滤配置失败", e);
                        // 恢复原配置
                        loadRefineConfig();
                    }
                }
            }

            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                drawDefaultBackground();
                
                // 标题和说明
                drawCenteredString(fontRenderer, "精炼配置", width/2, height/2 - 70, 0x00FF00);
                drawString(fontRenderer, "黑名单（逗号分隔，匹配任意部分即不精炼）", width/2 - 100, height/2 - 45, 0xFFAAAA);
                drawString(fontRenderer, "白名单（逗号分隔，匹配任意部分则精炼）", width/2 - 100, height/2 + 5, 0xAAFFAA);
                
                // 错误提示
                if (!errorMessage.isEmpty()) {
                    drawCenteredString(fontRenderer, errorMessage, width/2, height/2 + 90, 0xFF0000);
                }
                
                blacklistField.drawTextBox();
                whitelistField.drawTextBox();
                super.drawScreen(mouseX, mouseY, partialTicks);
            }

            @Override
            protected void keyTyped(char typedChar, int keyCode) throws IOException {
                super.keyTyped(typedChar, keyCode);
                blacklistField.textboxKeyTyped(typedChar, keyCode);
                whitelistField.textboxKeyTyped(typedChar, keyCode);
            }

            @Override
            protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
                super.mouseClicked(mouseX, mouseY, mouseButton);
                blacklistField.mouseClicked(mouseX, mouseY, mouseButton);
                whitelistField.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
        
        // 新增丢弃配置保存/加载方法
        private static void saveFilterConfig() {
            try {
                JsonObject json = new JsonObject();
                json.add("blacklist", new Gson().toJsonTree(blacklistFilters));
                json.add("whitelist", new Gson().toJsonTree(whitelistFilters));
                
                Path configDir = Paths.get("config/KeyCommand");
                if (!Files.exists(configDir)) Files.createDirectories(configDir);
                
                Files.write(configDir.resolve("filter_config.json"), 
                    json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOGGER.error("保存过滤配置失败", e);
            }
        }

        private static void loadFilterConfig() {
            try {
            	if (FILTER_CONFIG_FILE.exists()) {
            		// 修改读取方式，明确使用UTF-8编码
                    String jsonContent = new String(
                        Files.readAllBytes(FILTER_CONFIG_FILE.toPath()), 
                        StandardCharsets.UTF_8
                    );	
                    JsonObject json = new JsonParser().parse(jsonContent).getAsJsonObject();
                    
                    blacklistFilters = new Gson().fromJson(
                        json.get("blacklist"), new TypeToken<List<String>>(){}.getType());
                    whitelistFilters = new Gson().fromJson(
                        json.get("whitelist"), new TypeToken<List<String>>(){}.getType());
                }
            } catch (Exception e) {
                LOGGER.error("加载过滤配置失败", e);
            }
        }

        
        // 丢弃设置GUI
        public static class ItemFilterConfigGui extends GuiScreen {
            private GuiTextField blacklistField;
            private GuiTextField whitelistField;
            private String errorMessage = "";
            
            @Override
            public void initGui() {
                this.buttonList.add(new GuiButton(0, width/2 - 100, height/2 + 60, 200, 20, "保存配置"));
                
                // 黑名单输入框带提示文本
                blacklistField = new GuiTextField(1, this.fontRenderer, 
                    width/2 - 100, height/2 - 30, 200, 20);
                blacklistField.setMaxStringLength(255);
                blacklistField.setText(String.join(", ", blacklistFilters));
                blacklistField.setValidator(s -> s.matches("[^;]*")); // 禁止输入分号
                
                // 白名单输入框带提示文本
                whitelistField = new GuiTextField(2, this.fontRenderer,
                    width/2 - 100, height/2 + 20, 200, 20);
                whitelistField.setMaxStringLength(255);
                whitelistField.setText(String.join(", ", whitelistFilters));
                whitelistField.setValidator(s -> s.matches("[^;]*"));
            }
            
            @Override
            protected void actionPerformed(GuiButton button) {
                if (button.id == 0) {
                    try {
                        // 清理输入并分割（新增中文逗号替换）
                        String blacklistInput = blacklistField.getText().replace("，", ",");  // 替换中文逗号为英文
                        String whitelistInput = whitelistField.getText().replace("，", ",");  // 替换中文逗号为英文
                        
                        blacklistFilters = Arrays.stream(blacklistInput.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        
                        whitelistFilters = Arrays.stream(whitelistInput.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                        
                        saveFilterConfig();
                        errorMessage = "配置保存成功！";
                        
                        // 3秒后自动关闭提示
                        new Timer().schedule(new TimerTask() {
                            public void run() {
                                Minecraft.getMinecraft().addScheduledTask(() -> {
                                    errorMessage = "";
                                    mc.displayGuiScreen(null);
                                });
                            }
                        }, 3000);
                        
                    } catch (Exception e) {
                        errorMessage = "保存失败：" + e.getMessage();
                        LOGGER.error("保存过滤配置失败", e);
                        // 恢复原配置
                        loadFilterConfig();
                    }
                }
            }

            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                drawDefaultBackground();
                
                // 标题和说明
                drawCenteredString(fontRenderer, "物品过滤配置", width/2, height/2 - 70, 0x00FF00);
                drawString(fontRenderer, "黑名单（逗号分隔，匹配任意部分即丢弃）", width/2 - 100, height/2 - 45, 0xFFAAAA);
                drawString(fontRenderer, "白名单（逗号分隔，匹配任意部分则保留）", width/2 - 100, height/2 + 5, 0xAAFFAA);
                
                // 错误提示
                if (!errorMessage.isEmpty()) {
                    drawCenteredString(fontRenderer, errorMessage, width/2, height/2 + 90, 0xFF0000);
                }
                
                blacklistField.drawTextBox();
                whitelistField.drawTextBox();
                super.drawScreen(mouseX, mouseY, partialTicks);
            }

            @Override
            protected void keyTyped(char typedChar, int keyCode) throws IOException {
                super.keyTyped(typedChar, keyCode);
                blacklistField.textboxKeyTyped(typedChar, keyCode);
                whitelistField.textboxKeyTyped(typedChar, keyCode);
            }

            @Override
            protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
                super.mouseClicked(mouseX, mouseY, mouseButton);
                blacklistField.mouseClicked(mouseX, mouseY, mouseButton);
                whitelistField.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
        
        // 按键模拟方法
        public static void simulateKey(int keyCode, boolean pressed) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    java.awt.Robot robot = new java.awt.Robot();
                    String keyName = Keyboard.getKeyName(keyCode);
                    
                    // 修正小键盘键名映射
                    if (keyName.startsWith("NUMPAD")) {
                        keyName = keyName.replace("NUMPAD", "NUMPAD_");
                    }
                    
                    // 使用反射获取正确的键值常量
                    java.lang.reflect.Field field = java.awt.event.KeyEvent.class.getDeclaredField("VK_" + keyName);
                    int keyEventCode = field.getInt(null);
                    
                    if (pressed) {
                        robot.keyPress(keyEventCode);
                    } else {
                        robot.keyRelease(keyEventCode);
                    }
                    LOGGER.info("成功模拟按键: {} 状态: {}", keyName, pressed ? "按下" : "松开");
                } catch (Exception e) {
                    LOGGER.error("模拟按键失败", e);
                }
            });
        }
        
        // 修改模拟鼠标点击方法
        public static void simulateMouseClick(int x, int y, boolean isLeftClick) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                	// 基准分辨率（原始设计分辨率）
                    final int BASE_WIDTH = 1920;
                    final int BASE_HEIGHT = 1200;
                	
                    // 计算分辨率缩放比例
                    double widthScale = customScreenWidth > 0 ? (double)customScreenWidth / BASE_WIDTH : 1.0;
                    double heightScale = customScreenHeight > 0 ? (double)customScreenHeight / BASE_HEIGHT : 1.0;
                    
                    // 应用分辨率缩放
                    int scaledX = (int)(x * widthScale);
                    int scaledY = (int)(y * heightScale);
                    
                    // 获取系统实际缩放比例（考虑高DPI缩放）
                    double scale = customScreenScale / 100.0;
                    int actualX = (int)(scaledX / scale);
                    int actualY = (int)(scaledY / scale);

                    java.awt.Robot robot = new java.awt.Robot();
                    robot.mouseMove(actualX, actualY);
                    
                    int buttonMask = isLeftClick ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK : java.awt.event.InputEvent.BUTTON3_DOWN_MASK;
                    robot.mousePress(buttonMask);
                    robot.mouseRelease(buttonMask);
                    LOGGER.info("自动缩放点击 | 逻辑坐标: ({}, {}) 实际坐标: ({}, {}) 系统缩放: {}", x, y, actualX, actualY, scale);
                } catch (Exception e) {
                    LOGGER.error("模拟鼠标点击失败", e);
                }
            });
        }
        
        /**
         * 根据名称过滤丢弃物品（黑名单+白名单机制）
         * @param filterStr 格式如 "[布料,线团],[高级布料,精致线团]"
         *                  第一部分是黑名单（名称包含即丢弃）
         *                  第二部分是白名单（名称包含则保留）
         */
        public static void dropItemsByNameFilter() {
            EntityPlayerSP player = mc.player;
            if (player == null) return;

            // 使用加载的配置
            List<String> blacklist = blacklistFilters;
            List<String> whitelist = whitelistFilters;

            List<Integer> slotsToDrop = new ArrayList<>();
            Container container = mc.player.openContainer; // 当前容器GUI
            
            for (int i = 0; i < container.inventorySlots.size(); i++) {
                Slot slot = container.getSlot(i);
                
                // 移除背包槽位判断，支持任意容器
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;
                
                String itemName = stack.getDisplayName().trim();
                boolean shouldDrop = blacklist.stream().anyMatch(itemName::contains);
                boolean shouldKeep = whitelist.stream().anyMatch(itemName::contains);
                
                if (shouldDrop && !shouldKeep) {
                    slotsToDrop.add(i); // 直接使用容器槽位索引
                }
            }

            // 优化后的并行丢弃逻辑
            Minecraft.getMinecraft().addScheduledTask(() -> {
                // 将槽位分成3组并行处理
                int batchSize = 3;
                for (int batch = 0; batch < slotsToDrop.size(); batch += batchSize) {
                    final int currentBatch = batch;
                    new DelayAction(batch * 3, () -> {
                        // 并行处理当前批次
                        for (int i = 0; i < batchSize; i++) {
                            int globalIndex = currentBatch + i;
                            if (globalIndex >= slotsToDrop.size()) return;
                            
                            int containerSlot = slotsToDrop.get(globalIndex);
                            // 并行丢弃流程
                            mc.playerController.windowClick(
                                player.openContainer.windowId, 
                                containerSlot, 0, ClickType.PICKUP, player
                            );
                            mc.playerController.windowClick(
                                player.openContainer.windowId, 
                                -999, 0, ClickType.PICKUP, player
                            );
                        }
                    }).accept(player);
                }
            });
        }

        /**
         * 安全版一键取出箱子里面的所有物品（使用快速移动+大间隔）
         */
        public static void takeAllItemsFromChest() {
        	// 增加初始延迟确保GUI加载完成
        	new DelayAction(5, () -> {
        		if (mc.currentScreen instanceof GuiChest && mc.player.openContainer != null) {
                    GuiChest gui = (GuiChest) mc.currentScreen;
                    Container container = mc.player.openContainer;
                
                    // 修正槽位范围：0到containerInventorySlots.size()-1
                    int chestSlots = container.inventorySlots.size() - 36;  // 总槽位减去玩家背包
                    if (chestSlots <= 0 || chestSlots > 54) { // 添加合法范围检查
                        LOGGER.warn("异常容器槽位数量: {}", chestSlots);
                        return;
                    }
                    
                    List<Integer> slotsToProcess = new ArrayList<>();
                    // 使用容器实际槽位数量作为上限
                    for (int i = 0; i < Math.min(chestSlots, container.inventorySlots.size()); i++) {
                        Slot slot = container.getSlot(i);
                        if (slot != null && slot.getHasStack()) {
                            slotsToProcess.add(i);
                        }
                    }

                    if (!slotsToProcess.isEmpty()) {
                        processChestSlotsSafely(gui, slotsToProcess);
                    }
                } else {
                    LOGGER.warn("箱子GUI未正常打开或容器为空");
                    return;
                }
        	}).accept(mc.player);
        }

        private static void processChestSlotsSafely(GuiChest gui, List<Integer> slots) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
            	// 将槽位分成四列并行处理
                for (int batchIndex = 0; batchIndex < slots.size(); batchIndex +=3) {
                    final int currentBatch = batchIndex;
                    // 每列间隔3tick
                    new DelayAction(batchIndex, () -> {
                        // 并行处理当前列的4个槽位
                        for (int i = 0; i < 4; i++) {
                            int globalIndex = currentBatch + i;
                            if (globalIndex >= slots.size()) return;
                            
                            int slotIndex = slots.get(globalIndex);
                            if (slotIndex >= 0 && slotIndex < gui.inventorySlots.inventorySlots.size() 
                            	&& gui.inventorySlots.getSlot(slotIndex) != null) {
                                mc.playerController.windowClick(
                                    gui.inventorySlots.windowId, 
                                    slotIndex,
                                    0,
                                    ClickType.QUICK_MOVE,
                                    mc.player
                                );
                                LOGGER.info("并行转移槽位 {} (批次 {} 进度 {}/4)", 
                                    slotIndex, currentBatch/4 +1, i+1);
                            }
                        }
                    }).accept(mc.player);
                }
            });
        }
        

        /**
         * 自动进食类
         * 需要搭配GlobalEventListener使用
         * 安全版一键取出箱子里面的所有物品（使用快速移动+大间隔）
         */
        private static void checkAutoEat(EntityPlayerSP player) {   
            if (!autoEatEnabled || isEating || player.getFoodStats().getFoodLevel() > 12) return;

            // 优先使用快捷栏中的食物
            for (int i = 0; i < 9; i++) { //栏有食
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (isFoodItem(stack) && !isEating) {
                	int originalHotbar = player.inventory.currentItem;
                    GuiInventory.originalHotbarSlot = originalHotbar;
                    
                    handleEatingProcess(player, i);
                    return;
                }
            }

            // 新增交换逻辑：当快捷栏满时
            if (isHotbarFull(player) && !hasFoodInHotbar(player)) { //栏满有食
                Optional<Integer> foodSlot = findFoodInInventory(player);
                if (foodSlot.isPresent()) {
                    swapItemWithHotbar(player, foodSlot.get());
                }
            } else if (!isHotbarFull(player) && !hasFoodInHotbar(player)) { //栏未满无食
                moveFoodToHotbar();
            }
        }
        
        // 新增快捷栏食物检查方法
        private static boolean hasFoodInHotbar(EntityPlayerSP player) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (isFoodItem(stack)) {
                    return true;
                }
            }
            return false;
        }

        private static Optional<Integer> findFoodInInventory(EntityPlayerSP player) {
            for (int i = 9; i < 36; i++) { // 遍历背包槽位
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (isFoodItem(stack) && !isEating) {
                    return Optional.of(i);
                }
            }
            return Optional.empty();
        }
        
        // 新增快捷栏状态检查方法
        private static boolean isHotbarFull(EntityPlayerSP player) {
            for (int i = 0; i < 9; i++) {
                if (player.inventory.getStackInSlot(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        // 新增物品交换方法
        private static void swapItemWithHotbar(EntityPlayerSP player, int sourceSlot) {
            // 添加槽位有效性检查
        	if (mc.currentScreen instanceof GuiChest) {
        		GuiChest gui = (GuiChest) mc.currentScreen;
        		 if (sourceSlot < 0 || sourceSlot >= gui.inventorySlots.inventorySlots.size()) {
        			 LOGGER.error("无效的物品槽位: {}", sourceSlot);
        			 return;
        		 }
            } else if (sourceSlot < 0 || sourceSlot >= player.inventory.mainInventory.size()) {
                LOGGER.error("无效的物品槽位: {}", sourceSlot);
                return;
            }
            
            int originalHotbar = player.inventory.currentItem;
            GuiInventory.originalHotbarSlot = originalHotbar;
            // 槽位选择逻辑：优先+1，超过8则-1
            int currentHotbar = originalHotbar < 8 ? originalHotbar + 1 : originalHotbar - 1;
            GuiInventory.swappedItem = player.inventory.getStackInSlot(currentHotbar).copy();

            // 执行交换操作
            if (mc.currentScreen instanceof GuiChest) { // 小箱子GUI
            	mc.playerController.windowClick(
            			player.openContainer.windowId,
            			sourceSlot + 27,  // 箱子GUI背包中的食物槽位
            			currentHotbar,  // 当前手持的快捷栏槽位旁的槽位
            			ClickType.SWAP,
            			player
            	);
            } else { // 默认
            	mc.playerController.windowClick(
                        player.openContainer.windowId,
                        sourceSlot,  // 背包中的食物槽位
                        currentHotbar,  // 当前手持的快捷栏槽位旁的槽位
                        ClickType.SWAP,
                        player	
            	);
            }
        }
        
     // 新增物品交换方法
        private static void swapItemWithHotbar2(EntityPlayerSP player, int sourceSlot) {
            // 添加槽位有效性检查
        	if (mc.currentScreen instanceof GuiChest) {
        		GuiChest gui = (GuiChest) mc.currentScreen;
        		 if (sourceSlot < 0 || sourceSlot >= gui.inventorySlots.inventorySlots.size()) {
        			 LOGGER.error("无效的物品槽位: {}", sourceSlot);
        			 return;
        		 }
            } else if (sourceSlot < 0 || sourceSlot >= player.inventory.mainInventory.size()) {
                LOGGER.error("无效的物品槽位: {}", sourceSlot);
                return;
            }
            
            int currentHotbar = player.inventory.currentItem;

            // 执行交换操作
            if (mc.currentScreen instanceof GuiChest) { // 小箱子GUI
            	mc.playerController.windowClick(
            			player.openContainer.windowId,
            			sourceSlot + 27,  // 箱子GUI背包中的食物槽位
            			currentHotbar,  // 当前手持的快捷栏槽
            			ClickType.SWAP,
            			player
            	);
            } else { // 默认
            	mc.playerController.windowClick(
                        player.openContainer.windowId,
                        sourceSlot,  // 背包中的食物槽位
                        currentHotbar,  // 当前手持的快捷栏槽
                        ClickType.SWAP,
                        player	
            	);
            }
        }

        // 修改进食处理方法
        private static void handleEatingProcess(EntityPlayerSP player, int slot) {
            GuiInventory.isEating = true;
            GuiInventory.sendChatCommand(".pause");
            player.inventory.currentItem = slot;
            player.connection.sendPacket(new CPacketHeldItemChange(slot));
            
            
            // 使用新的持续按键动作
            new DelayAction(3, () -> {
                // 按下右键并保持
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                
                if (Minecraft.getMinecraft().playerController != null) { // 防止ESC界面、网络问题导致空指针
                	mc.playerController.processRightClick(player, player.world, EnumHand.MAIN_HAND);
                }
            
                new DelayAction(32, () -> {
                	KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                
                	// 进食完成后恢复原有物品并恢复原本手持
                	new DelayAction(3, () -> {
                		if (GuiInventory.originalHotbarSlot != -1) {
                    	
                        	swapItemWithHotbar2(player, findInventorySlotByItem(GuiInventory.swappedItem));
                        	player.inventory.currentItem = originalHotbarSlot;
                            player.connection.sendPacket(new CPacketHeldItemChange(originalHotbarSlot));
                        	GuiInventory.originalHotbarSlot = -1;
                        	GuiInventory.swappedItem = ItemStack.EMPTY;
                        	
                        	GuiInventory.sendChatCommand(".resume");
                        	GuiInventory.isEating = false;
                		}
                	}).accept(player);
            	}).accept(player);
            }).accept(player);
        }


        // 新增物品查找方法
        private static int findInventorySlotByItem(ItemStack target) {
            for (int i = 9; i < 36; i++) {
                if (ItemStack.areItemStacksEqual(mc.player.inventory.getStackInSlot(i), target)) {
                    return i;
                }
            }
            return -1;
        }

        // 在GuiInventory类中添加食物转移方法
        public static void moveFoodToHotbar() {
            EntityPlayerSP player = mc.player;
            // 遍历背包寻找食物
            for (int i = 9; i < 36; i++) { // 9-35是背包槽位
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (isFoodItem(stack)) {
                    // 找到第一个空快捷栏槽位
                    for (int hotbar = 0; hotbar < 9; hotbar++) {
                        if (player.inventory.getStackInSlot(hotbar).isEmpty()) {
                            // 移动物品到快捷栏
                            mc.playerController.windowClick(
                                player.openContainer.windowId,
                                i, hotbar, 
                                ClickType.SWAP, 
                                player
                            );
                            LOGGER.info("移动食物到快捷栏第{}格", hotbar + 1);
                            return;
                        }
                    }
                }
            }
        }
        
        // 判断是否为食物
        private static boolean isFoodItem(ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ItemFood;
        }

        // PathStep 类
        private static class PathStep {
            private final double[] gotoPoint;
            private final List<Consumer<EntityPlayerSP>> actions = new ArrayList<>();

            public PathStep(double[] gotoPoint) {
                this.gotoPoint = gotoPoint;
            }

            public void addAction(Consumer<EntityPlayerSP> action) {
                actions.add(action);
            }

            public double[] getGotoPoint() {
                return gotoPoint;
            }

            public List<Consumer<EntityPlayerSP>> getActions() {
                return actions;
            }
        }
        
        // 路径序列类（支持多步骤）
        private static class PathSequence {
            private final String name;
            private final List<PathStep> steps = new ArrayList<>();
            
            public PathSequence(String name) {
                this.name = name;
            }
            
            public void addStep(PathStep step) {
                steps.add(step);
            }
            
            public String getName() {
                return name;
            }
            
            public List<PathStep> getSteps() {
                return steps;
            }
        }
        
        // 路径序列管理器
        private static class PathSequenceManager {
            private final Map<String, PathSequence> sequences = new HashMap<>();
            
            public void addSequence(PathSequence sequence) {
                sequences.put(sequence.getName(), sequence);
            }
            
            public PathSequence getSequence(String name) {
                return sequences.get(name);
            }
            
            public boolean hasSequence(String name) {
                return sequences.containsKey(name);
            }
        }
        
        private static void saveLoopConfig(String sequenceName, int loopCount) {
            try {
                String json = "{"
                    + "\"autoLoop\":true,"
                    + "\"loopSequence\":\"" + sequenceName.replace("\"", "\\\"") + "\","
                    + "\"loopCount\":" + loopCount
                    + "}";
                Path configDir = Paths.get("config/KeyCommand");
                if (!Files.exists(configDir)) Files.createDirectories(configDir);
                Files.write(Paths.get("config/KeyCommand/keycommandmod_autorun.json"), json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOGGER.error("保存循环配置失败", e);
            }
        }
        
        // 运行路径序列
        public static void runPathSequence(String sequenceName) {
            if (!pathSequenceManager.hasSequence(sequenceName)) {
                LOGGER.error("未知路径序列: " + sequenceName);
                return;
            }
            
            PathSequence sequence = pathSequenceManager.getSequence(sequenceName);
            if (sequence == null || sequence.getSteps().isEmpty()) {
                LOGGER.error("无效路径序列: " + sequenceName);
                return;
            }
            
            loopCounter = 0;
            isLooping = true;
            
            // 如果循环次数不为0，则开始执行
            if (loopCount != 0) {
                startNextLoop(sequenceName);
            }
            
            // 如果无限循环，则保存自动运行配置
            if (loopCount < 0) {
            	saveLoopConfig(sequenceName, loopCount);
            }
        }
        
        // 开始下一次循环
        private static void startNextLoop(String sequenceName) {
            PathSequence sequence = pathSequenceManager.getSequence(sequenceName);
            
            // 获取路径序列的第一个点
            double[] firstTarget = sequence.getSteps().get(0).getGotoPoint();

            // 检查坐标有效性
            if (!Double.isNaN(firstTarget[0]) && !Double.isNaN(firstTarget[1]) && !Double.isNaN(firstTarget[2])) {
                // 发送第一个.goto命令
                sendChatCommand(String.format(".goto %.0f %.0f %.0f", firstTarget[0], firstTarget[1], firstTarget[2]));
            } else {
                LOGGER.warn("无效的目标坐标，跳过.goto命令");
            }
            
            // 更新计数器
            loopCounter++;
            
            // 设置状态信息
            String loopInfo = "循环 " + loopCounter;
            if (loopCount > 0) {
                loopInfo += "/" + loopCount;
            }
            PathSequenceEventListener .instance.setStatus(sequenceName + " - " + loopInfo);
            
            // 注册事件监听器
            PathSequenceEventListener .instance.startTracking(sequence, loopCount + 1 - loopCounter);
            
            // 注册全局事件监听器
            MinecraftForge.EVENT_BUS.register(PathSequenceEventListener .instance);
            LOGGER.info("开始运行序列: " + sequenceName);
        }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.85F); // 半透明白色背景
        
        // 整体尺寸250x200像素
        int totalWidth = 250;
        int height = 200;
        int x = (this.width - totalWidth) / 2; // 居中
        int y = (this.height - height) / 2; // 居中

        // 绘制半透明白色背景
        drawRect(x, y, x + totalWidth, y + height, 0x7FFFFFFF); // 半透明白色
        
        // 绘制标题（居中显示）
        String title = "快捷菜单 - " + currentCategory;
        drawCenteredString(fontRenderer, title, x + 125, y + 5, 0x555555); // 灰色标题文字

        // 绘制左侧分类区背景 (50x200)
        drawRect(x, y, x + 50, y + height, 0x80DDDDDD); // 浅灰色背景
        
        // 绘制分类按钮（左侧垂直排列） - 调整位置以适应方框
        for (int i = 0; i < categories.size(); i++) {
            String category = categories.get(i);
            // 调整按钮位置：Y位置从15改为25增加顶部空间，间距从45改为40减小间距
            int buttonY = y + 25 + i * 40; // 增加顶部间距，减少按钮间距
            
            // 圆形按钮
            int radius = 18; // 稍微减小半径以适应方框
            int buttonX = x + 25; // 圆心位置
            
            // 高亮当前分类
            float alpha = category.equals(currentCategory) ? 0.8F : 0.5F;
            int color = category.equals(currentCategory) ? 0xFF00DD00 : 0xFFAAAAAA;
            
            // 绘制圆形背景（带透明度）
            drawCircle(buttonX, buttonY, radius, (color & 0xFFFFFF) | ((int)(alpha * 255) << 24));
            
            // 绘制文字（固定在圆形中央）
            int textWidth = fontRenderer.getStringWidth(category);
            fontRenderer.drawStringWithShadow(category, 
                buttonX - textWidth/2, 
                buttonY - 3, 
                0xFFFFFF); // 白色文字
        }

        // 绘制当前分类的物品（右侧区域）
        List<String> items = categoryItems.get(currentCategory);
        List<String> itemNames = categoryItemNames.get(currentCategory);
        
        // 物品区位置
        int itemAreaX = x + 55; // 增加左边距
        int itemAreaY = y + 20;
        
        // 每行显示5个，最多4行
        for (int i = 0; i < 20; i++) { // 最多显示20个（5列×4行）
            int index = currentPage * 20 + i;
            if (index >= items.size()) break;
            
            int col = i % 5; // 列索引 (0-4)
            int row = i / 5; // 行索引 (0-3)
            int itemX = itemAreaX + col * 36; // 简化间距
            int itemY = itemAreaY + row * 40; // 每行40像素高
            
            // 绘制物品名称和图标
            fontRenderer.drawStringWithShadow(itemNames.get(index), itemX, itemY, 0x333333);
            fontRenderer.drawStringWithShadow("\u272A", itemX + 8, itemY + 12, 0xFFDD00); // 金色星星图标
        }

        // 绘制页码信息（上移到物品区中部）
        int totalPages = (items.size() + 19) / 20;
        drawCenteredString(fontRenderer, "第" + (currentPage + 1) + "页/共" + totalPages + "页", 
                        x + 175, y + 165, 0x666666); // 灰色页码文字

        // 绘制上一页按钮（下移）
        drawRect(x + 190, y + 188, x + 220, y + 200, 0xFFDDDDDD);
        drawCenteredString(fontRenderer, "上一页", x + 205, y + 190, 0x333333);

        // 绘制下一页按钮（下移）
        drawRect(x + 220, y + 188, x + 250, y + 200, 0xFFDDDDDD);
        drawCenteredString(fontRenderer, "下一页", x + 235, y + 190, 0x333333);

        // 如果是xxx分类，显示状态信息
        if (currentCategory.equals("自动操作")) {
            
            // 新增自动吃食物状态显示（在页码上方添加）
            String autoEatStatus = "自动进食 " + (autoEatEnabled ? "✔" : "✘");
            fontRenderer.drawStringWithShadow(autoEatStatus, x + 55, y + 170, 0xFFDEAD); // 那瓦霍白色
            
            // 显示当前循环设置
            String loopSetting = "循环设置: ";
            if (loopCount < 0) {
                loopSetting += "无限循环";
            } else if (loopCount == 0) {
                loopSetting += "不执行";
            } else {
                loopSetting += loopCount + "次";
            }
            fontRenderer.drawStringWithShadow(loopSetting, x + 55, y + 180, 0x55FFFF); // 青色
            
            String statusText = "状态: ";
            // 设置路径情况显示
            if (PathSequenceEventListener .instance.isTracking()) {
                statusText += PathSequenceEventListener .instance.getStatus();
            } else if (!isLooping) {
                statusText += "就绪";
            } else if (loopCounter > 0 && loopCount > 0 && loopCounter >= loopCount) {
                statusText += "已完成 (" + loopCounter + " 次)";
            } else if (!PathSequenceEventListener .instance.isTracking()) {
                statusText += "空闲";
            }
            fontRenderer.drawStringWithShadow(statusText, x + 55, y + 190, 0xFFFF55); // 黄色
            
        } else if (currentCategory.equals("设置")) {
            
            // 新增自动吃食物状态显示
            String autoEatStatus = "自动进食 " + (autoEatEnabled ? "✔" : "✘");
            fontRenderer.drawStringWithShadow(autoEatStatus, x + 55, y + 170, 0xFFDEAD); // 那瓦霍白色
            
            // 新增自动技能状态显示
            String autoSkillStatus = "自动技能 " + (autoSkillEnabled ? "✔" : "✘");
            fontRenderer.drawStringWithShadow(autoSkillStatus, x + 55, y + 180, 0xDFE0E2); // 铂金色
            
            // 显示当前分辨率
            String resolutionStatus = "当前分辨率: " + (customScreenWidth > 0 ? customScreenWidth + "x" 
                                                    + customScreenHeight + " (" + customScreenScale + "%)" : "未设置");
            fontRenderer.drawStringWithShadow(resolutionStatus, x + 135, y + 175, 0xb22222); // 砖红色
        
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
        
        // 辅助方法：绘制圆形
        private void drawCircle(int x, int y, int radius, int color) {
            for (int i = -radius; i <= radius; i++) {
                for (int j = -radius; j <= radius; j++) {
                    if (i * i + j * j <= radius * radius) {
                        drawRect(x + i, y + j, x + i + 1, y + j + 1, color);
                    }
                }
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            
            // 整体尺寸250x200像素
            int totalWidth = 250;
            int height = 200;
            int x = (this.width - totalWidth) / 2;
            int y = (this.height - height) / 2;

            // 处理分类按钮点击（左侧区域） - 调整位置以适应方框
            for (int i = 0; i < categories.size(); i++) {
                String category = categories.get(i);
                // 位置调整与drawScreen方法保持一致
                int buttonY = y + 25 + i * 40; 
                int buttonX = x + 25;
                int radius = 18; // 与绘制时相同的半径
                
                // 计算鼠标到圆心的距离
                int dx = mouseX - buttonX;
                int dy = mouseY - buttonY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                
                if (distance <= radius) {
                    // 保存当前分类的状态
                    CATEGORY_PAGE_MAP.put(currentCategory, currentPage);
                    sLastPage = currentPage;
                    sLastCategory = currentCategory;
                    
                    // 切换到新分类
                    currentCategory = category;
                    // 恢复新分类的页码
                    if (CATEGORY_PAGE_MAP.containsKey(currentCategory)) {
                        currentPage = CATEGORY_PAGE_MAP.get(currentCategory);
                    } else {
                        currentPage = 0;
                    }
                    return;
                }
            }

            // 处理物品点击（右侧物品区）
            List<String> items = categoryItems.get(currentCategory);
            
            // 物品区位置
            int itemAreaX = x + 55;
            int itemAreaY = y + 20;
            
            // 每行5个，最多4行
            for (int i = 0; i < 20; i++) {
                int index = currentPage * 20 + i;
                if (index >= items.size()) break;
                
                int col = i % 5;
                int row = i / 5;
                int itemX = itemAreaX + col * 36;
                int itemY = itemAreaY + row * 40;
                
                // 检测物品点击范围（基于文字的位置）
                if (mouseX >= itemX && mouseX <= itemX + 30 && 
                        mouseY >= itemY && mouseY <= itemY + 20) {
                        String command = items.get(index);
                        
                        if (command.startsWith("path:")) {
                        	String sequenceName = command.substring(5);
                        	
                            // 检查路径是否需要关闭GUI
                            if (CLOSE_GUI_SEQUENCES.contains(sequenceName)) {
                                mc.displayGuiScreen(null);
                            }
                        	
                            // 检查路径是否单次运行
                        	if (isSingleUseCommand(sequenceName) && loopCount < 0) {
                        		loopCount = 1;
                        		runPathSequence(sequenceName);
                        	} else {
                        		runPathSequence(sequenceName);
                        	}

                            return;
                        } else if (currentCategory.equals("自动操作")) {
                            if (command.equals("stop")) {
                                // 停止运行
                            	sendChatCommand(".stop");
                            	if (mc.gameSettings.keyBindUseItem.isKeyDown()) {
                            		KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                            	}
                            	PathSequenceEventListener .instance.stopTracking();
                                isLooping = false;
                                return;
                            } else if (command.equals("setloop")) {
                                // 打开循环次数设置GUI
                                mc.displayGuiScreen(new LoopCountInputGui(this));
                                return;
                            }
                        } else if (currentCategory.equals("设置")) {
                        	if (command.equals("togglecoords")) {
                            	KeyCommandMod.showCoordinates = !KeyCommandMod.showCoordinates;  // 修改静态变量
                                return;
                        	} else if (command.equals("autoeat")) {
                        		autoEatEnabled = !autoEatEnabled;
                        	} else if (command.equals("setresolution")) {
                        		// 打开分辨率设置界面
                        		mc.displayGuiScreen(new ResolutionInputGui());
                        		return;
                        	} else if (command.equals("filterconfig")) {
                        		mc.displayGuiScreen(new ItemFilterConfigGui());
                        		return;
                        	} else if (command.equals("autoskill")) {
                        		mc.displayGuiScreen(new GuiAutoSkillConfig());
                        		return;
                        	}else if (command.equals("refineconfig")) {
                        		mc.displayGuiScreen(new ItemRefineConfigGui());
                        		return;
                        	}
                        } else {
                            sendChatMessage(command);
                            mc.displayGuiScreen(null);
                            return;
                        }
                    }
                }

            // 处理上一页按钮
            if (mouseX >= x + 190 && mouseY >= y + 180 && 
                mouseX <= x + 220 && mouseY <= y + 200) {
                if (currentPage > 0) {
                    currentPage--;
                    CATEGORY_PAGE_MAP.put(currentCategory, currentPage);
                    sLastPage = currentPage;
                    sLastCategory = currentCategory;
                }
            }

            // 处理下一页按钮
            if (mouseX >= x + 220 && mouseY >= y + 180 && 
                mouseX <= x + 250 && mouseY <= y + 200) {
                int totalPages = (categoryItems.get(currentCategory).size() + 19) / 20;
                if (currentPage + 1 < totalPages) {
                    currentPage++;
                    CATEGORY_PAGE_MAP.put(currentCategory, currentPage);
                    sLastPage = currentPage;
                    sLastCategory = currentCategory;
                }
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        @Override
        public void onGuiClosed() {
            super.onGuiClosed();
            // 保存当前状态
            CATEGORY_PAGE_MAP.put(currentCategory, currentPage);
            sLastPage = currentPage;
            sLastCategory = currentCategory;

        }

        // 静态的事件监听器类（完全重写以支持多操作和循环）
        public static class PathSequenceEventListener  {
            public static final PathSequenceEventListener  instance = new PathSequenceEventListener ();
            private PathSequence currentSequence;
            private int currentStepIndex = 0;
            private int actionIndex = 0;
            private boolean tracking = false;
            private int tickDelay = 0;
            private boolean atTarget = false;
            private int remainingLoops = 0;
            private String status = "";
            private PathSequenceEventListener () {}
            
            public boolean isTracking() {
                return tracking;
            }

            public void setStatus(String s) {
                status = s;
            }
            
            public String getStatus() {
                return status;
            }

            public void startTracking(PathSequence sequence, int remainingLoops) {     
                this.currentSequence = sequence;
                this.currentStepIndex = 0;
                this.actionIndex = 0;
                this.tracking = true;
                this.atTarget = false;
                this.tickDelay = 0;
                this.remainingLoops = remainingLoops;
                MinecraftForge.EVENT_BUS.register(this);
                LOGGER.info("开始跟踪路径: " + sequence.getName());
            }

            public void stopTracking() {
                if (tracking) {
                    this.tracking = false;
                    this.currentSequence = null;
                    MinecraftForge.EVENT_BUS.unregister(this);
                    status = "已停止";
                    LOGGER.info("路径跟踪已停止");
                    clearLoopConfig();
                }
            }

            private void clearLoopConfig() {
                try {
                    Path configPath = Paths.get("config/KeyCommand/keycommandmod_autorun.json");
                    if (Files.exists(configPath)) Files.delete(configPath);
                } catch (Exception e) {
                    LOGGER.error("清除循环配置失败", e);
                }
            }
            
            @SubscribeEvent
            public void onPlayerTick(TickEvent.PlayerTickEvent event) {
                if (!tracking || event.phase != TickEvent.Phase.START || event.side != Side.CLIENT) return;
                if (event.player == null || !event.player.equals(Minecraft.getMinecraft().player)) return;
                
                EntityPlayerSP player = (EntityPlayerSP) event.player;
                
                // 如果有延迟，等待延迟结束
                if (tickDelay > 0) {
                    tickDelay--;
                    return;
                }
                
                List<PathStep> steps = currentSequence.getSteps();
                
                // 检查是否完成序列
                if (currentStepIndex >= steps.size()) {
                    sendChatCommand(".stop");

                    if (remainingLoops != 0 || GuiInventory.loopCount < 0) {
                        if (remainingLoops > 0) {
                            remainingLoops--;
                        }
                        if (remainingLoops != 0 || GuiInventory.loopCount < 0) {
                            status = "等待循环...";
                            // 关键：重新开始下一轮循环
                            tracking = false;
                            MinecraftForge.EVENT_BUS.unregister(this);

                            // 重新启动下一轮循环
                            // 用调度任务方式避免递归调用
                            Minecraft.getMinecraft().addScheduledTask(() -> {
                                if (currentSequence != null) {
                                    GuiInventory.startNextLoop(currentSequence.getName());
                                }
                            });
                            return;
                        } else {
                            // 完成所有循环
                            GuiInventory.isLooping = false;
                            status = "已完成 (" + GuiInventory.loopCounter + " 次)";
                            stopTracking();
                        }
                    } else {
                        stopTracking();
                    }
                    return;
                }
                
                PathStep currentStep = steps.get(currentStepIndex);
                double[] target = currentStep.getGotoPoint();
                
                if (!atTarget) {
                    // 检查玩家是否到达目标点
                    double playerX = player.posX;
                    double playerY = player.posY;
                    double playerZ = player.posZ;
                    
                    double tx = Double.isNaN(target[0]) ? player.posX : target[0];
                    double ty = Double.isNaN(target[1]) ? player.posY : target[1];
                    double tz = Double.isNaN(target[2]) ? player.posZ : target[2];
                    
                    double distanceSq = 
                        Math.pow(playerX - tx, 2) +
                        Math.pow(playerY - ty, 2) +
                        Math.pow(playerZ - tz, 2);
                    
                    if (distanceSq < 4.0) { // 2格距离平方
                        LOGGER.info("到达目标 {} for {}", currentStepIndex, currentSequence.getName());
                        atTarget = true;
                        actionIndex = 0;
                    }
                } else {
                    // 已到达目标点，执行动作
                    List<Consumer<EntityPlayerSP>> actions = currentStep.getActions();
                    
                    // 检查是否完成当前步骤的所有动作
                    if (actionIndex >= actions.size()) {
                        // 移动到下一步
                        currentStepIndex++;
                        actionIndex = 0;
                        atTarget = false;
                        
                        // 如果还有下一步，发送新的.goto命令
                        if (currentStepIndex < steps.size()) {
                            double[] nextTarget = steps.get(currentStepIndex).getGotoPoint();
                            sendChatCommand(".stop");
                            sendChatCommand(String.format(".goto %.0f %.0f %.0f", 
                                nextTarget[0], nextTarget[1], nextTarget[2]));
                        } else {
                            // 序列完成，发送取消指令
                            sendChatCommand(".stop");
                        }
                        return;
                    }
                    
                    // 获取当前动作
                    Consumer<EntityPlayerSP> action = actions.get(actionIndex);
                    
                    // 处理延迟动作
                    if (action instanceof GuiInventory.DelayAction) {
                    	GuiInventory.DelayAction delayAction = (GuiInventory.DelayAction) action;
                    	tickDelay = delayAction.getDelayTicks();
                    	LOGGER.info("延迟 {} tick", tickDelay);
                    	// 移动至下一个动作
                    	actionIndex++;
                    	return; // 等待延迟期间，直接返回
                    }
                    
                    // 执行动作
                    try {
                        action.accept(player);
                        LOGGER.info("执行动作 {} for step {}", actionIndex, currentStepIndex);
                    } catch (Exception e) {
                        LOGGER.error("执行动作失败", e);
                    }
                    
                    // 移动到下一个动作（如果有），添加少量延迟确保服务器处理
                    actionIndex++;
                    tickDelay = 5; // 等待5 ticks (0.25秒) 让服务器处理动作
                }
            }
            
            // 发送临时聊天命令（不依赖GUI的sendChatMessage）
            private static void sendChatCommand(String command) {
                EntityPlayerSP player = Minecraft.getMinecraft().player;
                if (player != null && !player.isSpectator()) {
                    player.sendChatMessage(command);
                    LOGGER.info("发送命令: " + command);
                }
            }
            
            @SubscribeEvent
            public void onClientTick(ClientTickEvent event) { // 序列（PathStep）内客户端时刻
                // 减少 tickDelay 并检查是否需要延迟
                if (tickDelay > 0) {
                    tickDelay--;
                    return;
                }
                
                // 空值检查
                if (mc == null || mc.player == null || mc.playerController == null || mc.world == null) {
                    LOGGER.warn("关键组件未初始化，跳过本tick处理");
                    return;
                }
            }
        }
        
        // 自动技能配置GUI
        public static class GuiAutoSkillConfig extends GuiScreen {
            private GuiTextField[] cooldownFields = new GuiTextField[4];
            private GuiButton enableButton;
            private GuiButton saveButton;
            private List<GuiTextField> textFields = new ArrayList<>();
            private String message = "";
            private boolean hasError = false;
            
            // 四个按钮用于控制每个技能是否启用
            private GuiButton[] skillEnableButtons = new GuiButton[4];

            @Override
            public void initGui() {
                int centerX = width / 2;
                int y = 50;

                // 技能冷却时间输入框
                for (int i = 0; i < 4; i++) {
                    cooldownFields[i] = new GuiTextField(i, fontRenderer, centerX - 50, y, 100, 20);
                    cooldownFields[i].setText(String.valueOf(skillCooldowns[i]));
                    textFields.add(cooldownFields[i]); 
                    y += 30;
                }
                
                // 技能启用按钮
                for (int i = 0; i < 4; i++) {
                    int buttonY = 50 + i * 30;
                    skillEnableButtons[i] = new GuiButton(10 + i, centerX + 60, buttonY, 20, 20, autoSkillIndividualEnabled[i] ? "✔" : "✘");
                    buttonList.add(skillEnableButtons[i]);
                }

                // 启用/禁用按钮
                enableButton = new GuiButton(4, centerX - 50, y, 100, 20, autoSkillEnabled ? "自动技能 ✔" : "自动技能 ✘");
                buttonList.add(enableButton);
                y += 30;

                // 保存按钮
                saveButton = new GuiButton(5, centerX - 50, y, 100, 20, "保存");
                buttonList.add(saveButton);
                y += 30;
            }

            @Override
            protected void actionPerformed(GuiButton button) throws IOException {
                switch (button.id) {
                    case 4: //自动技能启用/禁用按钮
                        autoSkillEnabled = !autoSkillEnabled;
                        enableButton.displayString = autoSkillEnabled ? "自动技能 ✔" : "自动技能 ✘"; // 更新按钮文本
                        break;
                    case 5: // 自动技能保存按钮
                    	// 每次保存前重置错误标志
                        hasError = false;
                        for (int i = 0; i < 4; i++) {
                        	final int currentIndex = i; // 创建final副本
                            try {
                                String input = cooldownFields[currentIndex].getText();
                                if (input.isEmpty()) throw new NumberFormatException();
                                // 限制最小值为1秒
                                skillCooldowns[i] = Math.max(1, Long.parseLong(input)); 
                                
                            } catch (NumberFormatException e) {
                            	// 处理无效输入，设置错误标志
                                hasError = true;
                                cooldownFields[i].setText("1"); // 重置为默认值1s
                            }
                        }
                        if (hasError) {
                            message = "保存失败，存在无效输入！"; // 错误提示
                        } else {
                            saveSkillConfig();
                            // 初始化技能最后使用时间
                            Arrays.fill(GuiInventory.skillCounts, 0);
                            message = "配置保存成功！"; // 成功提示
                        }
                        // 1 秒后清除提示
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Minecraft.getMinecraft().addScheduledTask(() -> {
                                    message = "";
                                });
                            }
                        }, 1000);
                        break;
                    default:
                        // 处理技能启用/禁用按钮点击
                        if (button.id >= 10 && button.id <= 13) {
                            int skillIndex = button.id - 10;
                            autoSkillIndividualEnabled[skillIndex] = !autoSkillIndividualEnabled[skillIndex];
                            button.displayString = autoSkillIndividualEnabled[skillIndex] ? "✔" : "✘";
                        }
                        break;
                }
            }

            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                drawDefaultBackground();
                drawCenteredString(fontRenderer, "自动技能配置(s)", width / 2, 20, 0x3E885B); // 海绿色

                // 绘制技能标签
                String[] skillLabels = {"技能1 (R)", "技能2 (Z)", "技能3 (X)", "技能4 (C)"};
                int centerX = width / 2;
                int y = 50;
                for (int i = 0; i < 4; i++) {
                    drawString(fontRenderer, skillLabels[i], centerX - 120, y + 5, 0xFFFFFF);
                    cooldownFields[i].drawTextBox();
                    y += 30;
                }
                
                // 绘制提示信息
                if (!message.isEmpty()) {
                    int color = message.startsWith("保存成功") ? 0x00FF00 : 0xFF0000;
                    drawCenteredString(fontRenderer, message, width / 2, height - 30, color);
                }

                super.drawScreen(mouseX, mouseY, partialTicks);
            }

            @Override
            public void updateScreen() {
                for (GuiTextField field : cooldownFields) {
                    field.updateCursorCounter();
                }
            }

            @Override
            protected void keyTyped(char typedChar, int keyCode) throws IOException {
                for (GuiTextField field : cooldownFields) {
                    field.textboxKeyTyped(typedChar, keyCode);
                }
                super.keyTyped(typedChar, keyCode);
            }

            @Override
            protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
                for (GuiTextField field : cooldownFields) {
                    field.mouseClicked(mouseX, mouseY, mouseButton);
                }
                super.mouseClicked(mouseX, mouseY, mouseButton);
            }
            
            @Override
            public void onGuiClosed() { // 关闭GUI后立即执行一次所有技能
                if (autoSkillEnabled) {
                    Minecraft.getMinecraft().addScheduledTask(() -> {
                        // 模拟按下并松开所有技能键
                    	for (int i = 0; i < 4; i++) {
                    		final int currentIndex = i; // 创建final副本
                    		
                    		// 仅当技能启用时才执行
                            if (!autoSkillIndividualEnabled[currentIndex]) continue;
                            
                    		new DelayAction(3 * currentIndex, () -> {
                    			simulateKey(SKILL_KEYS[currentIndex], true);
                    			new DelayAction(2, () -> {
                    				simulateKey(SKILL_KEYS[currentIndex], false);
                    			}).accept(mc.player);
                    		}).accept(mc.player);
                        }
                        Arrays.fill(skillCounts, 0);
                    });
                }
            }
            
        }
        
        // 循环次数设置GUI
        public static class LoopCountInputGui extends GuiScreen {
            private final GuiInventory parent;
            private String inputText = "";
            private GuiTextField numberField;
            
            public LoopCountInputGui(GuiInventory parent) {
                this.parent = parent;
            }
            
            @Override
            public void initGui() {
                super.initGui();
                this.buttonList.clear();
                
                // 创建输入框
                numberField = new GuiTextField(0, fontRenderer, 
                    this.width/2 - 100, this.height/2 - 25, 
                    200, 20);
                numberField.setFocused(true);
                numberField.setCanLoseFocus(false);
                numberField.setMaxStringLength(10);
                numberField.setText(inputText);
                
                // 确认按钮
                this.buttonList.add(new GuiButton(0, this.width/2 - 100, this.height/2, 90, 20, "确认"));
                // 取消按钮
                this.buttonList.add(new GuiButton(1, this.width/2 + 10, this.height/2, 90, 20, "取消"));
                // 无限循环按钮
                this.buttonList.add(new GuiButton(2, this.width/2 - 100, this.height/2 + 30, 200, 20, "设置为无限循环"));
                
                // 添加自动进食开关按钮（在自动操作分类中）
                buttonList.add(new GuiButton(101, width / 2 - 100, height / 2 + 55, 200, 20, 
                    "自动进食: " + (autoEatEnabled ? "✔" : "✘")));
            }
            
            @Override
            protected void keyTyped(char typedChar, int keyCode) throws IOException {
                super.keyTyped(typedChar, keyCode);
                
                if (numberField.textboxKeyTyped(typedChar, keyCode)) {
                    inputText = numberField.getText();
                    return;
                }
            }
            
            @Override
            protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
                super.mouseClicked(mouseX, mouseY, mouseButton);
                numberField.mouseClicked(mouseX, mouseY, mouseButton);
            }
            
            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                this.drawDefaultBackground();
                
                // 标题
                drawCenteredString(fontRenderer, "设置循环次数", width/2, height/2 - 50, 0xFFFFFF);
                
                // 提示文字
                drawString(fontRenderer, "输入数字（0=不循环，-1=无限循环）:", 
                    width/2 - 100, height/2 - 40, 0xA0A0A0);
                
                // 绘制输入框
                numberField.drawTextBox();
                
                super.drawScreen(mouseX, mouseY, partialTicks);
            }
            
            @Override
            protected void actionPerformed(GuiButton button) throws IOException {
                if (button.id == 0) { // 确认按钮
                    setLoopCount();
                    mc.displayGuiScreen(parent);
                } 
                else if (button.id == 1) { // 取消按钮
                    mc.displayGuiScreen(parent);
                }
                else if (button.id == 2) { // 无限循环按钮
                    GuiInventory.loopCount = -1; // 修正点：使用 parent.loopCount
                    mc.displayGuiScreen(parent);
                }
                
                if (button.id == 101) { // 自动进食开关
                    autoEatEnabled = !autoEatEnabled;
                    saveAutoEatConfig();  // 立即保存配置
                    button.displayString = "自动进食: " + (autoEatEnabled ? "开启" : "关闭");
                }
            }
            
            private void setLoopCount() {
                try {
                    GuiInventory.loopCount = Integer.parseInt(inputText.trim()); // 修正点：使用 parent.loopCount
                    GuiInventory.loopCounter = 0; // 修正点：使用 parent.loopCounter
                } catch (NumberFormatException e) {
                    // 无效输入处理
                    GuiInventory.loopCount = 1; // 修正点：使用 parent.loopCount
                    Minecraft.getMinecraft().player.sendMessage(
                        new TextComponentString("§c无效输入! 已重置为单次循环"));
                }
            }
        }
    }
    
}