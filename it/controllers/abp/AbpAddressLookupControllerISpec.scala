package controllers.abp

import address.v2.Country
import controllers.api.{ConfirmedResponseAddress, ConfirmedResponseAddressDetails}
import itutil.IntegrationSpecBase
import itutil.config.IntegrationTestConstants._
import itutil.config.PageElementConstants._
import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.libs.json.{JsObject, Json}

class AbpAddressLookupControllerISpec extends IntegrationSpecBase {

  "The lookup page" should {
    "pre-pop the postcode and filter on the view when they are passed in as query parameters and drop selected address on load" in {
      stubKeystore(testJourneyId, testMinimalLevelJourneyConfigV2, OK)
      stubKeystoreSave(testJourneyId, testMinimalLevelJourneyConfigV2, OK)

      val fResponse = buildClientLookupAddress(path = "lookup?postcode=AB11+1AB&filter=bar")
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookieWithCSRF, "Csrf-Token" -> "nocheck")
        .get()
      val res = await(fResponse)

      res.status shouldBe OK
      testFormElementValuesMatch(res, Map(LookupPage.postcodeId -> testPostCode, LookupPage.filterId -> testFilterValue))
    }

    "pre-pop the postcode only on the view when it is passed in as a query parameters" in {
      stubKeystore(testJourneyId, testMinimalLevelJourneyConfigV2, OK)
      stubKeystoreSave(testJourneyId, testMinimalLevelJourneyConfigV2, OK)

      val fResponse = buildClientLookupAddress(path = "lookup?postcode=AB11 1AB")
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookieWithCSRF, "Csrf-Token" -> "nocheck")
        .get()
      val res = await(fResponse)

      res.status shouldBe OK
      testFormElementValuesMatch(res, Map(LookupPage.postcodeId -> testPostCode, LookupPage.filterId -> ""))
    }

    "pre-pop the filter only on the view when it is passed in as a query parameters" in {
      stubKeystore(testJourneyId, testMinimalLevelJourneyConfigV2, OK)
      stubKeystoreSave(testJourneyId, testMinimalLevelJourneyConfigV2, OK)

      val fResponse = buildClientLookupAddress(path = "lookup?filter=bar")
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookieWithCSRF, "Csrf-Token" -> "nocheck")
        .get()
      val res = await(fResponse)

      res.status shouldBe OK
      testFormElementValuesMatch(res, Map(LookupPage.postcodeId -> "", LookupPage.filterId -> testFilterValue))
    }

    "not pre-pop the filter or postcode fields when no query parameters are used " in {
      stubKeystore(testJourneyId, testMinimalLevelJourneyConfigV2, OK)
      stubKeystoreSave(testJourneyId, testMinimalLevelJourneyConfigV2, OK)

      val fResponse = buildClientLookupAddress(path = "lookup")
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookieWithCSRF, "Csrf-Token" -> "nocheck")
        .get()
      val res = await(fResponse)

      res.status shouldBe OK
      testFormElementValuesMatch(res, Map(LookupPage.postcodeId -> "", LookupPage.filterId -> ""))
    }
  }

  "confirmed" should {
    "return correct address with jid" in {
      val configWithConfirmedAddress = testJourneyDataWithMinimalJourneyConfigV2.copy(confirmedAddress = Some(testFullNonUKConfirmedAddress))
      stubKeystore(testJourneyId, Json.toJson(configWithConfirmedAddress).as[JsObject], OK)

      val fResponse = buildClientAPI("v2/confirmed?id=Jid123")
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookieWithCSRF, "Csrf-Token" -> "nocheck")
        .get()

      val res = await(fResponse)

      res.status shouldBe OK
      res.json shouldBe Json.toJson(ConfirmedResponseAddress(
        auditRef = testAuditRef,
        id = Some(testAddressIdRaw),
        address = ConfirmedResponseAddressDetails(None, Some(Seq(testAddressLine1, testAddressLine2, testAddressLine3, testAddressTown)), Some(testPostCode), Some(Country("FR", "France"))))
      )
    }
  }
}
