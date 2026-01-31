import cats.effect.IO
import cats.effect.Ref
import io.circe.*
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import java.util.UUID

class MainSuite extends CatsEffectSuite:

  private def mkApp =
    Ref.of[IO, Map[UUID, PlayerList]](Map.empty).map(Main.app)

  test("GET /hello returns 200 with body 'hello'"):
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.GET, uri"/hello"))
      body <- response.as[String](implicitly, EntityDecoder.text[IO])
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(body, "hello")

  test("GET /nonexistent returns 404"):
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.GET, uri"/nonexistent"))
    yield assertEquals(response.status, Status.NotFound)

  test("POST /player-lists creates a new player list"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice", "Bob").asJson)
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      pl <- response.as[PlayerList]
    yield
      assertEquals(response.status, Status.Created)
      assertEquals(pl.name, "Team A")
      assertEquals(pl.players, List("Alice", "Bob"))

  test("GET /player-lists returns all player lists"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice").asJson)
    for
      app <- mkApp
      _ <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      _ <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      response <- app.run(Request[IO](Method.GET, uri"/player-lists"))
      lists <- response.as[List[PlayerList]]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(lists.length, 2)

  test("GET /player-lists/:id returns a player list by id"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice").asJson)
    for
      app <- mkApp
      createResp <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      created <- createResp.as[PlayerList]
      response <- app.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/player-lists/${created.id}")))
      pl <- response.as[PlayerList]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(pl.id, created.id)
      assertEquals(pl.name, "Team A")

  test("GET /player-lists/:id returns 404 for unknown id"):
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/player-lists/${UUID.randomUUID()}")))
    yield assertEquals(response.status, Status.NotFound)

  test("PUT /player-lists/:id updates a player list"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice").asJson)
    val updated = Json.obj("name" -> "Team B".asJson, "players" -> List("Charlie", "Dave").asJson)
    for
      app <- mkApp
      createResp <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      created <- createResp.as[PlayerList]
      response <- app.run(Request[IO](Method.PUT, Uri.unsafeFromString(s"/player-lists/${created.id}")).withEntity(updated))
      pl <- response.as[PlayerList]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(pl.id, created.id)
      assertEquals(pl.name, "Team B")
      assertEquals(pl.players, List("Charlie", "Dave"))

  test("PUT /player-lists/:id returns 404 for unknown id"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice").asJson)
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.PUT, Uri.unsafeFromString(s"/player-lists/${UUID.randomUUID()}")).withEntity(body))
    yield assertEquals(response.status, Status.NotFound)

  test("POST /player-lists/:id/players adds a player"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice").asJson)
    val addBody = Json.obj("player" -> "Bob".asJson)
    for
      app <- mkApp
      createResp <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      created <- createResp.as[PlayerList]
      response <- app.run(Request[IO](Method.POST, Uri.unsafeFromString(s"/player-lists/${created.id}/players")).withEntity(addBody))
      pl <- response.as[PlayerList]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(pl.id, created.id)
      assertEquals(pl.players, List("Alice", "Bob"))

  test("POST /player-lists/:id/players returns 404 for unknown id"):
    val addBody = Json.obj("player" -> "Bob".asJson)
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.POST, Uri.unsafeFromString(s"/player-lists/${UUID.randomUUID()}/players")).withEntity(addBody))
    yield assertEquals(response.status, Status.NotFound)

  test("POST /player-lists/:id/players/batch adds multiple players"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice").asJson)
    val batchBody = Json.obj("players" -> List("Bob", "Charlie").asJson)
    for
      app <- mkApp
      createResp <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      created <- createResp.as[PlayerList]
      response <- app.run(Request[IO](Method.POST, Uri.unsafeFromString(s"/player-lists/${created.id}/players/batch")).withEntity(batchBody))
      pl <- response.as[PlayerList]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(pl.id, created.id)
      assertEquals(pl.players, List("Alice", "Bob", "Charlie"))

  test("POST /player-lists/:id/players/batch returns 404 for unknown id"):
    val batchBody = Json.obj("players" -> List("Bob").asJson)
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.POST, Uri.unsafeFromString(s"/player-lists/${UUID.randomUUID()}/players/batch")).withEntity(batchBody))
    yield assertEquals(response.status, Status.NotFound)

  test("DELETE /player-lists/:id/players removes a player"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice", "Bob", "Charlie").asJson)
    val removeBody = Json.obj("player" -> "Bob".asJson)
    for
      app <- mkApp
      createResp <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      created <- createResp.as[PlayerList]
      response <- app.run(Request[IO](Method.DELETE, Uri.unsafeFromString(s"/player-lists/${created.id}/players")).withEntity(removeBody))
      pl <- response.as[PlayerList]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(pl.id, created.id)
      assertEquals(pl.players, List("Alice", "Charlie"))

  test("DELETE /player-lists/:id/players returns 404 for unknown id"):
    val removeBody = Json.obj("player" -> "Bob".asJson)
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.DELETE, Uri.unsafeFromString(s"/player-lists/${UUID.randomUUID()}/players")).withEntity(removeBody))
    yield assertEquals(response.status, Status.NotFound)

  test("DELETE /player-lists/:id/players/batch removes multiple players"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice", "Bob", "Charlie", "Dave").asJson)
    val batchBody = Json.obj("players" -> List("Bob", "Dave").asJson)
    for
      app <- mkApp
      createResp <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      created <- createResp.as[PlayerList]
      response <- app.run(Request[IO](Method.DELETE, Uri.unsafeFromString(s"/player-lists/${created.id}/players/batch")).withEntity(batchBody))
      pl <- response.as[PlayerList]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(pl.id, created.id)
      assertEquals(pl.players, List("Alice", "Charlie"))

  test("DELETE /player-lists/:id/players/batch returns 404 for unknown id"):
    val batchBody = Json.obj("players" -> List("Bob").asJson)
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.DELETE, Uri.unsafeFromString(s"/player-lists/${UUID.randomUUID()}/players/batch")).withEntity(batchBody))
    yield assertEquals(response.status, Status.NotFound)

  test("DELETE /player-lists/:id deletes a player list"):
    val body = Json.obj("name" -> "Team A".asJson, "players" -> List("Alice").asJson)
    for
      app <- mkApp
      createResp <- app.run(Request[IO](Method.POST, uri"/player-lists").withEntity(body))
      created <- createResp.as[PlayerList]
      deleteResp <- app.run(Request[IO](Method.DELETE, Uri.unsafeFromString(s"/player-lists/${created.id}")))
      getResp <- app.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/player-lists/${created.id}")))
    yield
      assertEquals(deleteResp.status, Status.NoContent)
      assertEquals(getResp.status, Status.NotFound)

  test("DELETE /player-lists/:id returns 404 for unknown id"):
    for
      app <- mkApp
      response <- app.run(Request[IO](Method.DELETE, Uri.unsafeFromString(s"/player-lists/${UUID.randomUUID()}")))
    yield assertEquals(response.status, Status.NotFound)
