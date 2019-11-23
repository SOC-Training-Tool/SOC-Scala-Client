package client

import protos.soc.game.ActionRequest
import protos.soc.moves.ActionSpecification

import scala.concurrent.Future

trait Strategy {

  def getActionForRequest(request: ActionRequest, position: Int): Future[ActionSpecification]

}
