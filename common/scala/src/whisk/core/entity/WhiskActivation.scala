/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.entity

import java.time.Instant

import scala.util.Try

import spray.json.DefaultJsonProtocol
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.RootJsonFormat
import spray.json.deserializationError
import spray.json.pimpAny
import whisk.core.database.DocumentFactory
import whisk.core.entity.schema.ActivationRecord

/**
 * A WhiskActivation provides an abstraction of the meta-data
 * for a whisk action activation record.
 *
 * The WhiskActivation object is used as a helper to adapt objects between
 * the schema used by the database and the WhiskAuth abstraction.
 *
 * @param namespace the namespace for the activation
 * @param name the name of the activated entity
 * @param subject the subject activating the entity
 * @param activationId the activation id
 * @param start the start of the activation in epoch millis
 * @param end the end of the activation in epoch millis
 * @param cause the activation id of the activated entity that causes this activation
 * @param response the activation response
 * @param logs the activation logs
 * @param version the semantic version (usually matches the activated entity)
 * @param publish true to share the activation or false otherwise
 * @param annotation the set of annotations to attribute to the activation
 * @throws IllegalArgumentException if any required argument is undefined
 */
@throws[IllegalArgumentException]
case class WhiskActivation(
    namespace: Namespace,
    override val name: EntityName,
    subject: Subject,
    activationId: ActivationId,
    start: Instant,
    end: Instant,
    cause: Option[ActivationId] = None,
    response: ActivationResponse = ActivationResponse.success(),
    logs: ActivationLogs = ActivationLogs(),
    version: SemVer = SemVer(),
    publish: Boolean = false,
    annotations: Parameters = Parameters())
    extends WhiskEntity(EntityName(activationId())) {

    require(cause != null, "cause undefined")
    require(start != null, "start undefined")
    require(end != null, "end undefined")
    require(response != null, "response undefined")
    require(logs != null, "logs undefined")

    override def serialize: Try[ActivationRecord] = Try {
        val r = serialize[ActivationRecord](new ActivationRecord)
        r.subject = subject.toString
        r.activationId = activationId.toString
        r.start = start.toEpochMilli
        r.end = end.toEpochMilli
        r.cause = if (cause != null) { cause map { _.toString } getOrElse null } else null
        r.response = response.toGson
        r.logs = logs.toGson
        r
    }

    override def summaryAsJson = {
        val JsObject(fields) = super.summaryAsJson
        JsObject(fields + ("activationId" -> activationId.toJson))
    }

    def toExtendedJson : JsObject = {
        val JsObject(baseFields) = WhiskActivation.serdes.write(this).asJsObject
        val newFields = (baseFields - "response") + ("response" -> response.toExtendedJson)
        JsObject(newFields)
    }
}

object WhiskActivation
    extends DocumentFactory[ActivationRecord, WhiskActivation]
    with WhiskEntityQueries[WhiskActivation]
    with DefaultJsonProtocol {

    private implicit val instantSerdes = new RootJsonFormat[Instant] {
        def write(t: Instant) = t.toEpochMilli.toJson

        def read(value: JsValue) = Try {
            value match {
                case JsString(t) => Instant.parse(t)
                case JsNumber(i) => Instant.ofEpochMilli(i.bigDecimal.longValue)
                case _           => deserializationError("timetsamp malformed 1")
            }
        } getOrElse deserializationError("timetsamp malformed 2")
    }

    override val collectionName = "activations"
    override implicit val serdes = jsonFormat12(WhiskActivation.apply)

    override def apply(r: ActivationRecord): Try[WhiskActivation] = Try {
        WhiskActivation(
            Namespace(r.namespace),
            EntityName(r.name),
            Subject(r.subject),
            ActivationId(r.activationId),
            Instant.ofEpochMilli(r.start),
            Instant.ofEpochMilli(r.end),
            if (r.cause == null) None else Some(ActivationId(r.cause)),
            ActivationResponse(r.response),
            ActivationLogs(r.logs),
            SemVer(r.version),
            r.publish,
            Parameters(r.annotations)).
            revision[WhiskActivation](r.docinfo.rev)
    }

    override val cacheEnabled = true
    override def cacheKeys(w: WhiskActivation) = Set(w.docid.asDocInfo, w.docinfo)
}
