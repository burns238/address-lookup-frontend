/*
 * Copyright 2016 HM Revenue & Customs
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

package address.bfpo

import java.net.URLEncoder

import address.bfpo.BfpoProposalsPage.showAddressListProposalForm
import address.outcome.SelectedAddress
import address.uk.service.AddressLookupService
import address.uk.{Services, TaggedAction}
import com.fasterxml.uuid.{EthernetAddress, Generators}
import config.FrontendGlobal
import keystore.MemoService
import play.api.Play.current
import play.api.i18n.Messages.Implicits._
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.address.uk.{Outcode, Postcode}
import uk.gov.hmrc.address.v2.{Address, AddressRecord, Countries}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.util.JacksonMapper
import views.html.addressuk._
import views.html.bfpo.blankBfpoForm

import scala.concurrent.{ExecutionContext, Future}


object BfpoAddressLookupController extends BfpoAddressLookupController(
  Services.configuredAddressLookupService,
  Services.metricatedKeystoreService,
  FrontendGlobal.executionContext)


class BfpoAddressLookupController(lookup: AddressLookupService, memo: MemoService, val ec: ExecutionContext) extends FrontendController {

  private implicit val xec = ec

  import BfpoForm.bfpoForm
  import address.ViewConfig._

  private val uuidGenerator = Generators.timeBasedGenerator(EthernetAddress.fromInterface())

  def getEmptyForm(tag: String, guid: Option[String], continue: Option[String],
                   backUrl: Option[String], backText: Option[String]): Action[AnyContent] =
    TaggedAction.withTag(tag).apply {
      implicit request =>
        Ok(basicBlankForm(tag, guid, continue, backUrl, backText))
    }

  private def basicBlankForm(tag: String, guid: Option[String], continue: Option[String],
                             backUrl: Option[String], backText: Option[String])(implicit request: Request[_]) = {
    val actualGuid = guid.getOrElse(uuidGenerator.generate.toString)
    val cu = continue.getOrElse(defaultContinueUrl)
    val ad = BfpoData(guid = actualGuid, continue = cu, backUrl = backUrl, backText = backText)
    val bound = bfpoForm.fill(ad)
    blankBfpoForm(tag, cfg(tag), bound, noMatchesWereFound = false)
  }

  //-----------------------------------------------------------------------------------------------

  def postFirstForm(tag: String): Action[AnyContent] =
    TaggedAction.withTag(tag).async {
      implicit request =>
        //        println("form1: " + PrettyMapper.writeValueAsString(request.body))
        val bound = bfpoForm.bindFromRequest()(request)
        if (bound.errors.nonEmpty) {
          Future.successful(BadRequest(blankBfpoForm(tag, cfg(tag), bound, noMatchesWereFound = false)))

        } else {
          val bfpoData = bound.get
          Future.successful(fixedAddress(tag, bfpoData))
        }
    }

  private def fixedAddress(tag: String, formData: BfpoData)(implicit request: Request[_]) = {
    if (formData.postcode.isEmpty && formData.number.isEmpty) {
      val formWithError = bfpoForm.fill(formData).withError("postcode", "Either a post code or a BFPO number is required")
      BadRequest(blankBfpoForm(tag, cfg(tag), formWithError, noMatchesWereFound = false))

    } else {
      val pc = formData.postcode.flatMap(Postcode.cleanupPostcode)
      if (formData.postcode.isDefined && pc.isEmpty) {
        val formWithError = bfpoForm.fill(formData).withError("postcode", "A valid post code is required")
        BadRequest(blankBfpoForm(tag, cfg(tag), formWithError, noMatchesWereFound = false))

      } else {
        val cu = Some(formData.continue)
        val number = formData.number.getOrElse("-")
        val proposalsRoute = routes.BfpoAddressLookupController.getProposals(tag, number, pc.map(_.toString).getOrElse("-"),
          formData.guid, cu, None, formData.backUrl, formData.backText)
        SeeOther(proposalsRoute.url)
      }
    }
  }

  //-----------------------------------------------------------------------------------------------

  private val BF1 = Outcode("BF1") // covers all BFPO addresses

  def getProposals(tag: String, number: String, postcode: String, guid: String, continue: Option[String],
                   editId: Option[String], backUrl: Option[String], backText: Option[String]): Action[AnyContent] =
    TaggedAction.withTag(tag).async {
      implicit request =>
        val optNumber = normaliseNumber(number)
        val uPostcode = Postcode.cleanupPostcode(postcode)
        val cu = continue.getOrElse(defaultContinueUrl)
        val pc = uPostcode.map(_.toString)
        val data = BfpoData(guid = guid, continue = cu,
          backUrl = backUrl, backText = backText,
          number = optNumber, postcode = pc,
          prevNumber = optNumber, prevPostcode = pc
        )
        if (optNumber.isEmpty && uPostcode.isEmpty) {
          Future.successful(BadRequest(basicBlankForm(tag, Some(guid), continue, backUrl, backText)))

        } else if (uPostcode.isDefined) {
          lookup.findByPostcode(uPostcode.get, None) map {
            list => showProposals(tag, data, list, editId)
          }

        } else {
          lookup.findByOutcode(BF1, number) map {
            list => showProposals(tag, data, list, editId)
          }
        }
    }

  private def normaliseNumber(number: String) = {
    val t = number.trim
    if (number.isEmpty || number == "-") {
      None
    } else if (t.toUpperCase.startsWith("BFPO ")) {
      Some(t.substring(5).trim)
    } else {
      Some(t)
    }
  }

  private def showProposals(tag: String, data: BfpoData, list: List[AddressRecord], editId: Option[String])
                           (implicit request: Request[_]) = {
    if (list.isEmpty) {
      val filledInForm = bfpoForm.fill(data)
      Ok(blankBfpoForm(tag, cfg(tag), filledInForm, noMatchesWereFound = list.isEmpty))

    } else {
      Ok(showAddressListProposalForm(tag, data, list, editId))
    }
  }

  //-----------------------------------------------------------------------------------------------

  def postSelected(tag: String): Action[AnyContent] =
    TaggedAction.withTag(tag).async {
      implicit request =>
        //println("form2: " + PrettyMapper.writeValueAsString(request.body))
        val bound = bfpoForm.bindFromRequest()(request)
        if (bound.errors.nonEmpty) {
          Future.successful(BadRequest(blankBfpoForm(tag, cfg(tag), bound, noMatchesWereFound = false)))

        } else {
          userSelection(tag, bound.get, request)
        }
    }


  private def userSelection(tag: String, bfpoData: BfpoData, request: Request[_]): Future[Result] = {
    if (bfpoData.hasBeenUpdated) {
      val proposalsRoute = routes.BfpoAddressLookupController.getProposals(tag, bfpoData.number.getOrElse("-"),
        bfpoData.postcode.getOrElse("-"), bfpoData.guid, Some(bfpoData.continue), None, bfpoData.backUrl, bfpoData.backText)
      Future(SeeOther(proposalsRoute.url))

    } else {
      continueToCompletion(tag, bfpoData, request)
    }
  }


  private def continueToCompletion(tag: String, bfpoData: BfpoData, request: Request[_]): Future[Result] = {
    lookup.findById(bfpoData.uprnId.get) flatMap {
      normativeAddress =>
        val response = SelectedAddress(
          normativeAddress = normativeAddress,
          bfpo = Some(bfpoData.toInternational))
        memo.storeSingleResponse(tag, bfpoData.guid, response) map {
          httpResponse =>
            SeeOther(bfpoData.continue + "?tag=" + tag + "&id=" + bfpoData.guid)
        }
    }
  }

  //-----------------------------------------------------------------------------------------------

  def confirmation(tag: String, id: String): Action[AnyContent] =
    TaggedAction.withTag(tag).async {
      implicit request =>
        require(id.nonEmpty)
        val fuResponse = memo.fetchSingleResponse(tag, id)
        fuResponse.map {
          response: Option[JsValue] =>
            if (response.isEmpty) {
              val emptyFormRoute = routes.BfpoAddressLookupController.getEmptyForm(tag, Some(id), None, None, None)
              TemporaryRedirect(emptyFormRoute.url)
            } else {
              val addressRecord = response.get.as[SelectedAddress]
              val international = addressRecord.bfpo orElse addressRecord.international
              Ok(confirmationPage(tag, cfg(tag), addressRecord.normativeAddress, addressRecord.userSuppliedAddress, international))
            }
        }
    }

  private def encJson(value: AnyRef): String = URLEncoder.encode(JacksonMapper.writeValueAsString(value), "ASCII")

  private val noFixedAbodeAddress = Address(List("No fixed abode"), None, None, "", None, Countries.UK)

  private val UkCode = Countries.UK.code

}
