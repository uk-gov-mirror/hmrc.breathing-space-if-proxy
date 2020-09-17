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

package uk.gov.hmrc.breathingspaceifproxy.controller

import java.util.UUID

import cats.data.ValidatedNec
import cats.implicits._
import play.api.Logging
import play.api.http.HttpVerbs
import play.api.libs.json._
import play.api.mvc.{BaseController => PlayController, _}
import uk.gov.hmrc.breathingspaceifproxy._
import uk.gov.hmrc.breathingspaceifproxy.model.{Attended, Error, Nino}
import uk.gov.hmrc.breathingspaceifproxy.model.BaseError._
import uk.gov.hmrc.domain.{Nino => DomainNino}

trait RequestValidation extends PlayController with Logging {

  type Validation[A] = ValidatedNec[Error, A]

  def validateNino(maybeNino: String): Validation[Nino] =
    if (DomainNino.isValid(maybeNino)) Nino(maybeNino).validNec
    else Error(INVALID_NINO, s"($maybeNino)".some).invalidNec

  def validateHeaders(implicit request: Request[_]): Validation[Unit] = {
    val headers = request.headers
    (
      validateContentType(request),
      validateCorrelationId(headers),
      validateRequestType(headers),
      validateStaffId(headers)
    ).mapN((_, _, _, _) => unit)
  }

  def validateBody[A, B](f: A => Validation[B])(implicit request: Request[AnyContent], reads: Reads[A]): Validation[B] =
    if (!request.hasBody) Error(MISSING_BODY).invalidNec
    else {
      request.body.asJson.fold[Validation[B]](Error(INVALID_JSON).invalidNec) { json =>
        Either.catchNonFatal(json.as[A]).fold(createErrorFromInvalidPayload[B], f(_))
      }
    }

  private def validateContentType(request: Request[_]): Validation[Unit] =
    request.headers
      .get(CONTENT_TYPE)
      .fold[Validation[Unit]] {
        if (request.method.toUpperCase == HttpVerbs.GET) unit.validNec
        // The "Content-type" header is mandatory for POST, PUT and DELETE,
        // (they are the only HTTP methods accepted, with GET of course).
        else Error(MISSING_HEADER, s"(${CONTENT_TYPE})".some).invalidNec
      } { contentType =>
        // In case the "Content-type" header is specified, a body, if any,
        // is always expected to be in Json format with charset=UTF-8.
        if (contentType.toLowerCase.replace(" ", "") == JsonContentType) unit.validNec
        else Error(INVALID_HEADER, s"(${CONTENT_TYPE}). Invalid value: ${contentType}".some).invalidNec
      }

  private def validateCorrelationId(headers: Headers): Validation[Unit] =
    headers
      .get(Header.CorrelationId)
      .fold[Validation[Unit]] {
        Error(MISSING_HEADER, s"(${Header.CorrelationId})".some).invalidNec
      } { correlationId =>
        Either
          .catchNonFatal(UUID.fromString(correlationId))
          .fold[Validation[Unit]](
            _ => Error(INVALID_HEADER, s"(${Header.CorrelationId})".some).invalidNec,
            _ => unit.validNec
          )
      }

  private lazy val invalidRequestHeader =
    s"(${Header.RequestType}). Valid values are: ${Attended.values.mkString(", ")}".some

  private def validateRequestType(headers: Headers): Validation[Unit] =
    headers
      .get(Header.RequestType)
      .fold[Validation[Unit]] {
        Error(MISSING_HEADER, s"(${Header.RequestType})".some).invalidNec
      } { requestType =>
        Attended
          .withNameOption(requestType.toUpperCase)
          .fold[Validation[Unit]] {
            Error(INVALID_HEADER, invalidRequestHeader).invalidNec
          } { _ =>
            unit.validNec
          }
      }

  private val staffIdRegex = "^[0-9]{7}$".r

  private def validateStaffId(headers: Headers): Validation[Unit] =
    headers
      .get(Header.StaffId)
      .fold[Validation[Unit]] {
        Error(MISSING_HEADER, s"(${Header.StaffId})".some).invalidNec
      } { staffId =>
        staffIdRegex
          .findFirstIn(staffId)
          .fold[Validation[Unit]] {
            Error(INVALID_HEADER, s"(${Header.StaffId}). Expected a 7-digit number but was $staffId".some).invalidNec
          } { _ =>
            unit.validNec
          }
      }

  private def createErrorFromInvalidPayload[B](throwable: Throwable)(implicit request: Request[_]): Validation[B] = {
    val correlationId = retrieveCorrelationId
    val reason = s"Exception raised while validating the request's body: ${request.body}"
    logger.error(s"(Correlation-id: ${correlationId.fold("")(_)}) $reason", throwable)
    Error(INVALID_JSON).invalidNec
  }

  def retrieveCorrelationId(implicit request: Request[_]): Option[String] =
    request.headers.get(Header.CorrelationId)
}
