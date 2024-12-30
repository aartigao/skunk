// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats._
import cats.effect.Ref
import cats.syntax.all._
import skunk.util.StatementCache
import skunk.exception.PostgresErrorException
import skunk.net.message.{ Parse => ParseMessage, Close => _, _ }
import skunk.net.MessageSocket
import skunk.net.Protocol.StatementId
import skunk.Statement
import skunk.util.Namer
import skunk.util.Typer
import skunk.exception.{ UnknownTypeException, TooManyParametersException }
import natchez.Trace
import cats.data.OptionT

trait Parse[F[_]] {
  def apply[A](statement: Statement[A], ty: Typer): F[StatementId]
}

object Parse {

  def apply[F[_]: Exchange: MessageSocket: Namer: Trace](cache: Cache[F])(
    implicit ev: MonadError[F, Throwable]
  ): Parse[F] =
    new Parse[F] {
      override def apply[A](statement: Statement[A], ty: Typer): F[StatementId] =
        statement.encoder.oids(ty) match {

          case Right(os) if os.length > Short.MaxValue =>
            TooManyParametersException(statement).raiseError[F, StatementId]

          case Right(os) =>
            OptionT(cache.value.get(statement)).getOrElseF {
              exchange("parse") {
                for {
                  id <- nextName("statement").map(StatementId(_))
                  _  <- Trace[F].put(
                          "statement-name"            -> id.value,
                          "statement-sql"             -> statement.sql,
                          "statement-parameter-types" -> os.map(n => ty.typeForOid(n, -1).fold(n.toString)(_.toString)).mkString("[", ", ", "]")
                        )
                  _  <- send(ParseMessage(id.value, statement.sql, os))
                  _  <- send(Flush)
                  _  <- flatExpect {
                          case ParseComplete       => ().pure[F]
                          case ErrorResponse(info) => syncAndFail(statement, info)
                        }
                  _  <- cache.value.put(statement, id)
                } yield id
              }
            }

          case Left(err) =>
            UnknownTypeException(statement, err, ty.strategy).raiseError[F, StatementId]

        }

      def syncAndFail(statement: Statement[_], info: Map[Char, String]): F[Unit] =
        for {
          hi <- history(Int.MaxValue)
          _  <- send(Sync)
          _  <- expect { case ReadyForQuery(_) => }
          a  <- new PostgresErrorException(
                  sql       = statement.sql,
                  sqlOrigin = Some(statement.origin),
                  info      = info,
                  history   = hi,
                ).raiseError[F, Unit]
        } yield a

    }

  /** A cache for the `Parse` protocol. */
  final case class Cache[F[_]](value: StatementCache[F, StatementId]) {
    def mapK[G[_]](fk: F ~> G): Cache[G] =
      Cache(value.mapK(fk))
  }

  object Cache {
    def empty[F[_]: Functor: Ref.Make](capacity: Int): F[Cache[F]] =
      StatementCache.empty[F, StatementId](capacity).map(Parse.Cache(_))
  }

}
