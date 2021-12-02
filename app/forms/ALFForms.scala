/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.Confirmed
import forms.Helpers.EmptyStringValidator
import model.{Edit, Lookup, Select}
import play.api.data.{Form, FormError, Forms}
import play.api.data.Forms.{default, ignored, mapping, nonEmptyText, optional, text}
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.i18n.Messages
import uk.gov.voa.play.form.Condition
import uk.gov.voa.play.form.ConditionalMappings.{isEqual, mandatoryIf}

object Helpers {

  trait EmptyStringValidator {
    def customErrorTextValidation(message: String) = Forms.of[String](stringFormat(message))
    def stringFormat(message: String): Formatter[String] = new Formatter[String] {

      private def getNonEmpty(key: String, data: Map[String, String]): Option[String] = data.getOrElse(key, "").trim match {
        case "" => None
        case entry => Some(entry)
      }

      def bind(key: String, data: Map[String, String]): Either[Seq[FormError],String] = getNonEmpty(key, data).toRight(Seq(FormError(key, message, Nil)))
      def unbind(key: String, value: String) = Map(key -> value)
    }

  }
}

object ALFForms extends EmptyStringValidator {

  def hasInvalidChars(chars: String) = !chars.replaceAll("\\s", "").forall(_.isLetterOrDigit)

  def isInvalidPostcode(postcode: String) = !Postcode.cleanupPostcode(postcode).isDefined

  //TODO: TESTS_REQUIRED
  //Need to cover uk and non-uk mode
  def postcodeConstraint(isUkMode: Boolean)(implicit messages: Messages): Constraint[String] = Constraint[String](Some("constraints.postcode"), Seq.empty)({
    case empty if empty.isEmpty =>
      Invalid(Seq(ValidationError(messages(s"constants.lookupPostcodeEmptyError${if (isUkMode) ".ukMode" else ""}"))))
    case chars if hasInvalidChars(chars) =>
      Invalid(Seq(ValidationError(messages(s"constants.lookupPostcodeInvalidError${if (isUkMode) ".ukMode" else ""}"))))
    case postcode if isInvalidPostcode(postcode) =>
      Invalid(Seq(ValidationError(messages(s"constants.lookupPostcodeError${if (isUkMode) ".ukMode" else ""}"))))
    case _ =>
      Valid
  })

  //TODO: TESTS_REQUIRED
  def lookupForm(isUkMode: Boolean = false)(implicit messages: Messages) = Form(
    mapping(
      "filter" -> optional(text.verifying(messages(s"constants.lookupFilterError"), txt => txt.length < 256)),
      "postcode" -> text.verifying(postcodeConstraint(isUkMode))
    )(Lookup.apply)(Lookup.unapply)
  )

  val minimumLength: Int = 1
  val maximumLength: Int = 255

  def minimumConstraint()(implicit messages: Messages): Constraint[String] = new Constraint[String](Some("length.min"), Seq.empty)(value =>
    if(value.length >= minimumLength) Valid else Invalid(messages(s"constants.errorMin").replace("$min", minimumLength.toString))
  )

  def maximumConstraint()(implicit messages: Messages): Constraint[String] = new Constraint[String](Some("length.max"), Seq.empty)(value =>
    if(value.length <= maximumLength) Valid else Invalid(messages(s"constants.errorMax").replace("$max", maximumLength.toString))
  )

  def nonEmptyConstraint()(implicit messages: Messages): Constraint[String] = new Constraint[String](Some("required"), Seq.empty)(value =>
    if(value.nonEmpty) Valid else Invalid(messages(s"constants.errorRequired"))
  )

  def selectForm()(implicit messages: Messages) = Form(
    mapping(
      "addressId" -> default(text, "").verifying(StopOnFirstFail(
        nonEmptyConstraint(),
        minimumConstraint(),
        maximumConstraint()
      ))
    )(Select.apply)(Select.unapply)
  )

  val constraintOptString256 = (msg: String)  => new Constraint[Option[String]](Some("length.max"),Seq.empty)(s => if(s.isEmpty || s.get.length < 256) {
    Valid
  } else {
    Invalid(msg)
  } )

  val constraintString256 = (msg: String)  => new Constraint[String](Some("length.max"),Seq.empty)(s => if(s.length < 256) {
    Valid
  } else {
    Invalid(msg)
  } )

  val constraintMinLength = (msg: String) => new Constraint[String](Some("length.min"),Seq.empty)(s => if(s.nonEmpty) {
    Valid
  } else {
    Invalid(msg)
  } )


  def isValidPostcode(form: Form[Edit])(implicit messages: Messages): Form[Edit] = {
    val isGB: Boolean = form("countryCode").value.fold(true)(_ == "GB")
    val postcode = form("postcode").value.getOrElse("")

    (isGB, postcode) match {
      case (true, p) if p.nonEmpty && !Postcode.cleanupPostcode(postcode).isDefined => form.withError("postcode", messages(s"constants.editPagePostcodeErrorMessage"))
      case _ => form
    }
  }

