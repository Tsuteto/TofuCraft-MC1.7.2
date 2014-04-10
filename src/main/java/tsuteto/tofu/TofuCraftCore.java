package tsuteto.tofu;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.VillagerRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockDispenser;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.*;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.oredict.OreDictionary;
import tsuteto.tofu.achievement.TcAchievementList;
import tsuteto.tofu.block.RenderYubaBlock;
import tsuteto.tofu.block.TcBlocks;
import tsuteto.tofu.block.tileentity.TileEntityMorijio;
import tsuteto.tofu.dispanse.DispenserBehaviorTcEmptyBucket;
import tsuteto.tofu.entity.TcEntity;
import tsuteto.tofu.entity.TofuCreeperSeed;
import tsuteto.tofu.eventhandler.*;
import tsuteto.tofu.fishing.TofuFishing;
import tsuteto.tofu.fluids.TcFluids;
import tsuteto.tofu.gui.TcGuiHandler;
import tsuteto.tofu.item.TcItems;
import tsuteto.tofu.network.PacketPipeline;
import tsuteto.tofu.network.packet.*;
import tsuteto.tofu.potion.TcPotion;
import tsuteto.tofu.recipe.Recipes;
import tsuteto.tofu.recipe.TcOreDic;
import tsuteto.tofu.util.ModLog;
import tsuteto.tofu.village.*;
import tsuteto.tofu.world.biome.BiomeGenTofuBase;
import tsuteto.tofu.world.TcChunkProviderEvent;
import tsuteto.tofu.world.WorldProviderTofu;
import tsuteto.tofu.world.biome.TcBiomes;
import tsuteto.tofu.world.tofuvillage.EntityJoinWorldEventHandler;
import tsuteto.tofu.world.tofuvillage.GetVillageBlockIDEventHandler;

import java.util.Arrays;

/**
 * The Main Class of the TofuCraft
 *
 * @author Tsuteto
 *
 */
@Mod(modid = TofuCraftCore.modid, version = "1.5.13-MC1.7.2")
public class TofuCraftCore
{
    public static final String modid = "TofuCraft";
    public static final String resourceDomain = "tofucraft:";

    @Mod.Instance(modid)
    public static TofuCraftCore instance;

    @SidedProxy(clientSide = "tsuteto.tofu.TofuCraftCore$ClientProxy", serverSide = "tsuteto.tofu.TofuCraftCore$ServerProxy")
    public static ISidedProxy sidedProxy;

    public static final PacketPipeline packetPipeline = new PacketPipeline();
    
    public static final BiomeDictionary.Type BIOME_TYPE_TOFU = EnumHelper.addEnum(BiomeDictionary.Type.class, "TOFU", new Class[0], new Object[0]);
    public static final CreativeTabs tabTofuCraft = new CreativeTabTofuCraft(modid);

    private Configuration conf;

    static
    {
        ModLog.modId = TofuCraftCore.modid;
    }

    @Mod.EventHandler
    public void preload(FMLPreInitializationEvent event)
    {
        conf = new Configuration(event.getSuggestedConfigurationFile());
        conf.load();

        Settings.load(conf);
        ModLog.isDebug = Settings.debug;

        conf.save();

        // Register basic features
        TcBlocks.register();
        TcItems.register();
        TcEntity.register(this);

        // Register liquid blocks
        TcFluids.register(event.getSide());
        
        // Prepare Tofu Force Materials
        TfMaterialRegistry.init();

        // Add Achievements
        if (Settings.achievement)
        {
            TcAchievementList.load();
        }
    }

    @Mod.EventHandler
    public void load(FMLInitializationEvent event)
    {
        // Register usage of the bone meal
        MinecraftForge.EVENT_BUS.register(new EventBonemeal());

        // Register usage of the empty bucket
        MinecraftForge.EVENT_BUS.register(new EventFillBucket());

        // Register event on player interact
        MinecraftForge.EVENT_BUS.register(new EventPlayerInteract());

        // Register event on EntityLiving
        MinecraftForge.EVENT_BUS.register(new EventEntityLiving());

        // Register event on tofu fishing
        MinecraftForge.EVENT_BUS.register(new TofuFishing());

        // Register gui handler
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new TcGuiHandler());

        // Register crafting handler
        FMLCommonHandler.instance().bus().register(new TcCraftingHandler());

        // Register pick-up Handler
        FMLCommonHandler.instance().bus().register(new EventItemPickup());

        // Register game tick handler
        FMLCommonHandler.instance().bus().register(new GameTickHandler());

        {
            TcChunkProviderEvent eventhandler = new TcChunkProviderEvent();
            
            // Nether populating
            MinecraftForge.EVENT_BUS.register(eventhandler);
    
            // Ore generation
            MinecraftForge.ORE_GEN_BUS.register(eventhandler);
        }
        
        // Register the Tofu World
        DimensionManager.registerProviderType(Settings.tofuDimNo, WorldProviderTofu.class, false);
        DimensionManager.registerDimension(Settings.tofuDimNo, Settings.tofuDimNo);
        
        // Register Tofu World biomes to the Forge Biome Dictionary
        for (BiomeGenTofuBase biome : TcBiomes.tofuBiomeList)
        {
            if (biome != null) BiomeDictionary.registerBiomeType(biome, BIOME_TYPE_TOFU);
        }
        ModLog.debug("Registered biomes as TOFU: " + Arrays.toString(BiomeDictionary.getBiomesForType(BIOME_TYPE_TOFU)));
        
