package com.ncl.audit

import com.ncl.parser.pf.generated.ProtobufParser._
import com.ncl.parser.pf.generated.ProtobufParserBaseVisitor

import scala.jdk.CollectionConverters._

// Case classes to represent extracted services and RPC methods
case class RpcMethod(name: String, inputType: String, outputType: String)
case class Service(name: String, methods: Seq[RpcMethod])

// Visitor for parsing Protobuf content
class ServiceExtractorVisitor extends ProtobufParserBaseVisitor[Seq[Service]] {

  override def visitFile(ctx: FileContext): Seq[Service] = {
    // Visit all file elements and collect services
    ctx.fileElement().asScala.flatMap(visit)
  }

  override def visitServiceDecl(ctx: ServiceDeclContext): Seq[Service] = {
    val serviceName = ctx.serviceName().getText
    val methods = ctx.serviceElement().asScala.flatMap { element =>
      Option(element.methodDecl()).map(extractMethod)
    }
    Seq(Service(serviceName, methods))
  }

  override def visitFileElement(ctx: FileElementContext): Seq[Service] = {
    if (ctx.serviceDecl() != null) visitServiceDecl(ctx.serviceDecl()) else Seq.empty
  }

  private def extractMethod(ctx: MethodDeclContext): RpcMethod = {
    val methodName = ctx.methodName().getText
    val inputType = ctx.inputType().getText
    val outputType = ctx.outputType().getText
    RpcMethod(methodName, inputType, outputType)
  }
}
