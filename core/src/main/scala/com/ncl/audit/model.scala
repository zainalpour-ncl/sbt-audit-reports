package com.ncl.audit

case class RpcMethod(name: String, inputType: String, outputType: String)
case class Service(name: String, methods: Set[RpcMethod], definedIn: Option[String] = None)
// TODO: Add support for project dependencies
// case class ProjectDependency(project: ProjectRef, serviceCalls: Set[ServiceCall])
case class ProjectDependency(serviceCalls: Set[ServiceCall])
case class ServiceCall(serviceName: String, calledMethods: Set[MethodCall])
case class MethodCall(methodName: String, inputType: Option[String] = None, outputType: Option[String] = None)
// TODO: Add support for project dependencies
// case class ProjectRef(name: String, repository: String)
case class ProjectModel(name: String, repository: String, services: Set[Service], dependencies: Set[ProjectDependency])
