/*
 * Copyright (c) 2017 Ghent University - imec
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.rml.framework.flink.item.json

import java.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.JsonPath
import io.rml.framework.core.internal.Logging
import io.rml.framework.flink.item.Item
import io.rml.framework.flink.source.JSONStream
import org.jsfr.json.provider.JacksonProvider
import org.jsfr.json.{JacksonParser, JsonSurfer}

import scala.collection.JavaConversions._
import scala.util.control.NonFatal

class JSONItem(map: java.util.Map[String, Object], val tag: Option[String] = None) extends Item  {



  override def refer(reference: String): Option[List[String]] = {
    try {

      val sanitizedReference: String = if (reference.contains(' ')) s"['$reference']" else reference
      val checkedReference = if (sanitizedReference.contains('$')) sanitizedReference else "$." + sanitizedReference
      // Some(next.toString.replaceAll("\"", "")) still necessary?
      val _object: Object = JsonPath.read(map, checkedReference)

      _object match {
        case arr: java.util.List[_] => Some(arr.toList.map(_.toString))
        case jsonObj: util.HashMap[_, _] =>
          JSONItem.logDebug(s"Cannot be object: \n $jsonObj")
          None
        case null => None

        case e => Some(List(e.toString))
      }

    } catch {
      case e: Throwable =>
        JSONItem.logDebug(s"Cannot do referencing: \n $e")
        None
    }
  }
}

object JSONItem extends Logging {

  private val surfer = new JsonSurfer(JacksonParser.INSTANCE, JacksonProvider.INSTANCE)



  def fromStringOptionableList(json: String, jsonPaths: List[String]): List[Item] = {
    val result: List[Item] = jsonPaths
      .flatMap(

        jsonPath => {
          try {
            val tag = jsonPath match {
              case JSONStream.DEFAULT_PATH_OPTION => None
              case _ => Some(jsonPath)
            }
            val collection = surfer.collectAll(json, jsonPath)
            val listOfJson = collection.toArray()
            val mapper = new ObjectMapper()

            val result: Array[Item] = listOfJson
              .map(node => mapper.convertValue(node, classOf[java.util.Map[String, Object]]))
              .map(map => new JSONItem(map, tag))

            Some(result)
          } catch {
            case NonFatal(e) => logError("Error while parsing JSON: " + e.getMessage + " | " + json); None
          }
        }
      )
      .flatten

    result
  }
}
