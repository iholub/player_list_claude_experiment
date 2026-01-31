import cats.effect.IO
import cats.effect.Ref
import com.comcast.ip4s.*
import io.circe.*
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import java.util.UUID

import scala.concurrent.duration.*

class MainIntegrationSuite extends CatsEffectSuite:

  override val munitTimeout = 60.seconds

  private val server = ResourceSuiteLocalFixture(
    "server",
    for
      ref <- cats.effect.Resource.eval(Ref.of[IO, Map[UUID, PlayerList]](Map.empty))
      srv <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withShutdownTimeout(1.second)
        .withHttpApp(Main.app(ref))
        .build
    yield srv
  )

  private val client = ResourceSuiteLocalFixture(
    "client",
    EmberClientBuilder
      .default[IO]
      .withIdleConnectionTime(1.second)
      .build
  )

  override def munitFixtures = List(server, client)

  private def baseUri =
    val port = server().address.getPort
    Uri.unsafeFromString(s"http://localhost:$port")

  test("GET /hello returns 'hello' from a running server"):
    val uri = baseUri / "hello"
    given EntityDecoder[IO, String] = EntityDecoder.text[IO]
    client().expect[String](uri).map(body => assertEquals(body, "hello"))

  test("GET /nonexistent returns 404 from a running server"):
    val uri = baseUri / "nonexistent"
    client().status(Request[IO](uri = uri)).map { status =>
      assertEquals(status, Status.NotFound)
    }

  test("POST /player-lists creates a player list"):
    val uri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice", "Bob").asJson)
    val req = Request[IO](Method.POST, uri).withEntity(body)
    for
      resp <- client().run(req).use { resp =>
        resp.as[PlayerList].map(pl => (resp.status, pl))
      }
      (status, pl) = resp
    yield
      assertEquals(status, Status.Created)
      assertEquals(pl.name, "Team A")
      assertEquals(pl.players, List("Alice", "Bob"))

  test("GET /player-lists returns all player lists"):
    val uri = baseUri / "player-lists"
    for
      lists <- client().expect[List[PlayerList]](uri)
    yield assert(lists.nonEmpty)

  test("GET /player-lists/:id returns a player list"):
    val createUri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Fetch Me".asJson, "players" -> List("Zara").asJson)
    val createReq = Request[IO](Method.POST, createUri).withEntity(body)
    for
      created <- client().expect[PlayerList](createReq)
      getUri = baseUri / "player-lists" / created.id.toString
      fetched <- client().expect[PlayerList](getUri)
    yield
      assertEquals(fetched.id, created.id)
      assertEquals(fetched.name, "Fetch Me")

  test("GET /player-lists/:id returns 404 for unknown id"):
    val uri = baseUri / "player-lists" / UUID.randomUUID().toString
    client().status(Request[IO](uri = uri)).map { status =>
      assertEquals(status, Status.NotFound)
    }

  test("PUT /player-lists/:id updates a player list"):
    val createUri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Old Name".asJson, "players" -> List("X").asJson)
    val createReq = Request[IO](Method.POST, createUri).withEntity(body)
    val updateBody = Json.obj("name" -> "New Name".asJson, "players" -> List("Y", "Z").asJson)
    for
      created <- client().expect[PlayerList](createReq)
      updateUri = baseUri / "player-lists" / created.id.toString
      updateReq = Request[IO](Method.PUT, updateUri).withEntity(updateBody)
      updated <- client().expect[PlayerList](updateReq)
    yield
      assertEquals(updated.id, created.id)
      assertEquals(updated.name, "New Name")
      assertEquals(updated.players, List("Y", "Z"))

  test("PUT /player-lists/:id returns 404 for unknown id"):
    val uri = baseUri / "player-lists" / UUID.randomUUID().toString
    val body = Json.obj("name" -> "X".asJson, "players" -> List.empty[String].asJson)
    val req = Request[IO](Method.PUT, uri).withEntity(body)
    client().status(req).map { status =>
      assertEquals(status, Status.NotFound)
    }

  test("POST /player-lists/:id/players adds a player"):
    val createUri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Add Player".asJson, "players" -> List("Alice").asJson)
    val createReq = Request[IO](Method.POST, createUri).withEntity(body)
    val addBody = Json.obj("player" -> "Bob".asJson)
    for
      created <- client().expect[PlayerList](createReq)
      addUri = baseUri / "player-lists" / created.id.toString / "players"
      addReq = Request[IO](Method.POST, addUri).withEntity(addBody)
      updated <- client().expect[PlayerList](addReq)
    yield
      assertEquals(updated.id, created.id)
      assertEquals(updated.players, List("Alice", "Bob"))

  test("POST /player-lists/:id/players returns 404 for unknown id"):
    val uri = baseUri / "player-lists" / UUID.randomUUID().toString / "players"
    val body = Json.obj("player" -> "Bob".asJson)
    val req = Request[IO](Method.POST, uri).withEntity(body)
    client().status(req).map { status =>
      assertEquals(status, Status.NotFound)
    }

  test("POST /player-lists/:id/players/batch adds multiple players"):
    val createUri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Batch Add".asJson, "players" -> List("Alice").asJson)
    val createReq = Request[IO](Method.POST, createUri).withEntity(body)
    val batchBody = Json.obj("players" -> List("Bob", "Charlie").asJson)
    for
      created <- client().expect[PlayerList](createReq)
      batchUri = baseUri / "player-lists" / created.id.toString / "players" / "batch"
      batchReq = Request[IO](Method.POST, batchUri).withEntity(batchBody)
      updated <- client().expect[PlayerList](batchReq)
    yield
      assertEquals(updated.id, created.id)
      assertEquals(updated.players, List("Alice", "Bob", "Charlie"))

  test("POST /player-lists/:id/players/batch returns 404 for unknown id"):
    val uri = baseUri / "player-lists" / UUID.randomUUID().toString / "players" / "batch"
    val body = Json.obj("players" -> List("Bob").asJson)
    val req = Request[IO](Method.POST, uri).withEntity(body)
    client().status(req).map { status =>
      assertEquals(status, Status.NotFound)
    }

  test("DELETE /player-lists/:id/players removes a player"):
    val createUri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Remove Player".asJson, "players" -> List("Alice", "Bob", "Charlie").asJson)
    val createReq = Request[IO](Method.POST, createUri).withEntity(body)
    val removeBody = Json.obj("player" -> "Bob".asJson)
    for
      created <- client().expect[PlayerList](createReq)
      removeUri = baseUri / "player-lists" / created.id.toString / "players"
      removeReq = Request[IO](Method.DELETE, removeUri).withEntity(removeBody)
      updated <- client().expect[PlayerList](removeReq)
    yield
      assertEquals(updated.id, created.id)
      assertEquals(updated.players, List("Alice", "Charlie"))

  test("DELETE /player-lists/:id/players returns 404 for unknown id"):
    val uri = baseUri / "player-lists" / UUID.randomUUID().toString / "players"
    val body = Json.obj("player" -> "Bob".asJson)
    val req = Request[IO](Method.DELETE, uri).withEntity(body)
    client().status(req).map { status =>
      assertEquals(status, Status.NotFound)
    }

  test("DELETE /player-lists/:id/players/batch removes multiple players"):
    val createUri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Batch Remove".asJson, "players" -> List("Alice", "Bob", "Charlie", "Dave").asJson)
    val createReq = Request[IO](Method.POST, createUri).withEntity(body)
    val batchBody = Json.obj("players" -> List("Bob", "Dave").asJson)
    for
      created <- client().expect[PlayerList](createReq)
      batchUri = baseUri / "player-lists" / created.id.toString / "players" / "batch"
      batchReq = Request[IO](Method.DELETE, batchUri).withEntity(batchBody)
      updated <- client().expect[PlayerList](batchReq)
    yield
      assertEquals(updated.id, created.id)
      assertEquals(updated.players, List("Alice", "Charlie"))

  test("DELETE /player-lists/:id/players/batch returns 404 for unknown id"):
    val uri = baseUri / "player-lists" / UUID.randomUUID().toString / "players" / "batch"
    val body = Json.obj("players" -> List("Bob").asJson)
    val req = Request[IO](Method.DELETE, uri).withEntity(body)
    client().status(req).map { status =>
      assertEquals(status, Status.NotFound)
    }

  test("DELETE /player-lists/:id deletes a player list"):
    val createUri = baseUri / "player-lists"
    val body = Json.obj("name" -> "Delete Me".asJson, "players" -> List("A").asJson)
    val createReq = Request[IO](Method.POST, createUri).withEntity(body)
    for
      created <- client().expect[PlayerList](createReq)
      deleteUri = baseUri / "player-lists" / created.id.toString
      deleteReq = Request[IO](Method.DELETE, deleteUri)
      deleteStatus <- client().status(deleteReq)
      getStatus <- client().status(Request[IO](uri = deleteUri))
    yield
      assertEquals(deleteStatus, Status.NoContent)
      assertEquals(getStatus, Status.NotFound)

  test("DELETE /player-lists/:id returns 404 for unknown id"):
    val uri = baseUri / "player-lists" / UUID.randomUUID().toString
    val req = Request[IO](Method.DELETE, uri)
    client().status(req).map { status =>
      assertEquals(status, Status.NotFound)
    }
