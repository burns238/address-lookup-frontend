/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import config.FrontendAppConfig
import model.ProposedAddress
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.address.v2._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.util.Random

class AddressLookupAddressServiceSpec extends PlaySpec with GuiceOneAppPerSuite with MockitoSugar with ScalaFutures {

  class Scenario(resp: List[AddressRecord]) {
    private implicit val ec = play.api.libs.concurrent.Execution.Implicits.defaultContext
    implicit val hc = HeaderCarrier()
    val end = "http://localhost:42"

    val httpClient = mock[HttpClient]
    val frontendAppConfig = app.injector.instanceOf[FrontendAppConfig]

    when(httpClient.GET[List[AddressRecord]](any(), any())(any(), any(), any())).thenReturn(Future.successful(resp))

    val service = new AddressLookupAddressService(frontendAppConfig, httpClient)(ec) {
      override val endpoint = end
    }
  }

  "find" should {

    "find addresses by postcode & isukMode == false" in new Scenario(oneAddress) {
      service.find("ZZ11 1ZZ", isukMode = false).futureValue must be(toProposals(oneAddress))
    }

    "map UK to GB & isukMode == false" in new Scenario(List(addr(Some("UK")))) {
      service.find("ZZ11 1ZZ", isukMode = false).futureValue.head.country.code must be("GB")
    }

    "return multiple addresses with diverse country codes when isukMode == false" in new Scenario(
      manyAddresses(0)(Some("foo")) ::: manyAddresses(1)(Some("UK"))) {
      service.find("ZZ11 1ZZ", isukMode = false).futureValue.map(a => a.country.code) mustBe Seq("foo", "GB", "GB")
    }

    "return no addresses where ukMode == true and all addresses are non UK addresses" in new Scenario(
      manyAddresses(2)(Some("foobar"))) {
      service.find("ZZ11 1ZZ", isukMode = true).futureValue.headOption must be(None)
    }

    "return 2 addresses where ukMode == true and 2 out of 3 addresses are UK" in new Scenario(
      manyAddresses(0)(Some("foo")) ::: manyAddresses(1)(Some("UK"))) {

      service.find("ZZ11 1ZZ", isukMode = true).futureValue.map(a => a.country.code) mustBe Seq("GB", "GB")
    }

    "sort the addresses intelligently based on street/flat numbers as well as string comparisons" in new Scenario(cannedAddresses) {
      val listOfLines = service.find("ZZ11 1ZZ", isukMode = true).futureValue.map(pa => pa.lines.mkString(" "))

      listOfLines mustBe Seq(
        "1 Malvern Court", "Flat 2a stuff 4 Malvern Court", "3b Malvern Court", "3c Malvern Court")
    }
  }

  private val cannedAddresses = List(
    cannedAddress(List("3c", "Malvern Court"), "ZZ11 1ZZ"),
    cannedAddress(List("Flat 2a stuff 4", "Malvern Court"), "ZZ11 1ZZ"),
    cannedAddress(List("3b", "Malvern Court"), "ZZ11 1ZZ"),
    cannedAddress(List("1", "Malvern Court"), "ZZ11 1ZZ"))

  private val manyAddresses = (numberOfAddresses: Int) =>
    (code: Option[String]) => someAddresses(numberOfAddresses, addr(code))

  private val oneAddress = someAddresses(1, addr(Some("GB")))

  private def someAddresses(num: Int = 1, addr: AddressRecord): List[AddressRecord] = {
    (0 to num).map { i =>
      addr
    }.toList
  }

  private def cannedAddress(lines: List[String], postCode: String) =
    AddressRecord("1234", Some(Random.nextLong()), Address(lines, None, None, postCode, Some(Countries.England),
      Country("GB", rndstr(32))), "en", Some(LocalCustodian(123, "Tyne & Wear")), None, None, None, None)

  private def addr(code: Option[String]): AddressRecord = {
    AddressRecord(
      rndstr(16),
      Some(Random.nextLong()),
      Address(
        List(rndstr(16), rndstr(16), rndstr(8)),
        Some(rndstr(16)),
        Some(rndstr(8)),
        rndstr(8),
        Some(Countries.England),
        Country(code.get, rndstr(32))
      ),
      "en",
      Some(LocalCustodian(123, "Tyne & Wear")), None, None, None, None
    )
  }

  private def rndstr(i: Int): String = Random.alphanumeric.take(i).mkString

  private def toProposals(found: List[AddressRecord]): Seq[ProposedAddress] = {
    found.map { addr =>
      ProposedAddress(
        addr.id,
        addr.address.postcode,
        addr.address.lines,
        addr.address.town,
        addr.address.county,
        addr.address.country
      )
    }
  }

}
