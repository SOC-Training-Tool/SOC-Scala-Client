package client

import java.util.concurrent.TimeUnit

import scala.concurrent.ExecutionContext.Implicits.global
import io.grpc.{CallOptions, ClientInterceptor, ManagedChannelBuilder}
import protos.soc.game.CatanServerGrpc.CatanServerStub
import protos.soc.game.{ActionRequest, CatanServerGrpc, CreateGameRequest, GetMovesRequest, StartGameRequest}
import protos.soc.moves.ActionSpecification

import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

object CatanClient {

  private val port = 50051
  private val host = "localhost"

  def main(args: Array[String]): Unit = {
    val client = new CatanClient()
    client.start()

  }

}



class CatanClient { self =>

  println("creating client")

  val channel = ManagedChannelBuilder.forAddress(CatanClient.host, CatanClient.port).usePlaintext(true).build
  val stub: CatanServerStub = CatanServerGrpc.stub(channel)

  //stub.build(channel, CallOptions.DEFAULT)

  val simunlations = 200

  implicit val random = new Random
  val strategy = new RandomStatelessStrategy(stub)

  val players = List(
    SimplePlayerClient("Ronnie", strategy, stub, false),
    SimplePlayerClient("Dani", strategy, stub, false),
    SimplePlayerClient("Greg", strategy, stub, false),
    SimplePlayerClient("Ariel", strategy, stub, false))

  private def start(): Unit = {
    println("creating games")

    for (i <- 1 to simunlations) {
      stub.createGame(CreateGameRequest()).flatMap { response =>
        val playersJoined = players.zipWithIndex.map { case (player, pos) =>
          player.joinGame(response.gameId, pos)
        }
        Future.sequence(playersJoined).map { _ =>
          response.gameId
        }
      } andThen {
        case Success(gameId) =>
          println(s"starting game: $gameId")
          stub.startGame(StartGameRequest(gameId))
        case Failure(exception) =>
          println(exception)
      }
      Thread.sleep(10)
    }

    while (true) {}
  }

}

class RandomStatelessStrategy(server: CatanServerStub)(implicit random: Random) extends Strategy {

  override def getActionForRequest(request: ActionRequest, position: Int): Future[ActionSpecification] = {
    val getMovesRequest = GetMovesRequest(position, request.publicGameState.get, request.inventory.get.inventory.`private`.get)
    server.getAllMoves(getMovesRequest).map { actions =>
      val n = random.nextInt(actions.actions.length)
      actions.actions.drop(n).head
    }
  }
}
