package com.ncl.audit

case class RpcMethod(name: String, inputType: String, outputType: String)
case class Service(name: String, methods: Set[RpcMethod], definedIn: Option[String] = None)
case class ProjectRef(name: String = "NA", repository: String = "NA")
case class ProjectDependency(serviceCalls: Set[ServiceCall], project: ProjectRef = ProjectRef())
case class ServiceCall(serviceName: String, calledMethods: Set[MethodCall], calledIn: Option[String] = None)
case class MethodCall(methodName: String, inputType: Option[String] = None, outputType: Option[String] = None)
case class SamlConfiguration(
  profileGroupsAttribute: String,
  roles: Map[String, Seq[String]],
  definedIn: String
)

case class RestEndpoint(
  method: String,
  path: String,
  controller: String,
  inputParameters: Option[String] = None
)

case class ProjectModel(
  name: String,
  repository: String,
  services: Set[Service],
  dependencies: Set[ProjectDependency],
  restEndpoints: Set[RestEndpoint] = Set.empty,
  samlConfigurations: Set[SamlConfiguration] = Set.empty
)
