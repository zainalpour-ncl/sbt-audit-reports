package com.ncl.audit.cli

import zio.Task
import zio.ZIO

package object services {
  def withResource[A, B <: AutoCloseable](resource: => B)(use: B => Task[A]): Task[A] =
    ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(resource))(res => ZIO.attemptBlockingIO(res.close()).orDie)(use)
}