  def atLeastOneAddressLineOrTown(message: String = "") = Forms.of[Option[String]](formatter(message))
  def formatter(message: String = ""): Formatter[Option[String]] = new Formatter[Option[String]] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError],Option[String]] = {
      val values = Seq(data.get("line1"), data.get("line2"), data.get("line3"), data.get("town")).flatten
      if (values.forall(_.isEmpty)) {
        Left(Seq(FormError(key, message, Nil)))
      } else {
        Right(data.get(key))
      }
    }

    override def unbind(key: String, value: Option[String]): Map[String, String] = Map(key -> value.getOrElse(""))
  }

  def ukEditForm()(implicit messages: Messages): Form[Edit] = Form(
    mapping(
      "line1" -> atLeastOneAddressLineOrTown(messages(s"constants.editPageAtLeastOneLineOrTown")).verifying(constraintOptString256(messages(s"constants.editPageAddressLine1MaxErrorMessage"))),
      "line2" -> atLeastOneAddressLineOrTown().verifying(constraintOptString256(messages(s"constants.editPageAddressLine2MaxErrorMessage"))),
      "line3" -> atLeastOneAddressLineOrTown().verifying(constraintOptString256(messages(s"constants.editPageAddressLine3MaxErrorMessage"))),
      "town" -> atLeastOneAddressLineOrTown().verifying(constraintOptString256(messages(s"constants.editPageTownMaxErrorMessage"))),
      "postcode" -> default(text,""),
      "countryCode" -> ignored[String]("GB")
    )(Edit.apply)(Edit.unapply).verifying(messages(s"constants.editPageAtLeastOneLineOrTown"), edit => {
      Seq(edit.line1, edit.line2, edit.line3, edit.town).flatten.nonEmpty
    })
  )

  import uk.gov.voa.play.form._




  def nonUkEditForm()(implicit messages: Messages) =
    Form(
      mapping(
        "line1" -> atLeastOneAddressLineOrTown(messages(s"constants.editPageAtLeastOneLineOrTown")).verifying(constraintOptString256(messages(s"constants.editPageAddressLine1MaxErrorMessage"))),
        "line2" -> atLeastOneAddressLineOrTown().verifying(constraintOptString256(messages(s"constants.editPageAddressLine2MaxErrorMessage"))),
        "line3" -> atLeastOneAddressLineOrTown().verifying(constraintOptString256(messages(s"constants.editPageAddressLine3MaxErrorMessage"))),
        "town" -> atLeastOneAddressLineOrTown().verifying(constraintOptString256(messages(s"constants.editPageTownMaxErrorMessage"))),
        "postcode" -> default(text,""),
        "countryCode" -> customErrorTextValidation("")
      )(Edit.apply)(Edit.unapply)
    )

//  def nonUkEditForm()(implicit messages: Messages) = {
//    val x = constraintOptString256(messages(s"constants.editPageAddressLine1MaxErrorMessage"))
//    Form(
//      mapping(
//        "line1" -> mandatoryIf(
//          isEqual("line2", "") and isEqual("line3", "") and isEqual("town", ""),
//          customErrorTextValidation(messages(s"constants.editPageAddressLine1MinErrorMessage"))).verifying(x),
//        "line2" -> mandatoryIf(
//          isEqual("line1", "") and isEqual("line3", "") and isEqual("town", ""),
//          customErrorTextValidation(messages(s"constants.editPageAddressLine2MinErrorMessage"))).verifying(x),
//        "line3" -> mandatoryIf(
//          isEqual("line1", "") and isEqual("line2", "") and isEqual("town", ""),
//          customErrorTextValidation(messages(s"constants.editPageAddressLine3MinErrorMessage"))).verifying(x),
//        "town" -> mandatoryIf(
//          isEqual("line1", "") and isEqual("line2", "") and isEqual("line3", ""),
//          customErrorTextValidation(messages(s"constants.editPageTownMinErrorMessage"))).verifying(x),
//        "postcode" -> default(text,""),
//        "countryCode" -> customErrorTextValidation(messages(s"constants.editPageCountryErrorMessage"))
//      )(Edit.apply)(Edit.unapply)
//    )
//  }

  val confirmedForm = Form(
    mapping(
      "id" -> text(1, 255)
    )(Confirmed.apply)(Confirmed.unapply)
  )
}

import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

object StopOnFirstFail {

  def apply[T](constraints: Constraint[T]*) = Constraint { field: T =>
    constraints.toList dropWhile (_(field) == Valid) match {
      case Nil             => Valid
      case constraint :: _ => constraint(field)
    }
  }

  def constraint[T](message: String, validator: (T) => Boolean) =
    Constraint((data: T) => if (validator(data)) Valid else Invalid(Seq(ValidationError(message))))
}