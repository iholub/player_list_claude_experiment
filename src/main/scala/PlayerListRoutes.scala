import cats.effect.IO
import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import java.util.UUID

object PlayerListRoutes:

  private case class CreateRequest(name: String, players: List[String])
  private object CreateRequest:
    given Decoder[CreateRequest] = deriveDecoder[CreateRequest]

  private case class AddPlayerRequest(player: String)
  private object AddPlayerRequest:
    given Decoder[AddPlayerRequest] = deriveDecoder[AddPlayerRequest]

  private case class AddPlayersRequest(players: List[String])
  private object AddPlayersRequest:
    given Decoder[AddPlayersRequest] = deriveDecoder[AddPlayersRequest]

  def routes(service: PlayerListService): HttpRoutes[IO] =
    HttpRoutes.of[IO]:
      case req @ POST -> Root / "player-lists" =>
        for
          body <- req.as[CreateRequest]
          pl <- service.create(body.name, body.players)
          resp <- Created(pl)
        yield resp

      case GET -> Root / "player-lists" =>
        for
          all <- service.getAll
          resp <- Ok(all)
        yield resp

      case GET -> Root / "player-lists" / UUIDVar(id) =>
        for
          opt <- service.get(id)
          resp <- opt match
            case Some(pl) => Ok(pl)
            case None => NotFound()
        yield resp

      case req @ POST -> Root / "player-lists" / UUIDVar(id) / "players" =>
        for
          body <- req.as[AddPlayerRequest]
          opt <- service.addPlayer(id, body.player)
          resp <- opt match
            case Some(pl) => Ok(pl)
            case None => NotFound()
        yield resp

      case req @ POST -> Root / "player-lists" / UUIDVar(id) / "players" / "batch" =>
        for
          body <- req.as[AddPlayersRequest]
          opt <- service.addPlayers(id, body.players)
          resp <- opt match
            case Some(pl) => Ok(pl)
            case None => NotFound()
        yield resp

      case req @ DELETE -> Root / "player-lists" / UUIDVar(id) / "players" =>
        for
          body <- req.as[AddPlayerRequest]
          opt <- service.removePlayer(id, body.player)
          resp <- opt match
            case Some(pl) => Ok(pl)
            case None => NotFound()
        yield resp

      case req @ DELETE -> Root / "player-lists" / UUIDVar(id) / "players" / "batch" =>
        for
          body <- req.as[AddPlayersRequest]
          opt <- service.removePlayers(id, body.players)
          resp <- opt match
            case Some(pl) => Ok(pl)
            case None => NotFound()
        yield resp

      case req @ PUT -> Root / "player-lists" / UUIDVar(id) =>
        for
          body <- req.as[CreateRequest]
          opt <- service.update(id, body.name, body.players)
          resp <- opt match
            case Some(pl) => Ok(pl)
            case None => NotFound()
        yield resp

      case DELETE -> Root / "player-lists" / UUIDVar(id) =>
        for
          deleted <- service.delete(id)
          resp <- if deleted then NoContent() else NotFound()
        yield resp
