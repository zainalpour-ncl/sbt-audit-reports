package com.ncl.audit.cli.services

import com.ncl.audit._
import zio._

trait DependencyResolverService {
  def resolveDependencies(
    models: Seq[ProjectModel],
    allServices: Set[Service]
  ): Task[Seq[ProjectModel]]
}

final case class DependencyResolverServiceImpl() extends DependencyResolverService {

  override def resolveDependencies(
    models: Seq[ProjectModel],
    allServices: Set[Service]
  ): Task[Seq[ProjectModel]] =
    for {
      _ <- ZIO.logInfo("Building service-to-project mapping...")
      serviceToProject = buildServiceToProjectMapping(models)
      _ <- ZIO.logInfo(s"Mapping completed. Found ${serviceToProject.size} services.")

      resolvedModels <- ZIO.foreach(models)(model => resolveModelDependencies(model, serviceToProject, allServices))

      _ <- ZIO.logInfo("Dependency resolution completed.")
    } yield resolvedModels

  private def buildServiceToProjectMapping(models: Seq[ProjectModel]): Map[String, ProjectRef] =
    models.flatMap { model =>
      model.services.map(service => service.name -> ProjectRef(model.name, model.repository, model.product))
    }.toMap

  private def resolveModelDependencies(
    model: ProjectModel,
    serviceToProject: Map[String, ProjectRef],
    allServices: Set[Service]
  ): UIO[ProjectModel] = ZIO.succeed {
    val newDependencies = model.dependencies.flatMap { originalDep =>
      val enrichedCalls = originalDep.serviceCalls.map { sc =>
        val enrichedMethods =
          sc.calledMethods.map(methodCall => enrichMethodCall(sc.serviceName, methodCall, allServices))
        sc.copy(calledMethods = enrichedMethods)
      }

      val callsByProjectRef = enrichedCalls.groupBy(sc => serviceToProject.getOrElse(sc.serviceName, ProjectRef()))

      callsByProjectRef.map { case (projectRef, calls) =>
        originalDep.copy(serviceCalls = calls, project = projectRef)
      }
    }

    model.copy(dependencies = newDependencies)
  }

  private def enrichMethodCall(
    serviceName: String,
    methodCall: MethodCall,
    allServices: Set[Service]
  ): MethodCall =
    allServices
      .find(_.name == serviceName)
      .flatMap(_.methods.find(_.name.equalsIgnoreCase(methodCall.methodName)))
      .map { rpcMethod =>
        methodCall.copy(
          inputType = Some(rpcMethod.inputType),
          outputType = Some(rpcMethod.outputType)
        )
      }
      .getOrElse(methodCall)
}

object DependencyResolverService {
  val live: ULayer[DependencyResolverService] = ZLayer.fromFunction(DependencyResolverServiceImpl.apply _)

  def resolveDependencies(
    models: Seq[ProjectModel],
    allServices: Set[Service]
  ): RIO[DependencyResolverService, Seq[ProjectModel]] =
    ZIO.serviceWithZIO[DependencyResolverService](_.resolveDependencies(models, allServices))
}
