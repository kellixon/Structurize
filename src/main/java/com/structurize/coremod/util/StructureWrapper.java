package com.structurize.coremod.util;

import com.structurize.api.configuration.Configurations;
import com.structurize.api.util.*;
import com.structurize.coremod.blocks.ModBlocks;
import com.structurize.coremod.blocks.interfaces.IAnchorBlock;
import com.structurize.coremod.management.Manager;
import com.structurize.coremod.placementhandlers.IPlacementHandler;
import com.structurize.coremod.placementhandlers.PlacementHandlers;
import com.structurize.structures.helpers.Structure;
import com.structurize.structures.helpers.StructureProxy;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.template.PlacementSettings;
import net.minecraft.world.gen.structure.template.Template;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Interface for using the structure codebase.
 */
public class StructureWrapper
{
    /**
     * The position we use as our uninitialized value.
     */
    protected static final BlockPos NULL_POS = new BlockPos(-1, -1, -1);

    /**
     * The Structure position we are at. Defaulted to NULL_POS.
     */
    protected final BlockPos.MutableBlockPos progressPos = new BlockPos.MutableBlockPos(-1, -1, -1);
    /**
     * The minecraft world this struture is displayed in.
     */
    protected final World          world;
    /**
     * The structure this structure comes from.
     */
    protected final StructureProxy structure;
    /**
     * The name this structure has.
     */
    protected final String         name;
    /**
     * The anchor position this structure will be
     * placed on in the minecraft world.
     */
    protected       BlockPos       position;

    /**
     * If complete placement or not.
     */
    private boolean complete = false;

    /**
     * Load a structure into this world.
     *
     * @param worldObj the world to load in
     * @param name     the structure name
     */
    public StructureWrapper(final World worldObj, final String name)
    {
        this(worldObj, new StructureProxy(worldObj, name), name);
    }

    /**
     * Create a new StructureProxy.
     *
     * @param worldObj  the world to show it in
     * @param structure the structure it comes from
     * @param name      the name this structure has
     */
    protected StructureWrapper(final World worldObj, final StructureProxy structure, final String name)
    {
        world = worldObj;
        this.structure = structure;
        this.name = name;
    }

    /**
     * Unload a structure at a certain location.
     * @param world the world.
     * @param pos the position.
     * @param first the name.
     * @param rotation the rotation.
     * @param mirror the mirror.
     */
    public static void unloadStructure(@NotNull final World world, @NotNull final BlockPos pos, @NotNull final String first, final int rotation, @NotNull final Mirror mirror)
    {
        @NotNull final StructureWrapper structureWrapper = new StructureWrapper(world, first);
        structureWrapper.position = pos;
        structureWrapper.rotate(rotation, world, pos, mirror);
        structureWrapper.removeStructure(pos.subtract(structureWrapper.getOffset()));
    }

    /**
     * Remove a structure from the world.
     *
     * @param pos      coordinates
     */
    private void removeStructure(@NotNull final BlockPos pos)
    {
        setLocalPosition(pos);
        for (int j = 0; j < structure.getHeight(); j++)
        {
            for (int k = 0; k < structure.getLength(); k++)
            {
                for (int i = 0; i < structure.getWidth(); i++)
                {
                    @NotNull final BlockPos localPos = new BlockPos(i, j, k);
                    final BlockPos worldPos = pos.add(localPos);
                    if (!world.isAirBlock(worldPos))
                    {
                        world.setBlockToAir(worldPos);
                    }
                }
            }
        }
    }

