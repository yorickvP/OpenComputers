package li.cil.oc.integration.mcmp

import java.util

import _root_.net.minecraft.block.Block
import _root_.net.minecraft.block.material.Material
import _root_.net.minecraft.block.state.BlockState
import _root_.net.minecraft.block.state.IBlockState
import _root_.net.minecraft.entity.Entity
import _root_.net.minecraft.entity.player.EntityPlayer
import _root_.net.minecraft.item.ItemStack
import _root_.net.minecraft.nbt.NBTTagCompound
import _root_.net.minecraft.network.PacketBuffer
import _root_.net.minecraft.tileentity.TileEntity
import _root_.net.minecraft.util.AxisAlignedBB
import _root_.net.minecraft.util.EnumFacing
import _root_.net.minecraft.util.EnumWorldBlockLayer
import _root_.net.minecraftforge.common.property.ExtendedBlockState
import _root_.net.minecraftforge.common.property.IExtendedBlockState
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.EventHandler
import li.cil.oc.common.block.property
import li.cil.oc.common.tileentity
import mcmultipart.multipart.IOccludingPart
import mcmultipart.multipart.IRedstonePart
import mcmultipart.multipart.Multipart
import mcmultipart.raytrace.PartMOP

class PartPrint extends Multipart with IOccludingPart with IRedstonePart {
  final val PrintDefinition = api.Items.get(Constants.BlockName.Print)
  final val PrintBlock = PrintDefinition.block()

  val wrapped = new tileentity.Print(canToggle _, scheduleUpdate _, onStateChange _)

  def canToggle: Boolean = getWorld != null && !getWorld.isRemote && {
    val toggled = new PartPrint()
    val nbt = new NBTTagCompound()
    wrapped.writeToNBTForServer(nbt)
    toggled.wrapped.readFromNBTForServer(nbt)
    toggled.wrapped.state = !wrapped.state
    getContainer.canReplacePart(this, toggled)
  }

  def scheduleUpdate(delay: Int): Unit = {
    EventHandler.scheduleServer(() => {
      if (wrapped.state) wrapped.toggleState()
      if (wrapped.state) scheduleUpdate(delay)
    }, delay)
  }

  def onStateChange(): Unit = {
    notifyPartUpdate()
  }

  // ----------------------------------------------------------------------- //
  // IRedstonePart

  override def canConnectRedstone(side: EnumFacing): Boolean = true

  override def getStrongSignal(side: EnumFacing): Int = getWeakSignal(side)

  override def getWeakSignal(side: EnumFacing): Int = wrapped.output(side)

  // ----------------------------------------------------------------------- //
  // IOccludingPart

  override def addOcclusionBoxes(list: util.List[AxisAlignedBB]): Unit = wrapped.addCollisionBoxesToList(null, list)

  // ----------------------------------------------------------------------- //
  // IMultipart

  override def addSelectionBoxes(list: util.List[AxisAlignedBB]): Unit = wrapped.addCollisionBoxesToList(null, list)

  override def addCollisionBoxes(mask: AxisAlignedBB, list: util.List[AxisAlignedBB], collidingEntity: Entity): Unit = {
    if (getWorld != null) {
      wrapped.addCollisionBoxesToList(mask, list)
    }
  }

  override def getLightValue: Int = wrapped.data.lightLevel

  override def getPickBlock(player: EntityPlayer, hit: PartMOP): ItemStack = wrapped.data.createItemStack()

  override def getDrops: util.List[ItemStack] = util.Arrays.asList(wrapped.data.createItemStack())

  override def getHardness(hit: PartMOP): Float = PrintBlock.getBlockHardness(getWorld, getPos)

  override def getMaterial: Material = PrintBlock.getMaterial

  override def isToolEffective(toolType: String, level: Int): Boolean = PrintBlock.isToolEffective(toolType, getWorld.getBlockState(getPos))

  // ----------------------------------------------------------------------- //

  override def getModelPath = Settings.resourceDomain + ":" + Constants.BlockName.Print

  override def canRenderInLayer(layer: EnumWorldBlockLayer): Boolean = layer == EnumWorldBlockLayer.CUTOUT_MIPPED

  override def createBlockState(): BlockState = new ExtendedBlockState(PrintBlock, Array.empty, Array(property.PropertyTile.Tile))

  override def getExtendedState(state: IBlockState): IBlockState =
    state match {
      case extendedState: IExtendedBlockState =>
        wrapped.setWorldObj(getWorld)
        wrapped.setPos(getPos)
        extendedState.withProperty(property.PropertyTile.Tile, wrapped)
      case _ => state
    }

  // ----------------------------------------------------------------------- //

  override def onNeighborBlockChange(block: Block): Unit = {
    EnumFacing.values().foreach(wrapped.updateRedstoneInput)
  }

  override def onAdded(): Unit = {
    super.onAdded()
    initialize()
  }

  override def onRemoved(): Unit = {
    super.onRemoved()
    wrapped.invalidate()
    wrapped.setWorldObj(null)
    wrapped.setPos(null)
  }

  override def onLoaded(): Unit = {
    super.onLoaded()
    initialize()
  }

  override def onUnloaded(): Unit = {
    super.onUnloaded()
    wrapped.onChunkUnload()
    wrapped.setWorldObj(null)
    wrapped.setPos(null)
  }

  override def onConverted(tile: TileEntity): Unit = {
    super.onConverted(tile)
    tile match {
      case print: tileentity.Print =>
        val nbt = new NBTTagCompound()
        print.writeToNBTForServer(nbt)
        wrapped.readFromNBTForServer(nbt)
      case _ =>
    }
    initialize()
  }

  private def initialize(): Unit = {
    wrapped.setWorldObj(getWorld)
    wrapped.setPos(getPos)
    wrapped.validate()
    wrapped.updateRedstone()

    if (wrapped.state && wrapped.data.isButtonMode) {
      scheduleUpdate(PrintBlock.tickRate(getWorld))
    }
  }

  // ----------------------------------------------------------------------- //

  override def onActivated(player: EntityPlayer, heldItem: ItemStack, hit: PartMOP): Boolean = {
    wrapped.activate()
  }

  // ----------------------------------------------------------------------- //

  override def writeToNBT(tag: NBTTagCompound): Unit = {
    super.writeToNBT(tag)
    wrapped.writeToNBT(tag)
  }

  override def readFromNBT(tag: NBTTagCompound): Unit = {
    super.readFromNBT(tag)
    wrapped.readFromNBT(tag)
  }

  override def writeUpdatePacket(buf: PacketBuffer): Unit = {
    super.writeUpdatePacket(buf)
    val nbt = new NBTTagCompound()
    wrapped.writeToNBTForClient(nbt)
    buf.writeNBTTagCompoundToBuffer(nbt)
  }

  override def readUpdatePacket(buf: PacketBuffer): Unit = {
    super.readUpdatePacket(buf)
    wrapped.setWorldObj(getWorld)
    wrapped.setPos(getPos)
    val nbt = buf.readNBTTagCompoundFromBuffer()
    wrapped.readFromNBTForClient(nbt)
  }
}