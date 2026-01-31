import java.util.UUID
import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.*

case class PlayerList(id: UUID, name: String, players: List[String])

object PlayerList:
  given Encoder[PlayerList] = deriveEncoder[PlayerList]
  given Decoder[PlayerList] = deriveDecoder[PlayerList]
