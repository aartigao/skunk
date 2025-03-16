// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import org.typelevel.otel4s.trace.Tracer
import skunk._
import skunk.exception.StartupException

class RedshiftTest extends ffstest.FTest {

  object X86ArchOnly extends munit.Tag("X86ArchOnly")

  tracedTest("redshift - successfully connect".tag(X86ArchOnly)) { implicit tracer: Tracer[IO] =>
    Session.Builder[IO]
      .withPort(5439) // redshift port
      .withConnectionParameters(Session.DefaultConnectionParameters - "IntervalStyle")
      .single
      .use(_ => IO.unit)
  }

  tracedTest("redshift - cannot connect with default params".tag(X86ArchOnly)) { implicit tracer: Tracer[IO] =>
    Session.Builder[IO]
      .withPort(5439) // redshift port
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
  }
}
