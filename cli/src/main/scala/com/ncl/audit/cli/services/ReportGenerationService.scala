package com.ncl.audit.cli.services

import com.ncl.audit._
import com.ncl.audit.cli.model.JsonCodecs._
import zio._
import zio.json._

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

// scalastyle:off
trait ReportGenerationService {
  def generateReports(models: Seq[ProjectModel], outputFolder: File): Task[Unit]
}

final case class ReportGenerationServiceImpl() extends ReportGenerationService {

  override def generateReports(models: Seq[ProjectModel], outputFolder: File): Task[Unit] =
    for {
      _ <- ZIO.logInfo("Starting report generation...")
      _ <- ZIO.foreachParDiscard(models) { model =>
        val projectFolder = new File(outputFolder, s"${model.product}/${model.name}")
        ZIO.attemptBlockingIO(projectFolder.mkdirs()) *>
          generateReportsForModel(model, projectFolder)
      }
      _ <- ZIO.logInfo("All reports generated successfully.")
    } yield ()

  private def generateReportsForModel(model: ProjectModel, projectFolder: File): Task[Unit] =
    ZIO.collectAllParDiscard(
      Seq(
        writeToFile(new File(projectFolder, "model.json"), model.toJsonPretty),
        writeToFile(new File(projectFolder, "hosted_services-REST.csv"))(writer => writeRestEndpoints(writer, model)),
        writeToFile(new File(projectFolder, "hosted_services-gRPC.csv"))(writer => writeGrpcServices(writer, model)),
        writeToFile(new File(projectFolder, "dependent_services.csv"))(writer => writeDependentServices(writer, model)),
        writeToFile(new File(projectFolder, "permission-role_matrix.csv"))(writer =>
          writePermissionRoleMatrix(writer, model)
        )
      )
    )

  private def writeToFile(file: File, content: String): Task[Unit] =
    withResource(new PrintWriter(new FileWriter(file)))(writer => ZIO.attemptBlocking(writer.write(content))).catchAll(
      e => ZIO.logError(s"Failed to write file ${file.getAbsolutePath}: ${e.getMessage}")
    )

  private def writeToFile(file: File)(writeContent: PrintWriter => Unit): Task[Unit] =
    withResource(new PrintWriter(new FileWriter(file)))(writer => ZIO.attemptBlocking(writeContent(writer))).catchAll(
      e => ZIO.logError(s"Failed to write CSV file ${file.getAbsolutePath}: ${e.getMessage}")
    )

  private def writeRestEndpoints(writer: PrintWriter, model: ProjectModel): Unit = {
    writer.println("Method,Path,Controller,InputParameters")
    model.restEndpoints.foreach { endpoint =>
      writer.println(
        s"${endpoint.method},${endpoint.path},${endpoint.controller},${endpoint.inputParameters.getOrElse("")}"
      )
    }
  }

  private def writeGrpcServices(writer: PrintWriter, model: ProjectModel): Unit = {
    writer.println("Service,Method,InputType,OutputType,DefinedIn")
    model.services.foreach { svc =>
      svc.methods.foreach { method =>
        writer.println(
          s"${svc.name},${method.name},${method.inputType},${method.outputType},${svc.definedIn.getOrElse("")}"
        )
      }
    }
  }

  private def writeDependentServices(writer: PrintWriter, model: ProjectModel): Unit = {
    writer.println("ServiceName,MethodName,InputType,OutputType,CalledIn")
    model.dependencies.flatMap(_.serviceCalls).foreach { serviceCall =>
      serviceCall.calledMethods.foreach { method =>
        writer.println(s"${serviceCall.serviceName},${method.methodName},${method.inputType
            .getOrElse("")},${method.outputType.getOrElse("")},${serviceCall.calledIn.getOrElse("")}")
      }
    }
  }

  private def writePermissionRoleMatrix(writer: PrintWriter, model: ProjectModel): Unit = {
    writer.println("Role,Groups")
    model.samlConfigurations.foreach { saml =>
      saml.roles.foreach { case (role, groups) =>
        writer.println(s"$role,${groups.mkString(";")}")
      }
    }
  }
}

object ReportGenerationService {
  val live: ULayer[ReportGenerationService] = ZLayer.fromFunction(ReportGenerationServiceImpl.apply _)

  def generateReports(models: Seq[ProjectModel], outputFolder: File): RIO[ReportGenerationService, Unit] =
    ZIO.serviceWithZIO[ReportGenerationService](_.generateReports(models, outputFolder))
}
