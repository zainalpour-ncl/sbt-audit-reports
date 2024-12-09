package com.ncl.audit

import fastparse.NoWhitespace._
import fastparse._
import org.slf4j.LoggerFactory

object RoutesParser {

  private val logger = LoggerFactory.getLogger(RoutesParser.getClass)

  def comment[_: P]: P[Unit] = P("#" ~ CharsWhile(_ != '\n', 0) ~ "\n")

  def whitespace[_: P]: P[Unit] = P(CharsWhileIn(" \t").?)

  def emptyLine[_: P]: P[Unit] = P(whitespace ~ "\n")

  def identifier[_: P]: P[String] =
    P(CharsWhile(c => c.isLetterOrDigit || c == '.' || c == '_').!)

  def params[_: P]: P[String] = P("(" ~ CharsWhile(_ != ')', 0).! ~ ")")

  // Mandatory HTTP methods
  def method[_: P]: P[String] = P(("GET" | "POST" | "PUT" | "DELETE" | "PATCH").!)

  def path[_: P]: P[String] = P(CharsWhile(_ != ' ', 0).!)

  def controller[_: P]: P[String] =
    P(identifier ~ ("." ~ identifier).rep).map { case (first, rest) => (first +: rest).mkString(".") }

  def handler[_: P]: P[String] = P("@" ~ controller | controller)

  // Route line now requires a method at the start
  def routeLine[_: P]: P[RestEndpoint] = P(
    method ~ whitespace ~ path ~ whitespace ~ handler ~ params.?
  ).map { case (m, p, c, i) =>
    RestEndpoint(m, p, c, i)
  }

  // GRPC lines starting with "->" should be ignored
  def grpcLine[_: P]: P[Unit] = P("->" ~ CharsWhile(_ != '\n', 0) ~ "\n")

  def parser[_: P]: P[Seq[RestEndpoint]] =
    P((grpcLine | comment | emptyLine | routeLine).rep.map(_.collect { case e: RestEndpoint => e }))

  def parseRoutes(input: String): Seq[RestEndpoint] =
    parse(input, parser(_)) match {
      case Parsed.Success(value, _) => value
      case f: Parsed.Failure =>
        logger.error(s"Failed to parse routes: ${f.msg}")
        Seq.empty
    }
}
