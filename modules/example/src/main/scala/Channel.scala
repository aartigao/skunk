// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import skunk._
import skunk.implicits._
import org.typelevel.otel4s.trace.Tracer

object Channel extends IOApp {

  implicit val tracer: Tracer[IO] = Tracer.noop

  val session: Resource[IO, Session[IO]] =
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .single

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      s.channel(id"foo")
       .listen(42)
       .take(3)
       .evalMap(n => IO(println(s"⭐️⭐  $n")))
       .compile
       .drain
    } as ExitCode.Success

}
