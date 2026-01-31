import cats.effect.IO
import cats.effect.Ref
import java.util.UUID

class PlayerListService(ref: Ref[IO, Map[UUID, PlayerList]]):

  def create(name: String, players: List[String]): IO[PlayerList] =
    for
      id <- IO(UUID.randomUUID())
      pl = PlayerList(id, name, players)
      _ <- ref.update(_ + (id -> pl))
    yield pl

  def get(id: UUID): IO[Option[PlayerList]] =
    ref.get.map(_.get(id))

  def getAll: IO[List[PlayerList]] =
    ref.get.map(_.values.toList)

  def update(id: UUID, name: String, players: List[String]): IO[Option[PlayerList]] =
    ref.modify { store =>
      store.get(id) match
        case Some(_) =>
          val updated = PlayerList(id, name, players)
          (store.updated(id, updated), Some(updated))
        case None =>
          (store, None)
    }

  def addPlayer(id: UUID, player: String): IO[Option[PlayerList]] =
    ref.modify { store =>
      store.get(id) match
        case Some(pl) =>
          val updated = pl.copy(players = pl.players :+ player)
          (store.updated(id, updated), Some(updated))
        case None =>
          (store, None)
    }

  def addPlayers(id: UUID, players: List[String]): IO[Option[PlayerList]] =
    ref.modify { store =>
      store.get(id) match
        case Some(pl) =>
          val updated = pl.copy(players = pl.players ++ players)
          (store.updated(id, updated), Some(updated))
        case None =>
          (store, None)
    }

  def removePlayer(id: UUID, player: String): IO[Option[PlayerList]] =
    ref.modify { store =>
      store.get(id) match
        case Some(pl) =>
          val updated = pl.copy(players = pl.players.filterNot(_ == player))
          (store.updated(id, updated), Some(updated))
        case None =>
          (store, None)
    }

  def removePlayers(id: UUID, players: List[String]): IO[Option[PlayerList]] =
    ref.modify { store =>
      store.get(id) match
        case Some(pl) =>
          val toRemove = players.toSet
          val updated = pl.copy(players = pl.players.filterNot(toRemove.contains))
          (store.updated(id, updated), Some(updated))
        case None =>
          (store, None)
    }

  def delete(id: UUID): IO[Boolean] =
    ref.modify { store =>
      if store.contains(id) then (store - id, true)
      else (store, false)
    }
