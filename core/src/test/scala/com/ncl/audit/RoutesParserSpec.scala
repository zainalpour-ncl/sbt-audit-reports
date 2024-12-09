package com.ncl.audit

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

// scalastyle:off
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

      // Check a complex endpoint with parameters
      val listVacations = endpoints.find(_.path == "/api/v0/vacations/list")
      listVacations shouldBe defined
      listVacations.get.method shouldBe "GET"
      listVacations.get.inputParameters shouldBe Some("pageSize: Option[Int], pageIndex: Option[Int]")

      // Check an endpoint with empty parentheses
      val vacationHistoryLogs = endpoints.find(_.path == "/api/v0/vacations/history")
      vacationHistoryLogs shouldBe defined
      vacationHistoryLogs.get.method shouldBe "GET"
      vacationHistoryLogs.get.inputParameters shouldBe Some("")

      // Check an endpoint with one parameter
      val getPromotion = endpoints.find(_.path == "/api/v0/promotions/:promoCode")
      getPromotion shouldBe defined
      getPromotion.get.method shouldBe "GET"
      getPromotion.get.inputParameters shouldBe Some("promoCode")

      // Check an endpoint with multiple parameters
      val backfillVacation = endpoints.find(_.path == "/api/v0/vacation/backfill/:vacationDisplayId")
      backfillVacation shouldBe defined
      backfillVacation.get.method shouldBe "POST"
      backfillVacation.get.inputParameters shouldBe Some("vacationDisplayId: String")

      // Check an endpoint with optional parameters
      val syncStateroom = endpoints.find(_.path == "/api/v0/experiences/sync-stateroom-configurations")
      syncStateroom shouldBe defined
      syncStateroom.get.method shouldBe "POST"
      syncStateroom.get.inputParameters shouldBe Some("syncSailingStateroomExperienceComponents: Option[Boolean], syncPricingInventory: Option[Boolean]")
    }
  }
}
