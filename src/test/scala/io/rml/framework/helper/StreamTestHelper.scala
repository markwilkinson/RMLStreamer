package io.rml.framework.helper

import java.io.File
import java.util.concurrent.Executors

import io.netty.channel.ChannelHandlerContext
import io.rml.framework.helper.fileprocessing.MappingTestHelper
import io.rml.framework.{Main, TestUtil}
import org.apache.flink.api.common.JobID
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.configuration.{ConfigConstants, Configuration, TaskManagerOptions}
import org.apache.flink.runtime.messages.JobManagerMessages.CancelJob
import org.apache.flink.runtime.minicluster.{FlinkMiniCluster, LocalFlinkMiniCluster}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object StreamTestHelper {

  implicit val env = ExecutionEnvironment.getExecutionEnvironment
  implicit var senv = StreamExecutionEnvironment.createLocalEnvironment()
  implicit val executor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  /**
    * Create a data stream from mapping file in de specified test case folder.
    * The method won't run it yet as a job in flink cluster.
    *
    * This is used to generate and obtain job graphs which will be used by the
    * cluster to schedule/cancel jobs.
    *
    * @param testCaseFolder folder containing rml mapping file
    * @return flink DataStream[String]
    */
  def createDataStream(testCaseFolder: File): DataStream[String] = {

    // Refreshes stream execution environment for each data stream
    senv = StreamExecutionEnvironment.createLocalEnvironment()
    // read the mapping
    val formattedMapping = MappingTestHelper.processFilesInTestFolder(testCaseFolder.getAbsolutePath)
    val stream = Main.createStreamFromFormattedMapping(formattedMapping.head)


    stream.addSink(TestSink())
    stream
  }

  /**
    * Cancels a job with the given JobID in the cluster.
    *
    * See line 550 of src/main/java/org/apache/flink/client/CliFrontend.java in
    * https://github.com/apache/flink/tree/release-1.3.2-rc3/flink-clients
    *
    * @param jobID
    * @param cluster
    * @param duration
    */

  def cancelJob(jobID: JobID, cluster: FlinkMiniCluster, duration: FiniteDuration = FiniteDuration(1, "seconds")): Future[Unit] = {
    val actorGateFuture = cluster.getLeaderGatewayFuture
    actorGateFuture map { gateway =>
      gateway.ask(new CancelJob(jobID), duration)

    }

  }

  /**
    * Write out the given messages to a channel handled by the given netty ChannelHandlerContext
    *
    * The given channel context is wrapped in a handler since it has to be first set-up by the netty
    *  TCP server
    * @param messages
    * @param ctxChlHandler future wrapped handler.
    */

  def writeDataToTCP(messages: Iterator[String], ctxChlHandler: Future[ChannelHandlerContext]): Unit = {
      ctxChlHandler map {ctx =>

        for(el <- messages) {
          val byteBuff = ctx.alloc.buffer(el.length)
          byteBuff.writeBytes(el.getBytes())
          ctx.channel.writeAndFlush(byteBuff)
        }
      }
  }

  /**
    * Submit a job specified in the data stream to the given mini flink cluster.
    *
    * @param cluster a flink cluster
    * @param dataStream
    * @param name    tag for the job whic will be submitted to cluster
    * @tparam T result type of the data stream
    * @return JobID of the submitted job which can be used later on to cancel/stop
    */
  def submitJobToCluster[T](cluster: FlinkMiniCluster, dataStream: DataStream[T], name: String): JobID = {


    val graph = dataStream.executionEnvironment.getStreamGraph
    graph.setJobName(name)
    val jobGraph = graph.getJobGraph
    cluster.submitJobDetached(jobGraph)

    jobGraph.getJobID
  }


  /**
    * Starts a tcp server on port 9999(default) on another thread.
    *
    * @param port default value is 9999
    * @return
    */
  def getTCPFuture(port: Int = 9999): Future[Unit] = {
    Logger.logInfo(s"Begin TCP server on port: $port")
    TestUtil.createTCPServer(port)
    Future.successful()
  }


  /**
    * Starts a local mini cluster to let scala tests have more control
    * over the flink jobs (cancelling, adding, multiple jobs, etc...)
    *
    * The cluster will be run on a separate thread.
    *
    *
    * See link below for usage of flink cluster.
    * https://stackoverflow.com/questions/43871438/can-flink-attach-multiple-jobs-to-stream-local-envirnoment-with-web-ui-by-java-c
    *
    * @return Future[LocalFlinkMiniCluster] containing the cluster class
    *         which can be used for stopping/cancelling and starting jobs
    */

  def getClusterFuture: Future[LocalFlinkMiniCluster] = {

    val configuration = new Configuration
    configuration.setLong(TaskManagerOptions.MANAGED_MEMORY_SIZE, -1L)
    configuration.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 2)
    configuration.setInteger(ConfigConstants.TASK_MANAGER_NUM_TASK_SLOTS, 100)

    // start cluster
    val cluster = new LocalFlinkMiniCluster(configuration, true)

    cluster.start()
    // sleep or wait for all job finishes
    Future.successful(cluster)
  }

}
