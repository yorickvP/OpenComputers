package li.cil.oc.common.event

import cpw.mods.fml.common.eventhandler.SubscribeEvent
import li.cil.oc.api
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.server.component.WirelessNetworkCard

import scala.collection.convert.WrapAsScala._

object WirelessNetworkCardHandler {
  @SubscribeEvent
  def onMove(e: RobotMoveEvent.Post) {
    val machineNode = e.agent.machine.node
    machineNode.reachableNodes.foreach(_.host match {
      case card: WirelessNetworkCard => api.Network.updateWirelessNetwork(card)
      case _ =>
    })
  }
}
