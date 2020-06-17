/**
 * MIT License
 *
 * Copyright (C) 2017 - 2020 RDF Mapping Language (RML)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 **/
package io.rml.framework

import java.io.File

import io.rml.framework.Main
import io.rml.framework.api.RMLEnvironment
import io.rml.framework.core.extractors.{MappingExtractor, MappingReader}
import io.rml.framework.core.function.FunctionLoader
import io.rml.framework.core.model.FunctionMapping
import io.rml.framework.core.util.Util
import io.rml.framework.engine.NopPostProcessor
import io.rml.framework.util.TestUtil
import io.rml.framework.util.logging.Logger
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.streaming.api.scala._
import org.scalatest.{DoNotDiscover, FunSuite, Ignore, Matchers}
import io.rml.framework.util.logging.Logger
@DoNotDiscover
class SandboxTests extends FunSuite with Matchers  {

  val functionFile = new File(getClass.getClassLoader.getResource("functions.ttl").getFile)


  private def executeTest(mappingFile: String): Unit = {
    RMLEnvironment.setGeneratorBaseIRI(Some("http://example.org/base/"))
    implicit val env = ExecutionEnvironment.getExecutionEnvironment
    implicit val senv = StreamExecutionEnvironment.getExecutionEnvironment
    implicit val postProcessor = new NopPostProcessor()

    val testDir = Util.getFile(new File(mappingFile).getParent)
    val mappingFileAbs = new File(testDir, new File(mappingFile).getName)

    // function descriptions
    val functionDescriptionFilePaths = List(
      "functions_grel.ttl",
      "functions_idlab.ttl"
    )

    // function mappings
    val grelJavaMappingFile = new File(getClass.getClassLoader.getResource("grel_java_mapping.ttl").getFile)
    val idlabJavaMappingFile = new File(getClass.getClassLoader.getResource("idlab_java_mapping.ttl").getFile)

    // singleton FunctionLoader created and initialized with given function descriptions
    val functionLoader = FunctionLoader(functionDescriptionFilePaths)

    // Parse the function mapping files.
    // The functionloader will construct a mapping between function uris and the corresponding function meta data objects
    functionLoader
      .parseFunctionMapping(grelJavaMappingFile)
      .parseFunctionMapping(idlabJavaMappingFile)


    // read the mapping
    val formattedMapping = Util.readMappingFile(mappingFileAbs.getAbsolutePath)

    // execute
    val result = Main.createDataSetFromFormattedMapping(formattedMapping).collect()

    // get expected output
    //val testDir = new File(mappingFile).getParentFile.getAbsoluteFile
    val (expectedOutput, expectedOutputFormat) = TestUtil.getExpectedOutputs(testDir)

    val testOutcome = TestUtil.compareResults(s"StatementEngineTest: ${testDir}", result, expectedOutput, postProcessor.outputFormat, expectedOutputFormat)
    testOutcome match {
      case Left(e) => {
        Logger.logError(e)
        System.exit(1)
        fail(e)
      }
      case Right(e) => {
        Logger.logSuccess(e)
      }
    }
  }
  // Example
//  test("sandbox/fno-testcases/RMLFNOTC0004-CSV") {
//    pending
//    executeTest("sandbox/fno-testcases/RMLFNOTC0004-CSV/mapping.ttl")
//  }








}
