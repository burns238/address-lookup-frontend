/*
 * Copyright 2023 HM Revenue & Customs
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

package forms

import com.codahale.metrics.SharedMetricRegistries
import model.Edit
import org.scalatest.{MustMatchers, WordSpec}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.data.validation.{Invalid, Valid}
import play.api.i18n.{Lang, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder

class ALFFormsSpec extends WordSpec with MustMatchers with GuiceOneAppPerSuite {
  override implicit lazy val app: Application = {
    SharedMetricRegistries.clear()
    new GuiceApplicationBuilder().build()
  }

  val messagesApi = app.injector.instanceOf[MessagesApi]
  implicit val messages = messagesApi.preferred(Seq(Lang("en")))

  val editFormNonuk = ALFForms.nonUkEditForm()
  val editFormNonukWelsh = ALFForms.nonUkEditForm()
  val editFormUk = ALFForms.ukEditForm()
  val editFormUkWelsh = ALFForms.ukEditForm()

  val chars257 = List.fill(257)("A").reduce(_ + _)
  val chars256 = List.fill(256)("A").reduce(_ + _)
  val chars255 = List.fill(255)("A").reduce(_ + _)

  "ukEditForm" should {
    "return no errors with valid data" in {
      val data = Map(
        "organisation" -> "some-organisation",
        "line1" -> "foo1",
        "line2" -> "foo2",
        "line3" -> "foo3",
        "town" -> "twn",
        "postcode" -> "ZZ1 1ZZ",
        "countryCode" -> "GB")

      editFormUk.bind(data).hasErrors mustBe false
    }
    "retrieve organisation when present" in {
      val data = Map(
        "organisation" -> "some-organisation",
        "line1" -> "foo1",
        "line2" -> "foo2",
        "line3" -> "foo3",
        "town" -> "twn",
        "postcode" -> "ZZ1 1ZZ",
        "countryCode" -> "GB")

      editFormUk.bind(data).get.organisation mustBe Some("some-organisation")
    }
    "not retrieve organisation when it is not present" in {
      val data = Map(
        "line1" -> "foo1",
        "line2" -> "foo2",
        "line3" -> "foo3",
        "town" -> "twn",
        "postcode" -> "ZZ1 1ZZ",
        "countryCode" -> "GB")

      editFormUk.bind(data).get.organisation mustBe None
    }
    "should default country code if country code is different to GB but all data is valid" in {
      val data = Map(
        "line1" -> "foo1",
        "line2" -> "foo2",
        "line3" -> "foo3",
        "town" -> "twn",
        "postcode" -> "AA199ZZ",
        "countryCode" -> "FR")

      editFormUk.bind(data).get.countryCode mustBe "GB"
    }
    "should default country code if country code is not provided and all data is valid" in {
      val data = Map(
        "line1" -> "foo1",
        "line2" -> "foo2",
        "line3" -> "foo3",
        "town" -> "twn",
        "postcode" -> "AA199ZZ")

      editFormUk.bind(data).get.countryCode mustBe "GB"
    }
    "should return error if line 3 > 255 chars" in {
      val data = Map(
        "line1" -> "foo",
        "line2" -> "foo2",
        "line3" -> chars256,
        "town" -> "twn",
        "postcode" -> "AA199ZZ",
        "countryCode" -> "GB")

      editFormUk.bind(data).hasErrors mustBe true
    }

    "isvalidPostCode should accept international address with no postcode because country is defaulted to GB and postcode is optional" in {
      ALFForms.isValidPostcode(editFormUk.fill(Edit(None, None, None, None, None, "", "FR"))).hasErrors mustBe false
    }
    Seq(("case 1", "MN 99555"),
      ("case 2","A"),
      ("case 3","1"),
      ("case 4","999999999999"),
      ("case 5","ABC123XYZ123"),
      ("case 6", "SW778 2BH")).foreach{
      case (caseNum,postcode) =>
        s"$editFormUk NOT accept international address with invalid postcodes ($caseNum) because country code is defaulted to GB" in {
          ALFForms.isValidPostcode(editFormUk.fill(Edit(None, None, None, None, None, postcode, "FR"))).hasErrors mustBe true
        }
    }
    "isvalidPostCode should  accept international address with valid postcode because country is defaulted to GB" in {
      ALFForms.isValidPostcode(editFormUk.fill(Edit(None, None, None, None, None, "ZZ1 1ZZ", "FR"))).hasErrors mustBe false
    }
  }

  "nonUkEditForm" should {
    "return no errors with valid data with invalid postcode provided because country is not GB" in {
      val data = Map(
        "line1" -> "foo1",
        "line2" -> "foo2",
        "town" -> "twn",
        "postcode" -> "fudgebarwizz123",
        "countryCode" -> "FR")

      editFormNonuk.bind(data).hasErrors mustBe false
    }
    "return errors with valid data that does not have country" in {
      val data = Map(
        "line1" -> "foo1",
        "line2" -> "foo2",
        "town" -> "twn",
        "postcode" -> "fudgebarwizz123")

      editFormNonuk.bind(data).hasErrors mustBe true
    }
  }

  "uk and non uk edit form" should {
    Map(editFormUk -> "uk", editFormNonuk -> "nonUk", editFormUkWelsh -> "uk Welsh", editFormNonukWelsh -> "nonUk Welsh").foreach{ mapOfForms =>
      val formOfTest = mapOfForms._2
      val form = mapOfForms._1

      // #Scenario: UK Address no postcode
      s" accept a UK address with no postcode where for $formOfTest" in {
        ALFForms.isValidPostcode(form.fill(Edit(None, None, None, None, None, "", "GB"))).hasErrors mustBe false
      }
      // #Scenario Outline: UK Address with Invalid PostCode
      Seq(
        ("case 1","MN 99555"),
        ("case 2","A"),
        ("case 3","1"),
        ("case 4","999999999999"),
        ("case 5","ABC123XYZ123"),
        ("case 6","SW778 2BH")).foreach {
        case (caseNum, postcode) =>
          s"not accept a UK address with an invalid postcode ($caseNum) for $formOfTest" in {
            ALFForms.isValidPostcode(form.fill(Edit(None, None, None, None, None, postcode, "GB"))).hasErrors mustBe true
          }
      }

      // #Scenario Outline: UK Address with Valid PostCode
      Seq(
        ("case 1","SW1A 1AA"),
        ("case 2","SW11 2BB"),
        ("case 3","SW7 9YY"),
        ("case 4","B1 1AA"),
        ("case 5","E1W 3CC"),
        ("case 6","B11 6HJ")).foreach{
        case  (caseNum,postcode) =>
          s"accept a UK address with a valid postcode ($caseNum) for $formOfTest" in {
            ALFForms.isValidPostcode(form.fill(Edit(None, None, None, None, None, postcode, "GB"))).hasErrors mustBe false
          }
      }
      s"accept valid postcode and no CountryCode as country code is defaulted for $formOfTest" in {
        ALFForms.isValidPostcode(form.fill(Edit(None, None, None, None, None, "ZZ11ZZ", ""))).hasErrors mustBe false
      }

      s"$formOfTest accept input if only line 1 is present" in {
        val data = Map(
          "line1" -> "foo1",
          "line2" -> "",
          "line3" -> "",
          "town" -> "",
          "postcode" -> "ZZ11ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe false
      }

      s"$formOfTest accept input if only line 2 is present" in {
        val data = Map(
          "line1" -> "",
          "line2" -> "foo2",
          "line3" -> "",
          "town" -> "",
          "postcode" -> "ZZ11ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe false
      }

      s"$formOfTest accept input if only line 3 is present" in {
        val data = Map(
          "line1" -> "",
          "line2" -> "",
          "line3" -> "foo3",
          "town" -> "",
          "postcode" -> "ZZ11ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe false
      }

      s"$formOfTest accept input if only town is present" in {
        val data = Map(
          "line1" -> "",
          "line2" -> "",
          "line3" -> "",
          "town" -> "town",
          "postcode" -> "AA199ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe false
      }

      s"$formOfTest return error if all lines and town are empty" in {
        val data = Map(
          "line1" -> "",
          "line2" -> "",
          "line3" -> "",
          "town" -> "",
          "postcode" -> "AA199ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe true
      }

      s"$formOfTest return error if line 1 > 255 chars" in {
        val data = Map(
          "line1" -> chars256,
          "line2" -> "foo2",
          "line3" -> "foo3",
          "town" -> "twn",
          "postcode" -> "ZZ11ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe true
      }
      s"$formOfTest return error if line 2 > 255 chars" in {
        val data = Map(
          "line1" -> "foo",
          "line2" -> chars256,
          "line3" -> "foo3",
          "town" -> "twn",
          "postcode" -> "ZZ11ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe true
      }
      s"$formOfTest return error if line 3 > 255 chars" in {
        val data = Map(
          "line1" -> "foo",
          "line2" -> "foo2",
          "line3" -> chars256,
          "town" -> "twn",
          "postcode" -> "ZZ11ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe true
      }
      s"$formOfTest return error if town > 255 chars" in {
        val data = Map(
          "line1" -> "foo",
          "line2" -> "foo2",
          "line3" -> "foo3",
          "town" -> chars256,
          "postcode" -> "AA199ZZ",
          "countryCode" -> "GB")

        form.bind(data).hasErrors mustBe true
      }
    }
  }

  "constraintString256" should {
    "return invalid for string > 256" in {
      ALFForms.constraintString256("foo")(chars257) mustBe Invalid("foo")
    }
    "return Invalid for string = 256" in {
      ALFForms.constraintString256("foo")(chars256)  mustBe Invalid("foo")
    }
    "return Valid for string < 256" in  {
      ALFForms.constraintString256("foo")(chars255)  mustBe Valid
    }
  }

  "constraintMinLength" should {
    "return invalid for empty string" in {
      ALFForms.constraintMinLength("foo")("") mustBe Invalid("foo")
    }
    "return valid for string > 0 chars" in {
      ALFForms.constraintMinLength("foo")("1") mustBe Valid
    }
  }
}
