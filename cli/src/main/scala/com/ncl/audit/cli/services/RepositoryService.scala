package com.ncl.audit.cli.services

import com.ncl.audit.cli.model.AppConfig
import zio._
import zio.process.Command

import java.io.File

trait RepositoryService {
  def cloneRepositories(appConfig: AppConfig, baseFolder: File): Task[Unit]
}

sealed trait RepositoryError extends Throwable
case class FolderCreationError(message: String) extends RepositoryError
case class CloneError(repoName: String, cloneUrl: String, exitCode: ExitCode) extends RepositoryError

final case class RepositoryServiceImpl() extends RepositoryService {

  override def cloneRepositories(appConfig: AppConfig, baseFolder: File): Task[Unit] =
    for {
      _ <- ZIO.logInfo(s"Starting to clone repositories into ${baseFolder.getAbsolutePath}...")
      _ <- createBaseFolder(baseFolder)

      _ <- ZIO.foreachParDiscard(appConfig.products) { product =>
        ZIO.foreachPar(product.projects) { project =>
          cloneRepositoryIfNecessary(project, product.product, baseFolder)
            .retry(Schedule.recurs(2) && Schedule.exponential(100.milliseconds))
            .tapError(e => ZIO.logError(s"Failed to clone repository '$project': ${e.getMessage}"))
        }
      }
      _ <- ZIO.logInfo("Repository cloning complete.")
    } yield ()

  private def createBaseFolder(baseFolder: File): Task[Unit] =
    ZIO
      .attemptBlockingIO(baseFolder.mkdirs())
      .flatMap {
        case true  => ZIO.logInfo(s"Base folder ${baseFolder.getAbsolutePath} is ready.")
        case false => ZIO.fail(FolderCreationError(s"Failed to create base folder: ${baseFolder.getAbsolutePath}"))
      }
      .tapError(err => ZIO.logError(s"Error during base folder creation: ${err.getMessage}"))
      .unit

  private def cloneRepositoryIfNecessary(
    repoName: String,
    product: String,
    baseFolder: File
  ): Task[Unit] = {
    val repoPath = new File(baseFolder, repoName)

    if (repoPath.exists()) {
      ZIO.logInfo(s"Skipping '$repoName' - already exists.")
    } else {
      val cloneUrl = s"https://github.com/$product/$repoName.git"
      val cloneCommand = Command("gh", "repo", "clone", cloneUrl, repoPath.getAbsolutePath)

      for {
        _ <- ZIO.logInfo(s"Cloning repository '$repoName'...")
        exitCode <- cloneCommand.exitCode
        _ <- handleCloneResult(exitCode, repoName, cloneUrl)
      } yield ()
    }
  }

  private def handleCloneResult(exitCode: ExitCode, repoName: String, cloneUrl: String): Task[Unit] =
    exitCode match {
      case ExitCode.success =>
        ZIO.logInfo(s"Successfully cloned '$repoName'.")
      case _ =>
        ZIO.fail(CloneError(repoName, cloneUrl, exitCode))
    }
}

object RepositoryService {
  val live: ULayer[RepositoryService] = ZLayer.fromFunction(RepositoryServiceImpl.apply _)

  def cloneRepositories(appConfig: AppConfig, baseFolder: File): RIO[RepositoryService, Unit] =
    ZIO.serviceWithZIO[RepositoryService](_.cloneRepositories(appConfig, baseFolder))
}
