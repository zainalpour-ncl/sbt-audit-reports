package com.ncl.audit.cli.services

import com.ncl.audit._
import zio._
import scala.util.matching.Regex
import java.io.File
import scala.io.Source

trait ProtobufService {
  def parseProtoFiles(protoFiles: List[File]): Task[Set[Service]]
}

final case class ProtobufServiceImpl() extends ProtobufService {

  private val servicePattern: Regex = """service\s+(\w+)\s*\{""".r
  private val rpcPattern: Regex = """rpc\s+(\w+)\s*\(\s*(\w+)\s*\)\s+returns\s*\(\s*(\w+)\s*\)\s*;""".r

  override def parseProtoFiles(protoFiles: List[File]): Task[Set[Service]] =
    ZIO.foreach(protoFiles)(parseSingleProtoFile).map(_.flatten.toSet)

  private def parseSingleProtoFile(file: File): Task[Set[Service]] =
    withResource(Source.fromFile(file)) { source =>
      ZIO.attemptBlocking {
        val lines = source.mkString
        parseServices(lines, file.getAbsolutePath)
      }
    }.mapError(e => new RuntimeException(s"Failed to parse proto file: ${file.getAbsolutePath}", e))

  private def parseServices(content: String, filePath: String): Set[Service] =
    servicePattern
      .findAllMatchIn(content)
      .map { serviceMatch =>
        val serviceName = serviceMatch.group(1)
        val methods = rpcPattern
          .findAllMatchIn(content)
          .map { rpcMatch =>
            RpcMethod(
              name = rpcMatch.group(1),
              inputType = rpcMatch.group(2),
              outputType = rpcMatch.group(3)
            )
          }
          .toSet
        Service(name = serviceName, methods = methods, definedIn = Some(filePath))
      }
      .toSet
}

object ProtobufService {
  val live: ULayer[ProtobufService] = ZLayer.fromFunction(ProtobufServiceImpl.apply _)

  def parseProtoFiles(protoFiles: List[File]): RIO[ProtobufService, Set[Service]] =
    ZIO.serviceWithZIO[ProtobufService](_.parseProtoFiles(protoFiles))
}
