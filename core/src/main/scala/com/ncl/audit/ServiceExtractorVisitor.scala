package com.ncl.audit

import com.ncl.parser.pf.generated.ProtobufParser._
import com.ncl.parser.pf.generated.ProtobufParserBaseVisitor

import scala.jdk.CollectionConverters._

// Visitor for parsing Protobuf content
class ServiceExtractorVisitor extends ProtobufParserBaseVisitor[Seq[Service]] {

  override def visitFile(ctx: FileContext): Seq[Service] =
    // Visit all file elements and collect services
    ctx.fileElement().asScala.flatMap(visit).toSeq

  override def visitServiceDecl(ctx: ServiceDeclContext): Seq[Service] = {
    val serviceName = ctx.serviceName().getText
    val methods = ctx.serviceElement().asScala.flatMap { element =>
      Option(element.methodDecl()).map(extractMethod)
    }
    Seq(Service(serviceName, methods.toSet))
  }

  override def visitFileElement(ctx: FileElementContext): Seq[Service] =
    if (ctx.serviceDecl() != null) visitServiceDecl(ctx.serviceDecl()) else Seq.empty

  private def extractMethod(ctx: MethodDeclContext): RpcMethod = {
    val methodName = ctx.methodName().getText
    val inputType = removeParentheses(ctx.inputType().getText)
    val outputType = removeParentheses(ctx.outputType().getText)
    RpcMethod(methodName, inputType, outputType)
  }

  private def removeParentheses(input: String): String =
    input.replaceAll("[()]", "")
}