    /**
     * Load a structure into this world
     * and place it in the right position and rotation.
     *  @param worldObj  the world to load it in
     * @param pos       coordinates
     * @param rotations number of times rotated
     * @param mirror    the mirror used.
     * @param player the placing player.
     */
    public static void loadAndPlaceShapeWithRotation(
      final WorldServer worldObj,
      final Template template,
      @NotNull final BlockPos pos, final int rotations, @NotNull final Mirror mirror, final EntityPlayerMP player)
    {
        try
        {
            final Structure structure = new Structure(worldObj);
            StructureProxy proxy = new StructureProxy(structure);
            structure.setTemplate(template);
            structure.setPlacementSettings(new PlacementSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE));
            @NotNull final StructureWrapper structureWrapper = new StructureWrapper(worldObj, proxy, "shape" + player.getName() + ".nbt");
            structureWrapper.position = pos;
            structureWrapper.rotate(rotations, worldObj, pos, mirror);
            structureWrapper.setupStructurePlacement(pos.subtract(structureWrapper.getOffset()), false, player);
        }
        catch (final IllegalStateException e)
        {
            Log.getLogger().warn("Could not load structure!", e);
        }
    }

    /**
     * Load a structure into this world
     * and place it in the right position and rotation.
     *  @param worldObj  the world to load it in
     * @param name      the structures name
     * @param pos       coordinates
     * @param rotations number of times rotated
     * @param mirror    the mirror used.
     * @param complete  paste it complete (with structure blocks) or without
     * @param player the placing player.
     */
    public static void loadAndPlaceStructureWithRotation(
      final World worldObj, @NotNull final String name,
      @NotNull final BlockPos pos, final int rotations, @NotNull final Mirror mirror,
      final boolean complete, final EntityPlayerMP player)
    {
        try
        {
            @NotNull final StructureWrapper structureWrapper = new StructureWrapper(worldObj, name);
            structureWrapper.position = pos;
            structureWrapper.rotate(rotations, worldObj, pos, mirror);
            structureWrapper.setupStructurePlacement(pos.subtract(structureWrapper.getOffset()), complete, player);
        }
        catch (final IllegalStateException e)
        {
            Log.getLogger().warn("Could not load structure!", e);
        }
    }

    /**
     * Rotates the structure x times.
     *
     * @param times     times to rotateWithMirror.
     * @param world     world it's rotating it in.
     * @param rotatePos position to rotateWithMirror it around.
     * @param mirror    the mirror to rotate with.
     */
    public void rotate(final int times, @NotNull final World world, @NotNull final BlockPos rotatePos, @NotNull final Mirror mirror)
    {
        structure.rotateWithMirror(times, world, rotatePos, mirror);
    }

    /**
     * Setup the structure placement and add to buffer.
     * @param pos the world anchor.
     * @param complete if complete or not.
     * @param player the issuing player.
     */
    public void setupStructurePlacement(@NotNull final BlockPos pos, final boolean complete, final EntityPlayerMP player)
    {
        setLocalPosition(pos);
        this.complete = complete;
        this.position = pos;
        Manager.addToQueue(new ScanToolOperation(this, player));
    }

    /**
     * Place a structure into the world.
     * @param world the placing player.
     */
    public BlockPos placeStructure(final World world, final ChangeStorage storage, final BlockPos inputPos, final boolean complete)
    {
        setLocalPosition(inputPos);
        @NotNull final List<BlockPos> delayedBlocks = new ArrayList<>();
        final BlockPos endPos = new BlockPos(structure.getWidth(), structure.getHeight(), structure.getLength());
        BlockPos currentPos = inputPos;
        int count = 0;

        for (int y = currentPos.getY(); y < endPos.getY(); y++)
        {
            for (int x = currentPos.getX(); x < endPos.getX(); x++)
            {
                for (int z = currentPos.getZ(); z < endPos.getZ(); z++)
                {
                    @NotNull final BlockPos localPos = new BlockPos(x, y, z);
                    final IBlockState localState = this.structure.getBlockState(localPos);
                    if (localState == null)
                    {
                        continue;
                    }
                    final Block localBlock = localState.getBlock();

                    final BlockPos worldPos = position.add(localPos);

                    if ((localBlock == ModBlocks.blockSubstitution && !complete) || localBlock instanceof IAnchorBlock)
                    {
                        continue;
                    }
                    count++;

                    storage.addPositionStorage(worldPos, world);

                    if (localState.getMaterial().isSolid())
                    {
                        handleBlockPlacement(world, worldPos, localState, complete, this.structure.getBlockInfo(localPos).tileentityData);
                    }
                    else
                    {
                        delayedBlocks.add(localPos);
                    }

                    if (count >= Configurations.gameplay.maxOperationsPerTick)
                    {
                        this.handleDelayedBlocks(delayedBlocks, storage, world);
                        return new BlockPos(x, y, z);
                    }
                }
                currentPos = new BlockPos(x, y, 0);
            }
            currentPos = new BlockPos(0, y, 0);
        }
        currentPos = new BlockPos(0, 0, 0);
        this.handleDelayedBlocks(delayedBlocks, storage, world);

        for (int y = currentPos.getY(); y < endPos.getY(); y++)
        {
            for (int x = currentPos.getX(); x < endPos.getX(); x++)
            {
                for (int z = currentPos.getZ(); z < endPos.getZ(); z++)
                {
                    @NotNull final BlockPos localPos = new BlockPos(x, y, z);
                    final Template.EntityInfo info = this.structure.getEntityinfo(localPos);

                    if (info != null)
                    {
                        try
                        {
                            final Entity entity = EntityList.createEntityFromNBT(info.entityData, world);
                            entity.setUniqueId(UUID.randomUUID());
                            world.spawnEntity(entity);
                            storage.addToBeKilledEntity(entity);
                        }
                        catch (final RuntimeException e)
                        {
                            Log.getLogger().info("Couldn't restore entitiy", e);
                        }
                    }
                    if (count >= Configurations.gameplay.maxOperationsPerTick)
                    {
                        return new BlockPos(x, y, z);
                    }
                }
                currentPos = new BlockPos(x, y, 0);
            }
            currentPos = new BlockPos(0, y, 0);
        }

        return null;
    }

    /**
     * Handle the delayed blocks (non solid blocks)
     * @param delayedBlocks the delayed block list.
     * @param storage the changeStorage.
     * @param world the world.
     */
    private void handleDelayedBlocks(final List<BlockPos> delayedBlocks, final ChangeStorage storage, final World world)
    {
        for (@NotNull final BlockPos coords : delayedBlocks)
        {
            final IBlockState localState = this.structure.getBlockState(coords);
            final BlockPos newWorldPos = position.add(coords);
            storage.addPositionStorage(coords, world);
            handleBlockPlacement(world, newWorldPos, localState, complete, this.structure.getBlockInfo(coords) == null ? null : this.structure.getBlockInfo(coords).tileentityData);
        }
    }

    /**
     * @return Where the hut (or any offset) is in the structure.
     */
    public BlockPos getOffset()
    {
        return structure.getOffset();
    }

    /**
     * This method handles the block placement.
     * When we extract this into another mod, we have to override the method.
     *
     * @param world the world.
     * @param pos the world position.
     * @param localState the local state.
     * @param complete if complete with it.
     * @param tileEntityData the tileEntity.
     */
    public void handleBlockPlacement(final World world, final BlockPos pos, final IBlockState localState, final boolean complete, final NBTTagCompound tileEntityData)
    {
        for (final IPlacementHandler handlers : PlacementHandlers.handlers)
        {
            if (handlers.canHandle(world, pos, localState))
            {
                handlers.handle(world, pos, localState, tileEntityData, complete, position);
                return;
            }
        }
    }

    /**
     * Increment progressPos.
     *
     * @return false if the all the block have been incremented through.
     */
    public boolean incrementBlock()
    {
        if (this.progressPos.equals(NULL_POS))
        {
            this.progressPos.setPos(-1, 0, 0);
        }

        this.progressPos.setPos(this.progressPos.getX() + 1, this.progressPos.getY(), this.progressPos.getZ());
        if (this.progressPos.getX() == structure.getWidth())
        {
            this.progressPos.setPos(0, this.progressPos.getY(), this.progressPos.getZ() + 1);
            if (this.progressPos.getZ() == structure.getLength())
            {
                this.progressPos.setPos(this.progressPos.getX(), this.progressPos.getY() + 1, 0);
                if (this.progressPos.getY() == structure.getHeight())
                {
                    reset();
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if the block in the world is the same as what is in the structure.
     *
     * @return true if the structure block equals the world block.
     */
    public boolean isStructureBlockEqualWorldBlock()
    {
        final IBlockState structureBlockState = structure.getBlockState(this.getLocalPosition());
        final Block structureBlock = structureBlockState.getBlock();

        //All worldBlocks are equal the substitution block
        if (structureBlock == ModBlocks.blockSubstitution)
        {
            return true;
        }

        final BlockPos worldPos = this.getBlockPosition();

        final IBlockState worldBlockState = world.getBlockState(worldPos);

        if (structureBlock == ModBlocks.blockSolidSubstitution && worldBlockState.getMaterial().isSolid())
        {
            return true;
        }

        final Block worldBlock = worldBlockState.getBlock();

        //list of things to only check block for.
        //For the time being any flower pot is equal to each other.
        if (structureBlock instanceof BlockDoor || structureBlock == Blocks.FLOWER_POT)
        {
            return structureBlock == worldBlock;
        }
        else if ((structureBlock instanceof BlockStairs && structureBlockState == worldBlockState)
                   || BlockUtils.isGrassOrDirt(structureBlock, worldBlock, structureBlockState, worldBlockState))
        {
            return true;
        }

        final Template.EntityInfo entityInfo = structure.getEntityinfo(this.getLocalPosition());
        if (entityInfo != null)
        {
            return false;
            //todo get entity at position.
        }

        //had this problem in a super flat world, causes builder to sit doing nothing because placement failed
        return worldPos.getY() <= 0
                 || structureBlockState == worldBlockState;
    }

    /**
     * Reset the progressPos.
     */
    public void reset()
    {
        BlockPosUtil.set(this.progressPos, NULL_POS);
    }

    /**
     * @return progressPos as an immutable.
     */
    @NotNull
    public BlockPos getLocalPosition()
    {
        return this.progressPos.toImmutable();
    }

    /**
     * Change the current progressPos. Used when loading progress.
     *
     * @param localPosition new progressPos.
     */
    public void setLocalPosition(@NotNull final BlockPos localPosition)
    {
        BlockPosUtil.set(this.progressPos, localPosition);
    }

    /**
     * @return World position.
     */
    public BlockPos getBlockPosition()
    {
        return this.progressPos.add(getOffsetPosition());
    }

    /**
     * @return Min world position for the structure.
     */
    public BlockPos getOffsetPosition()
    {
        return position.subtract(getOffset());
    }

    /**
     * Gets the block state for the current local block.
     *
     * @return Current local block state.
     */
    @Nullable
    public IBlockState getBlockState()
    {
        if (this.progressPos.equals(NULL_POS))
        {
            return null;
        }
        return this.structure.getBlockState(this.progressPos);
    }

    /**
     * @return A list of all the entities in the structure.
     */
    @NotNull
    public List<Template.EntityInfo> getEntities()
    {
        return structure.getTileEntities();
    }

    /**
     * Base position of the structure.
     *
     * @return BlockPos representing where the structure is.
     */
    public BlockPos getPosition()
    {
        if (position == null)
        {
            return new BlockPos(0, 0, 0);
        }
        return position;
    }

    /**
     * Set the position, used when loading.
     *
     * @param position Where the structure is in the world.
     */
    public void setPosition(final BlockPos position)
    {
        this.position = position;
    }

    /**
     * Calculate the item needed to place the current block in the structure.
     *
     * @return an item or null if not initialized.
     */
    @Nullable
    public Item getItem()
    {
        @Nullable final Block block = this.getBlock();
        @Nullable final IBlockState blockState = this.getBlockState();
        if (block == null || blockState == null || block == Blocks.AIR || blockState.getMaterial().isLiquid())
        {
            return null;
        }

        final ItemStack stack = BlockUtils.getItemStackFromBlockState(blockState);

        if (!ItemStackUtils.isEmpty(stack))
        {
            return stack.getItem();
        }

        return null;
    }

    /**
     * Calculate the current block in the structure.
     *
     * @return the current block or null if not initialized.
     */
    @Nullable
    public Block getBlock()
    {
        @Nullable final IBlockState state = getBlockState();
        if (state == null)
        {
            return null;
        }
        return state.getBlock();
    }

    /**
     * Find the next block that doesn't already exist in the world.
     *
     * @return true if a new block is found and false if there is no next block.
     */
    public boolean findNextBlock()
    {
        int count = 0;
        do
        {
            count++;
            if (!incrementBlock())
            {
                return false;
            }
        }
        while (isStructureBlockEqualWorldBlock() && count < Configurations.gameplay.maxBlocksChecked);

        return true;
    }

    /**
     * Check if there is enough free space to place a structure in the world.
     *
     * @param pos coordinates
     */
    public boolean checkForFreeSpace(@NotNull final BlockPos pos)
    {
        setLocalPosition(pos);
        for (int j = 0; j < structure.getHeight(); j++)
        {
            for (int k = 0; k < structure.getLength(); k++)
            {
                for (int i = 0; i < structure.getWidth(); i++)
                {
                    @NotNull final BlockPos localPos = new BlockPos(i, j, k);

                    final BlockPos worldPos = pos.add(localPos);

                    if (worldPos.getY() <= pos.getY() && !world.getBlockState(worldPos.down()).getMaterial().isSolid())
                    {
                        return false;
                    }

                    final IBlockState worldState = world.getBlockState(worldPos);
                    if (worldState.getBlock() == Blocks.BEDROCK)
                    {
                        return false;
                    }

                    if (worldPos.getY() > pos.getY() && worldState.getBlock() != Blocks.AIR)
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Decrement progressPos.
     *
     * @return false if progressPos can't be decremented any more.
     */
    public boolean decrementBlock()
    {
        if (this.progressPos.equals(NULL_POS))
        {
            this.progressPos.setPos(structure.getWidth(), structure.getHeight() - 1, structure.getLength() - 1);
        }

        this.progressPos.setPos(this.progressPos.getX() - 1, this.progressPos.getY(), this.progressPos.getZ());
        if (this.progressPos.getX() == -1)
        {
            this.progressPos.setPos(structure.getWidth() - 1, this.progressPos.getY(), this.progressPos.getZ() - 1);
            if (this.progressPos.getZ() == -1)
            {
                this.progressPos.setPos(this.progressPos.getX(), this.progressPos.getY() - 1, structure.getLength() - 1);
                if (this.progressPos.getY() == -1)
                {
                    reset();
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @return The name of the structure.
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return The height of the structure.
     */
    public int getHeight()
    {
        return structure.getHeight();
    }

    /**
     * @return The width of the structure.
     */
    public int getWidth()
    {
        return structure.getWidth();
    }

    /**
     * @return The length of the structure.
     */
    public int getLength()
    {
        return structure.getLength();
    }

    /**
     * @return The StructureProxy that houses all the info about what is stored in a structure.
     */
    public StructureProxy getStructure()
    {
        return structure;
    }

    /**
     * Calculate the current block in the structure.
     *
     * @return the current block or null if not initialized.
     */
    @Nullable
    public Template.BlockInfo getBlockInfo()
    {
        if (this.progressPos.equals(NULL_POS))
        {
            return null;
        }
        return this.structure.getBlockInfo(this.progressPos);
    }

    /**
     * Calculate the current entity in the structure.
     *
     * @return the entityInfo.
     */
    @Nullable
    public Template.EntityInfo getEntityinfo()
    {
        if (this.progressPos.equals(NULL_POS))
        {
            return null;
        }
        return this.structure.getEntityinfo(this.progressPos);
    }
}
