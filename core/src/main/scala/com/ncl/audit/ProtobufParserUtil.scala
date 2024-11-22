package com.ncl.audit

import com.ncl.parser.pf.generated.ProtobufLexer
import com.ncl.parser.pf.generated.ProtobufParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream

import scala.io.Source

// scalastyle:off
object ProtobufParserUtil {

  def parseContent(protobufContent: String): Seq[Service] = {
    // Setup ANTLR lexer and parser
    val lexer = new ProtobufLexer(CharStreams.fromString(protobufContent))
    val tokens = new CommonTokenStream(lexer)
    val parser = new ProtobufParser(tokens)

    // Parse the file and visit using the visitor
    val fileContext = parser.file()
    // println(fileContext.toStringTree(parser))
    val visitor = new ServiceExtractorVisitor
    visitor.visit(fileContext)
  }

  def parseFile(filePath: String): Seq[Service] = {
    val fileContent = Source.fromFile(filePath).mkString
    parseContent(fileContent).map(_.copy(definedIn = Some(filePath)))
  }
}
