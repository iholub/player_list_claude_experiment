import cats.effect.*
import cats.syntax.semigroupk.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import java.util.UUID

object Main extends IOApp.Simple:

  val helloRoutes: HttpRoutes[IO] = HttpRoutes.of[IO]:
    case GET -> Root / "hello" => Ok("hello")

  def app(ref: Ref[IO, Map[UUID, PlayerList]]): HttpApp[IO] =
    val service = PlayerListService(ref)
    val playerListRoutes = PlayerListRoutes.routes(service)
    (helloRoutes <+> playerListRoutes).orNotFound

  val run: IO[Unit] =
    for
      ref <- Ref.of[IO, Map[UUID, PlayerList]](Map.empty)
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(app(ref))
        .build
        .useForever
    yield ()
