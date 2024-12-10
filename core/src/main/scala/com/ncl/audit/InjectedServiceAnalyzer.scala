package com.ncl.audit

import scala.meta._

// scalastyle:off
object InjectedServiceAnalyzer {

  implicit val dialect: Dialect = scala.meta.dialects.Scala213

  def analyzeServiceCalls(source: String, fileName: String): Set[ServiceCall] = {
    val parsed = source.parse[Source] match {
      case Parsed.Success(tree) => tree
      case Parsed.Error(pos, message, _) =>
        throw new IllegalArgumentException(s"Failed to parse $fileName: $message at $pos")
    }

    // Step 1: Extract injected services with type-based service name inference
    val injectedServices = parsed
      .collect { case cls: Defn.Class =>
        cls.ctor.paramss.flatten.collect {
          case param: Term.Param if param.decltpe.exists {
            case tpe: Type.Name   => tpe.value.endsWith("Client")
            case tpe: Type.Select => tpe.name.value.endsWith("Client")
            case _                => false
          } =>
            val paramName = param.name.value
            val serviceName = param.decltpe
              .map {
                case tpe: Type.Name   => tpe.value.stripSuffix("Client")
                case tpe: Type.Select => tpe.name.value.stripSuffix("Client")
                case _                => "UnknownService"
              }
              .getOrElse("UnknownService")
            paramName -> serviceName
        }
      }
      .flatten
      .toMap

    // Step 2: Find wrapped services
    val wrappedServices = parsed.collect {
      case Defn.Val(_, List(Pat.Var(Term.Name(wrappedName))), _, Term.Apply(_, List(Term.Name(originalName))))
      if injectedServices.contains(originalName) =>
        wrappedName -> injectedServices(originalName)
    }.toMap

    val allServices = injectedServices ++ wrappedServices

    // Step 3: Analyze method calls in the class
    val serviceCalls = parsed
      .collect { case cls: Defn.Class =>
        cls.templ.stats.collect { case method: Defn.Def =>
          analyzeMethodCalls(method, allServices)
        }
      }
      .flatten
      .flatten

    // Group by service name and aggregate methods
    serviceCalls
      .groupBy(_.serviceName)
      .map { case (serviceName, calls) =>
        ServiceCall(serviceName, calls.flatMap(_.calledMethods).toSet, calledIn = Some(fileName))
      }
      .toSet
  }

  private def analyzeMethodCalls(
    method: Defn.Def,
    services: Map[String, String]
  ): Set[ServiceCall] =
    method.collect {
      case Term.Select(Term.Name(variableName), Term.Name(methodName)) if services.contains(variableName) =>
        val serviceName = services(variableName)
        ServiceCall(serviceName, Set(MethodCall(methodName)))
    }.toSet
}
