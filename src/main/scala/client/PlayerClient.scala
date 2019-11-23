package client

import io.grpc.stub.StreamObserver
import protos.soc.game.ActionRequest.ActionRequestType.ACKNOWLEDGE_PING
import protos.soc.game.{ActionRequest, GameMessage, SubscribeRequest, TakeActionRequest}
import protos.soc.game.CatanServerGrpc.CatanServerStub
import protos.soc.game.SubscribeRequest.SubscriptionType.PLAYER
import protos.soc.moves.{ActionResult, ActionSpecification}
import protos.soc.moves.ActionSpecification.Specification
import protos.soc.moves.GameAction.ACKNOWLEDGE

import scala.concurrent.{ExecutionContext, Future, Promise}

abstract class PlayerClient(name: String, strategy: Strategy, server: CatanServerStub)(implicit ec: ExecutionContext) {

  def joinGame(gameId: String, position: Int): Future[Unit] = this.synchronized {
    val request: SubscribeRequest = SubscribeRequest.of(gameId, name, PLAYER, Some(position))
    val join = Promise.apply[Unit]()
    val streamObserver: StreamObserver[GameMessage] = new StreamObserver[GameMessage] {

      override def onNext(value: GameMessage): Unit = value.payload match {
        case GameMessage.Payload.Request(ActionRequest(ACKNOWLEDGE_PING, _, _)) =>
          server.takeAction(TakeActionRequest(gameId, position, ActionSpecification.of(ACKNOWLEDGE, Specification.Empty))).foreach { _ =>
            join.success()
          }

        case GameMessage.Payload.Request(request) => handleRequestSynchronized(gameId, position, request)
        case GameMessage.Payload.Result(result) => handleResultSynchronized(gameId, position, result)
      }

      override def onError(t: Throwable): Unit = ???

      override def onCompleted(): Unit = ???
    }
    server.subscribe(request, streamObserver)
    join.future
  }

  private def handleRequestSynchronized(gameId: String, position: Int, actionRequest: ActionRequest) = this.synchronized {
    handleRequest(gameId, position, actionRequest)
  }

  private def handleResultSynchronized(gameId: String, position: Int, actionResult: ActionResult) = this.synchronized {
    handleResult(gameId, position, actionResult)
  }

  protected def handleRequest(gameId: String, position: Int, actionRequest: ActionRequest): Unit

  protected def handleResult(gameId: String, position: Int, actionResult: ActionResult): Unit

}

case class SimplePlayerClient(name: String, strategy: Strategy, server: CatanServerStub, print: Boolean)(implicit ec: ExecutionContext) extends PlayerClient(name, strategy, server) {

  override protected def handleRequest(gameId: String, position: Int, actionRequest: ActionRequest): Unit = {
    if (print) println(s"$name: received request ${actionRequest.`type`}")
    strategy.getActionForRequest(actionRequest, position).foreach { actionResponse =>
      if (print) println(s"$name: sending response: $actionResponse")
      server.takeAction(TakeActionRequest(gameId, position, actionResponse)).foreach{ a => if (print) println(a)}
    }
  }

  override protected def handleResult(gameId: String, position: Int, actionResult: ActionResult): Unit =  {
    if (print) println(s"$name: received result $actionResult")

  }
}

