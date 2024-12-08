package com.ncl.audit

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class RoutesParserSpec extends AnyFunSpec with Matchers {

  describe("RoutesParser") {

    it("should parse REST endpoints from the provided routes.conf file") {
      val resourceStream = getClass.getResourceAsStream("/routes")
      resourceStream should not be null

      val content = scala.io.Source.fromInputStream(resourceStream).mkString
      val endpoints = RoutesParser.parseRoutes(content)

      endpoints should not be empty

      // Check a known endpoint
      val diagnosticBuildInfo = endpoints.find(_.path == "/diagnostic/build-info")
      diagnosticBuildInfo shouldBe defined
      diagnosticBuildInfo.get.method shouldBe "GET"
      diagnosticBuildInfo.get.controller shouldBe "com.ncl.common.play.DiagnosticController.buildInfo"

      // Check another endpoint
      val cancelGuest = endpoints.find(_.path == "/api/v0/vacations/cancel-guest")
      cancelGuest shouldBe defined
      cancelGuest.get.method shouldBe "POST"
      cancelGuest.get.controller shouldBe "com.ncl.vacation.controllers.VacationController.cancelGuest"

      // Check that gRPC lines are ignored (e.g., ReflectionRouter line)
      endpoints.exists(_.controller.contains("ReflectionRouter")) shouldBe false

      // Check another complex endpoint with parameters
      val listVacations = endpoints.find(_.path == "/api/v0/vacations/list")
      listVacations shouldBe defined
      listVacations.get.method shouldBe "GET"
      // The input parameters should be captured if present
      // (e.g., "(pageSize: Option[Int], pageIndex: Option[Int])")
      listVacations.get.inputParameters shouldBe Some("pageSize: Option[Int], pageIndex: Option[Int]")
    }

  }
}
