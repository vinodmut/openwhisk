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

package whisk.core.database

import scala.annotation.implicitNotFound
import scala.concurrent.Future
import scala.util.Try

import spray.json.JsObject
import whisk.common.Logging
import whisk.common.TransactionId
import whisk.core.entity.DocInfo
import whisk.utils.ExecutionContextFactory

/** Basic client to put and delete artifacts in a data store. */
trait ArtifactStore[RawDocument, DocumentAbstraction] extends Logging {

    /** Execution context for futures */
    protected[core] implicit val executionContext = ExecutionContextFactory.makeExecutionContext()

    /**
     * Puts (saves) document to database using a future.
     * If the operation is successful, the future completes with DocId else an appropriate exception.
     *
     * @param d the document to put in the database
     * @param transid the transaction id for logging
     * @return a future that completes either with DocId
     */
    protected[database] def put(d: DocumentAbstraction)(implicit transid: TransactionId): Future[DocInfo]

    /**
     * Deletes document from database using a future.
     * If the operation is successful, the future completes with true.
     *
     * @param doc the document info for the record to delete (must contain valid id and rev)
     * @param transid the transaction id for logging
     * @return a future that completes true iff the document is deleted, else future is failed
     */
    protected[database] def del(doc: DocInfo)(implicit transid: TransactionId): Future[Boolean]

    /**
     * Gets document from database by id using a future.
     * If the operation is successful, the future completes with the requested document if it exists.
     *
     * @param doc the document info for the record to get (must contain valid id and rev)
     * @param deserialize a function from R => Try[A] where R is the RawDocument type and A is the DocumentAbstraction type
     * @param transid the transaction id for logging
     * @param m manifest for R to determine its runtime type, required by db API
     * @return a future that completes either with DocumentAbstraction if the document exists and is deserializable into desired type
     */
    protected[database] def get[R <: RawDocument, A <: DocumentAbstraction](doc: DocInfo)(
        implicit transid: TransactionId,
        deserialize: Deserializer[R, A],
        m: Manifest[R]): Future[A]

    /**
     * Gets all documents from database view that match a start key, up to an end key, using a future.
     * If the operation is successful, the promise completes with List[View]] with zero or more documents.
     *
     * @param table the name of the table to query
     * @param startKey to starting key to query the view for
     * @param endKey to starting key to query the view for
     * @param skip the number of record to skip (for pagination)
     * @param limit the maximum number of records matching the key to return, iff > 0
     * @param includeDocs include full documents matching query iff true (shall not be used with reduce)
     * @param descending reverse results iff true
     * @param reduce apply reduction associated with query to the result iff true
     * @param transid the transaction id for logging
     * @return a future that completes with List[JsObject] of all documents from view between start and end key (list may be empty)
     */
    protected[core] def query(table: String, startKey: List[Any], endKey: List[Any], skip: Int, limit: Int, includeDocs: Boolean, descending: Boolean, reduce: Boolean)(
        implicit transid: TransactionId): Future[List[JsObject]]

    /** Type for deserializer function that converts between raw document and abstraction. */
    protected type Deserializer[R <: RawDocument, A <: DocumentAbstraction] = R => Try[A]

    /** Shut it down. After this invocation, every other call is invalid. */
    def shutdown() : Unit
}
