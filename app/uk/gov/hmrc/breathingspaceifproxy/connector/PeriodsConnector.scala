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

package uk.gov.hmrc.breathingspaceifproxy.connector

import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import cats.syntax.validated._
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json._
import uk.gov.hmrc.breathingspaceifproxy._
import uk.gov.hmrc.breathingspaceifproxy.config.AppConfig
import uk.gov.hmrc.breathingspaceifproxy.connector.service.EisConnector
import uk.gov.hmrc.breathingspaceifproxy.metrics.HttpAPIMonitor
import uk.gov.hmrc.breathingspaceifproxy.model._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpReads.Implicits._

@Singleton
class PeriodsConnector @Inject()(http: HttpClient, metrics: Metrics)(
  implicit appConfig: AppConfig,
  val eisConnector: EisConnector,
  ec: ExecutionContext
) extends HttpAPIMonitor {

  import PeriodsConnector._

  override lazy val metricRegistry: MetricRegistry = metrics.defaultRegistry

  def get(nino: Nino)(implicit requestId: RequestId, hc: HeaderCarrier): ResponseValidation[PeriodsInResponse] =
    eisConnector.monitor {
      monitor(s"ConsumedAPI-${requestId.endpointId}") {
        http.GET[PeriodsInResponse](Url(url(nino)).value).map(_.validNec)
      }
    }

  def post(nino: Nino, postPeriods: PostPeriodsInRequest)(
    implicit requestId: RequestId,
    hc: HeaderCarrier
  ): ResponseValidation[PeriodsInResponse] =
    eisConnector.monitor {
      monitor(s"ConsumedAPI-${requestId.endpointId}") {
        http
          .POST[JsValue, PeriodsInResponse](Url(url(nino)).value, Json.toJson(postPeriods))
          .map(_.validNec)
      }
    }

  def put(nino: Nino, putPeriods: PutPeriodsInRequest)(
    implicit requestId: RequestId,
    hc: HeaderCarrier
  ): ResponseValidation[PeriodsInResponse] =
    eisConnector.monitor {
      monitor(s"ConsumedAPI-${requestId.endpointId}") {
        http
          .PUT[JsValue, PeriodsInResponse](Url(url(nino)).value, Json.obj("periods" -> putPeriods))
          .map(_.validNec)
      }
    }
}

object PeriodsConnector {

  def path(nino: Nino)(implicit appConfig: AppConfig): String =
    s"/${appConfig.integrationFrameworkContext}/breathing-space/NINO/${nino.value}/periods"

  def url(nino: Nino)(implicit appConfig: AppConfig): String =
    s"${appConfig.integrationFrameworkBaseUrl}${path(nino)}"
}
