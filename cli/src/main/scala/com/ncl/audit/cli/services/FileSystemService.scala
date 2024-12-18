package com.ncl.audit.cli.services

import zio._

import java.io.File
import java.nio.file._
import scala.jdk.StreamConverters._

trait FileSystemService {
  def findFilesWithExtension(baseDir: File, extension: String): Task[List[File]]
  def findRoutesConfFiles(baseDir: File): Task[List[File]]
  def findSamlConfFiles(baseDir: File): Task[List[File]]
}

final case class FileSystemServiceImpl() extends FileSystemService {

  override def findFilesWithExtension(baseDir: File, extension: String): Task[List[File]] =
    traverseFileTree(baseDir) { file =>
      file.getFileName.toString.endsWith(s".$extension")
    }

  override def findRoutesConfFiles(baseDir: File): Task[List[File]] =
    traverseFileTree(baseDir) { file =>
      val name = file.getFileName.toString
      (name == "routes" || name == "routes.conf") && !file.toString.contains("test")
    }

  override def findSamlConfFiles(baseDir: File): Task[List[File]] =
    traverseFileTree(baseDir) { file =>
      val name = file.getFileName.toString
      val pathString = file.toString
      val isValidFile = name.endsWith(".conf") &&
        !Set("test", "nginx", "node.conf", "Makefile.conf", "users.conf", "groups.conf", "devops")
          .exists(pathString.contains)

      isValidFile
    }

  private def traverseFileTree(baseDir: File)(filter: Path => Boolean): Task[List[File]] =
    withResource(Files.walk(baseDir.toPath)) { stream =>
      ZIO.attemptBlocking {
        stream.toScala(List).filter(filter).map(_.toFile)
      }
    }
}

object FileSystemService {
  val live: ULayer[FileSystemService] = ZLayer.fromFunction(FileSystemServiceImpl.apply _)

  def findFilesWithExtension(baseDir: File, extension: String): RIO[FileSystemService, List[File]] =
    ZIO.serviceWithZIO[FileSystemService](_.findFilesWithExtension(baseDir, extension))

  def findRoutesConfFiles(baseDir: File): RIO[FileSystemService, List[File]] =
    ZIO.serviceWithZIO[FileSystemService](_.findRoutesConfFiles(baseDir))

  def findSamlConfFiles(baseDir: File): RIO[FileSystemService, List[File]] =
    ZIO.serviceWithZIO[FileSystemService](_.findSamlConfFiles(baseDir))
}
