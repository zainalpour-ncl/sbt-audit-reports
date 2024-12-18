package com.ncl.audit.cli.services

import zio._
import zio.process.Command

final case class DependencyCheckResult(tool: String, isAvailable: Boolean)

final case class DependencyCheckError(missingDependencies: Seq[String]) extends Throwable {
  override def getMessage: String = s"Missing dependencies: ${missingDependencies.mkString(", ")}"
}

trait DependencyCheckerService {
  def checkDependencies(dependencies: Seq[String]): Task[Unit]
}

final case class DependencyCheckerServiceImpl() extends DependencyCheckerService {

  override def checkDependencies(dependencies: Seq[String]): Task[Unit] =
    for {
      results <- ZIO.foreach(dependencies)(checkSingleDependency)
      missing = results.collect { case DependencyCheckResult(tool, false) => tool }
      _ <- logResults(missing)
    } yield ()

  private def checkSingleDependency(tool: String): Task[DependencyCheckResult] =
    for {
      _ <- ZIO.logInfo(s"Checking availability of '$tool'...")
      isAvailable <- Command("which", tool).exitCode.map(_ == ExitCode.success).orElseSucceed(false)
      _ <- if (isAvailable) ZIO.logInfo(s"'$tool' is available.") else ZIO.logError(s"'$tool' is missing!")
    } yield DependencyCheckResult(tool, isAvailable)

  private def logResults(missing: Seq[String]): Task[Unit] =
    if (missing.isEmpty) ZIO.logInfo("All required dependencies are installed.")
    else ZIO.fail(DependencyCheckError(missing))
}

object DependencyCheckerService {
  val live: ULayer[DependencyCheckerService] =
    ZLayer.fromFunction(DependencyCheckerServiceImpl.apply _)

  def checkDependencies(dependencies: Seq[String]): RIO[DependencyCheckerService, Unit] =
    ZIO.serviceWithZIO[DependencyCheckerService](_.checkDependencies(dependencies))
}
