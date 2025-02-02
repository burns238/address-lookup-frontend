package controllers

import itutil.IntegrationSpecBase
import itutil.config.IntegrationTestConstants.{journeyDataV2MinimalUkMode, testJourneyDataWithMinimalJourneyConfigV2, testJourneyId}
import play.api.http.HeaderNames
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json

class BeginJourneyISpec extends IntegrationSpecBase {

  "The begin journey endpoint" when {
    "in uk mode" should {
      "redirect to the lookup page" in {

        stubKeystore(testJourneyId, Json.toJson(journeyDataV2MinimalUkMode), OK)
        stubKeystoreSave(testJourneyId, Json.toJson(journeyDataV2MinimalUkMode), OK)

        val fResponse = buildClientLookupAddress(path = s"begin")
          .withHttpHeaders(HeaderNames.COOKIE -> sessionCookieWithCSRF, "Csrf-Token" -> "nocheck")
          .get()

        val res = await(fResponse)
        res.status shouldBe SEE_OTHER

        res.header(HeaderNames.LOCATION).get shouldBe "/lookup-address/Jid123/lookup"
      }
    }

    "in non-uk mode" should {
      "redirect to the country picker page" in {
        stubKeystore(testJourneyId, Json.toJson(testJourneyDataWithMinimalJourneyConfigV2), OK)
        stubKeystoreSave(testJourneyId, Json.toJson(testJourneyDataWithMinimalJourneyConfigV2), OK)

        val fResponse = buildClientLookupAddress(path = s"begin")
          .withHttpHeaders(HeaderNames.COOKIE -> sessionCookieWithCSRF, "Csrf-Token" -> "nocheck")
          .get()

        val res = await(fResponse)
        res.status shouldBe SEE_OTHER

        res.header(HeaderNames.LOCATION).get shouldBe "/lookup-address/Jid123/country-picker"
      }
    }
  }
}