        // Tofu Village handler
        BiomeManager.addVillageBiome(TcBiomes.tofuPlains, true);
        BiomeManager.addVillageBiome(TcBiomes.tofuLeekPlains, true);
        BiomeManager.addVillageBiome(TcBiomes.tofuForest, true);
        MinecraftForge.TERRAIN_GEN_BUS.register(new GetVillageBlockIDEventHandler());
        MinecraftForge.EVENT_BUS.register(new EntityJoinWorldEventHandler());

        // Register the profession of Tofu Cook
        VillagerRegistry vill = VillagerRegistry.instance();
        vill.registerVillagerId(Settings.professionIdTofucook);
        vill.registerVillageTradeHandler(Settings.professionIdTofucook, new TradeHandlerTofuCook());

        // Register the profession of Tofunian
        vill.registerVillagerId(Settings.professionIdTofunian);
        vill.registerVillageTradeHandler(Settings.professionIdTofunian, new TradeHandlerTofunian());

        // Add Trade Recipes
        vill.registerVillageTradeHandler(0, new TradeHandlerFarmer());
        
        // Tofu cook's house
        vill.registerVillageCreationHandler(new TofuCookHouseHandler());
        net.minecraft.world.gen.structure.MapGenStructureIO.func_143031_a(ComponentVillageHouseTofu.class, "ViTfH");

        // Register Packets
        packetPipeline.initalise();
        packetPipeline.registerPacket(PacketDimTrip.class);
        packetPipeline.registerPacket(PacketBugle.class);
        packetPipeline.registerPacket(PacketZundaArrowHit.class);
        packetPipeline.registerPacket(PacketZundaArrowType.class);
        packetPipeline.registerPacket(PacketTofuRadar.class);
        packetPipeline.registerPacket(PacketGlowingFinish.class);
        packetPipeline.registerPacket(PacketTfMachineData.class);

        // Add chest loot
        this.registerChestLoot(new ItemStack(TcItems.defattingPotion), 1, 1, 8);
        this.registerChestLoot(new ItemStack(TcBlocks.tcSapling, 1, 0), 1, 4, 8);
        this.registerChestLoot(new ItemStack(TcItems.doubanjiang), 1, 1, 4);

        ChestGenHooks.addItem(ChestGenHooks.VILLAGE_BLACKSMITH,
                new WeightedRandomChestContent(new ItemStack(TcItems.bugle), 1, 1, 5));

        // Ore Dictionary additions for common use
        OreDictionary.registerOre("logWood", new ItemStack(TcBlocks.tcLog, 1, OreDictionary.WILDCARD_VALUE));

        // Register recipes
        Recipes.unifyOreDicItems();
        Recipes.register();

        // Register renderer of yuba
        RenderingRegistry.registerBlockHandler(new RenderYubaBlock());

        // Register sided components
        sidedProxy.registerComponents();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event)
    {
        TcEntity.addSpawns();

        TcItems.registerExternalModItems();
        TcBlocks.registerExternalModBlocks();
        Recipes.registerExternalModRecipes();

        // Register potion effects
        TcPotion.register(conf);
        // Register biomes
        TcBiomes.register(conf);

        packetPipeline.postInitialise();

        conf.save();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event){
        event.registerServerCommand(new CommandTofuSlimeCheck());

        // Replace dispenser behavior for empty bucket
        BlockDispenser.dispenseBehaviorRegistry.putObject(Items.bucket, new DispenserBehaviorTcEmptyBucket());

        // To handle spawn of Tofu Creeper ;)
        TofuCreeperSeed.initialize(12L);
        TofuCreeperSeed.instance().initSeed(event.getServer().worldServerForDimension(0).getSeed());
    }

    public void registerChestLoot(ItemStack loot, int min, int max, int rarity)
    {
        ChestGenHooks.addItem(ChestGenHooks.DUNGEON_CHEST,
                new WeightedRandomChestContent(loot, min, max, rarity));
        ChestGenHooks.addItem(ChestGenHooks.MINESHAFT_CORRIDOR,
                new WeightedRandomChestContent(loot, min, max, rarity));
        ChestGenHooks.addItem(ChestGenHooks.STRONGHOLD_CORRIDOR,
                new WeightedRandomChestContent(loot, min, max, rarity));
        ChestGenHooks.addItem(ChestGenHooks.STRONGHOLD_CROSSING,
                new WeightedRandomChestContent(loot, min, max, rarity));
        ChestGenHooks.addItem(ChestGenHooks.STRONGHOLD_LIBRARY,
                new WeightedRandomChestContent(loot, min, max, rarity));
        ChestGenHooks.addItem(ChestGenHooks.PYRAMID_JUNGLE_CHEST,
                new WeightedRandomChestContent(loot, min, max, rarity));
    }

    @SideOnly(Side.CLIENT)
    public static class ClientProxy implements ISidedProxy
    {
        @Override
        public void registerComponents()
        {
            TcEntity.registerEntityRenderer();

            VillagerRegistry vill = VillagerRegistry.instance();
            vill.registerVillagerSkin(Settings.professionIdTofucook, new ResourceLocation("tofucraft", "textures/mob/tofucook.png"));
        }
    }

    @SideOnly(Side.SERVER)
    public static class ServerProxy implements ISidedProxy
    {
        @Override
        public void registerComponents()
        {
            GameRegistry.registerTileEntity(TileEntityMorijio.class, "TmMorijio");
        }
    }

    public static interface ISidedProxy
    {
        public void registerComponents();
    }
}
