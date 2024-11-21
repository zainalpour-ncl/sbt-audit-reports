package com.ncl.audit

import fastparse.NoWhitespace._
import fastparse._

case class Endpoint(
  method: Option[String],
  path: String,
  controller: String,
  inputParameters: Option[String] = None
)

object RoutesParser {
  def comment[_: P]: P[Unit] = P("#" ~ CharsWhile(_ != '\n', 0) ~ "\n")

  def whitespace[_: P]: P[Unit] = P(CharsWhileIn(" \t").?)

  def emptyLine[_: P]: P[Unit] = P(whitespace ~ "\n")

  def identifier[_: P]: P[String] = P(CharsWhile(c => c.isLetterOrDigit || c == '.' || c == '_').!)

  def params[_: P]: P[String] = P("(" ~ CharsWhile(_ != ')').! ~ ")")

  def method[_: P]: P[String] = P(("GET" | "POST" | "PUT" | "DELETE" | "PATCH").!)

  def path[_: P]: P[String] = P(CharsWhile(_ != ' ', 0).!)

  def controller[_: P]: P[String] = P(identifier ~ ("." ~ identifier).rep).map { case (first, rest) =>
    (first +: rest).mkString(".")
  }

  def handler[_: P]: P[String] = P("@" ~ controller | controller)

  def routeLine[_: P]: P[Endpoint] = P(
    (method.? ~ whitespace ~ path ~ whitespace ~ handler ~ params.?).map { case (m, p, c, i) =>
      Endpoint(m, p, c, i)
    }
  )

  def parser[_: P]: P[Seq[Endpoint]] = P((comment | emptyLine | routeLine).rep.map(_.collect { case e: Endpoint =>
    e
  }))

  def parseRoutes(input: String): Seq[Endpoint] =
    parse(input, parser(_)) match {
      case Parsed.Success(value, _) => value
      case f: Parsed.Failure =>
        println(s"Parsing failed: $f")
        Seq.empty
    }
}
