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

import javax.inject.{Inject, Singleton}

import scala.annotation.switch
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.syntax.apply._
import cats.syntax.option._
import cats.syntax.validated._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.breathingspaceifproxy.{ResponseValidation, Validation}
import uk.gov.hmrc.breathingspaceifproxy.config.AppConfig
import uk.gov.hmrc.breathingspaceifproxy.connector.IndividualDetailsConnector
import uk.gov.hmrc.breathingspaceifproxy.model._
import uk.gov.hmrc.breathingspaceifproxy.model.BaseError.INVALID_DETAIL_INDEX
import uk.gov.hmrc.breathingspaceifproxy.model.EndpointId._

@Singleton()
class IndividualDetailsController @Inject()(
  appConfig: AppConfig,
  cc: ControllerComponents,
  individualDetailsConnector: IndividualDetailsConnector
) extends AbstractBaseController(appConfig, cc) {

  val lastDetailId = 1

  def get(maybeNino: String, detailId: Int): Action[Validation[AnyContent]] = Action.async(withoutBody) {
    implicit request =>
      (
        validateHeadersForNPS,
        validateNino(maybeNino),
        validateDetailId(detailId),
        request.body
      ).mapN((correlationId, nino, endpointId, _) => (RequestId(endpointId, correlationId), nino))
        .fold(
          ErrorResponse(retrieveCorrelationId, BAD_REQUEST, _).value,
          validationTuple => {
            implicit val (requestId, nino) = validationTuple
            logger.debug(s"$requestId for Nino(${nino.value}) with detailId($detailId)")
            (detailId: @switch) match {
              case 0 => evalResponse[Detail0](individualDetailsConnector.get[Detail0](nino, DetailData0), DetailData0)
              case 1 => evalResponse[Detail1](individualDetailsConnector.get[Detail1](nino, DetailData1), DetailData1)
              // Shouldn't happen as detailId was already validated.
              case _ => ErrorResponse(requestId.correlationId, invalidDetailId(detailId)).value
            }
          }
        )
  }

  private def evalResponse[T <: Detail](response: ResponseValidation[T], detailData: DetailData[T])(
    implicit requestId: RequestId
  ): Future[Result] =
    response.flatMap {
      implicit val format = detailData.format
      _.fold(ErrorResponse(requestId.correlationId, _).value, composeResponse(OK, _))
    }

  private def validateDetailId(detailId: Int): Validation[EndpointId] =
    (detailId: @switch) match {
      case 0 => Breathing_Space_Detail0_GET.validNec[ErrorItem]
      case 1 => Breathing_Space_Detail1_GET.validNec[ErrorItem]
      case _ => invalidDetailId(detailId).invalidNec[EndpointId]
    }

  private def invalidDetailId(detailId: Int): ErrorItem =
    ErrorItem(INVALID_DETAIL_INDEX, s"$lastDetailId but it was ($detailId)".some)
}