package com.structurize.coremod.items;

import com.structurize.api.util.BlockPosUtil;
import com.structurize.api.util.LanguageHandler;
import com.structurize.api.util.Log;
import com.structurize.api.util.Utils;
import com.structurize.api.util.constant.Constants;
import com.structurize.coremod.Structurize;
import com.structurize.coremod.client.gui.WindowScan;
import com.structurize.coremod.creativetab.ModCreativeTabs;
import com.structurize.coremod.management.StructureName;
import com.structurize.coremod.management.Structures;
import com.structurize.coremod.network.messages.SaveScanMessage;
import com.structurize.structures.helpers.Structure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.template.Template;
import net.minecraft.world.gen.structure.template.TemplateManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import static com.structurize.api.util.constant.Constants.MAX_SCHEMATIC_SIZE;
import static com.structurize.api.util.constant.NbtTagConstants.FIRST_POS_STRING;
import static com.structurize.api.util.constant.NbtTagConstants.SECOND_POS_STRING;
import static com.structurize.api.util.constant.TranslationConstants.MAX_SCHEMATIC_SIZE_REACHED;

/**
 * Item used to scan structures.
 */
public class ItemScanTool extends AbstractItemStructurize
{
    /**
     * Creates instance of item.
     */
    public ItemScanTool()
    {
        super("scepterSteel");

        super.setCreativeTab(ModCreativeTabs.STRUCTURIZE);
        setMaxStackSize(1);
    }

    @Override
    public float getDestroySpeed(final ItemStack stack, final IBlockState state)
    {
        return Float.MAX_VALUE;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World worldIn, final EntityPlayer playerIn, final EnumHand hand)
    {
        final ItemStack stack = playerIn.getHeldItem(hand);
        if (!stack.hasTagCompound())
        {
            stack.setTagCompound(new NBTTagCompound());
        }
        final NBTTagCompound compound = stack.getTagCompound();

        @NotNull final BlockPos pos1 = BlockPosUtil.readFromNBT(compound, FIRST_POS_STRING);
        @NotNull final BlockPos pos2 = BlockPosUtil.readFromNBT(compound, SECOND_POS_STRING);

        if (!worldIn.isRemote)
        {
            if (playerIn.isSneaking())
            {
                saveStructure(worldIn, pos1, pos2, playerIn, null);
            }
        }
        else
        {
            if (!playerIn.isSneaking())
            {
                final WindowScan window = new WindowScan(pos1, pos2);
                window.open();
            }
        }

        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
    }

    @NotNull
    @Override
    public EnumActionResult onItemUse(
      final EntityPlayer playerIn,
      final World worldIn,
      final BlockPos pos,
      final EnumHand hand,
      final EnumFacing facing,
      final float hitX,
      final float hitY,
      final float hitZ)
    {
        final ItemStack stack = playerIn.getHeldItem(hand);
        if (!stack.hasTagCompound())
        {
            stack.setTagCompound(new NBTTagCompound());
        }
        final NBTTagCompound compound = stack.getTagCompound();

        @NotNull final BlockPos pos1 = BlockPosUtil.readFromNBT(compound, FIRST_POS_STRING);
        @NotNull final BlockPos pos2 = pos;
        if (pos2.distanceSq(pos1) > 0)
        {
            BlockPosUtil.writeToNBT(compound, SECOND_POS_STRING, pos2);
            if (worldIn.isRemote)
            {
                LanguageHandler.sendPlayerMessage(playerIn, "item.scepterSteel.point2", pos.getX(), pos.getY(), pos.getZ());
            }

            stack.setTagCompound(compound);
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.FAIL;
    }

    /**
     * Scan the structure and save it to the disk.
     *
     * @param world  Current world.
     * @param from   First corner.
     * @param to     Second corner.
     * @param player causing this action.
     * @param name   the name of it.
     */
    public static void saveStructure(@NotNull final World world, @NotNull final BlockPos from, @NotNull final BlockPos to, @NotNull final EntityPlayer player, final String name)
    {

        final BlockPos blockpos =
          new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        final BlockPos blockpos1 =
          new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        final BlockPos size = blockpos1.subtract(blockpos).add(1, 1, 1);
        if (size.getX() * size.getY() * size.getZ() > MAX_SCHEMATIC_SIZE)
        {
            LanguageHandler.sendPlayerMessage(player, MAX_SCHEMATIC_SIZE_REACHED, MAX_SCHEMATIC_SIZE);
            return;
        }
        final WorldServer worldserver = (WorldServer) world;
        final MinecraftServer minecraftserver = world.getMinecraftServer();
        final TemplateManager templatemanager = worldserver.getStructureTemplateManager();

        final long currentMillis = System.currentTimeMillis();
        final String currentMillisString = Long.toString(currentMillis);
        final String prefix = "/minecolonies/scans/";
        final String fileName;
        if (name == null || name.isEmpty())
        {
            fileName = LanguageHandler.format("item.scepterSteel.scanFormat", "", currentMillisString);
        }
        else
        {
            fileName = name;
        }

        final Template template = templatemanager.getTemplate(minecraftserver, new ResourceLocation(prefix + fileName + ".nbt"));
        template.takeBlocksFromWorld(world, blockpos, size, true, Blocks.STRUCTURE_VOID);
        template.setAuthor(Constants.MOD_ID);
        Structurize.getNetwork().sendTo(
          new SaveScanMessage(template.writeToNBT(new NBTTagCompound()), fileName), (EntityPlayerMP) player);
    }

    public static boolean saveStructureOnServer(@NotNull final World world, @NotNull final BlockPos from, @NotNull final BlockPos to, final String name)
    {
        final BlockPos blockpos =
          new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        final BlockPos blockpos1 =
          new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
        final BlockPos size = blockpos1.subtract(blockpos).add(1, 1, 1);
        if (size.getX() * size.getY() * size.getZ() > MAX_SCHEMATIC_SIZE)
        {
            Log.getLogger().warn("Saving too large schematic for:" + name);
        }
        final WorldServer worldserver = (WorldServer) world;
        final MinecraftServer minecraftserver = world.getMinecraftServer();
        final TemplateManager templatemanager = worldserver.getStructureTemplateManager();

        final String prefix = "cache";
        final String fileName;
        if (name == null || name.isEmpty())
        {
            fileName = LanguageHandler.format("item.scepterSteel.scanFormat");
        }
        else
        {
            fileName = name;
        }

        final StructureName structureName = new StructureName(prefix, "backup", fileName);

        final List<File> folder = Structure.getCachedSchematicsFolders();
        if (folder == null || folder.isEmpty())
        {
            Log.getLogger().warn("Unable to save schematic in cache since no folder was found.");
            return false;
        }

        final Template template = templatemanager.getTemplate(minecraftserver,
          new ResourceLocation(folder.get(0) + "/" + structureName.toString() + Structures.SCHEMATIC_EXTENSION));

        template.takeBlocksFromWorld(world, blockpos, size, false, Blocks.STRUCTURE_VOID);
        template.setAuthor(Constants.MOD_ID);

        final File file = new File(folder.get(0), structureName.toString() + Structures.SCHEMATIC_EXTENSION);
        Utils.checkDirectory(file.getParentFile());

        try (OutputStream outputstream = new FileOutputStream(file))
        {
            CompressedStreamTools.writeCompressed(template.writeToNBT(new NBTTagCompound()), outputstream);
        }
        catch (Exception e)
        {
            return false;
        }
        return true;
    }
}
