package com.joneubank.kf

import java.io.{BufferedWriter, File, FileWriter}
import java.text.SimpleDateFormat
import java.util.Calendar

import com.joneubank.kf.gen3.Gen3
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods.parse


object Extractor {

  private val projectId = "project_id"
  private val id = "id"
  private val nodeList = List("case", "demographic", "diagnosis", "sample", "aliquot", "read_group", "submitted_aligned_reads", "trio")

  private val dateFormatter = new SimpleDateFormat("YYYYMMddHHmmss")
  private val savePath = s"data/${dateFormatter.format(Calendar.getInstance.getTime)}/"
  private val saveSuffix = ".tsv"

  private var timingSteps: List[(String, Long)] = List()

  case class Record(nodeType: String, projectId: String, id: String)

  def run(): Unit = {
    // EXAMPLE Post to Graphql
    // val response = Gen3.graphql("{program{id}}")
    // println(response)

    // EXAMPLE Get Documents
    // val export = Gen3.export(program="GMKF", project="PCGC2016", id="1a9c0fd0-5a5b-4c70-b367-2a42f6c6df44")
    // val export = Gen3.export(program="INTERNAL", project="TEST", id="f7c1df63-c068-4a8c-91a0-23bfadf9b81f")
    // println(export)


    println("\n=======================\n  EXTRACTOR STARTING!  \n=======================\n\n")
    println(s"Start Time: ${Calendar.getInstance().getTime}")
    val ticker = new Ticker


    printBlock("FETCHING COUNTS:")

    // Get QTY of each node available
    val countsMap = getCounts()
    println(countsMap.mkString("\n").replaceAll("_count","").replaceAll(" ->", ":"))

    printBlock("COUNTS RECEIVED!")
    printTicker("Fetching Counts Time", ticker.interval)


    printBlock("SPARK STARTING:")

    val conf = new SparkConf().setAppName("KF-ETL").setMaster("local[4]")
    val sc = new SparkContext(conf)

    printBlock("SPARK STARTED!")
    printTicker("Spark Startup Time", ticker.interval)

    //make sure we have the directory before we let Spark run
    new File(savePath).mkdir()


    printBlock("SPARK EXECUTION:")

    val exports = sc.makeRDD(nodeList)
      .flatMap(nodeType => { getIdLists(nodeType, countsMap(countKeyword(nodeType))) })
      .map(record => { (record.nodeType, getExport(record)) })
      .groupByKey
      .map( tuple => (tuple._1, tuple._2.mkString("\n")) )
      .foreach(tuple => {
        val file = new File(s"$savePath/${tuple._1}$saveSuffix")
        val bw = new BufferedWriter(new FileWriter(file))

        if (Gen3.exportFormat.equals("tsv")) bw.write(getHeaders(tuple._1))

        bw.write(tuple._2)
        bw.close()
      })

    printBlock("SPARK EXECUTION COMPLETE!")
    printTicker("Spark Execution Time", ticker.interval)
    println(s"Files saved to: $savePath")



    printBlock("EXTRACTOR DONE!")
    println(s"End Time: ${Calendar.getInstance().getTime}")
    printTicker("Extractor Total Time", ticker.total)
    println("\nTiming Summary:\n")
    printTimeSummary
  }

  /* *************************
     PRETTY PRINTING HELPERS
   ************************* */

  private def printBlock(name: String): Unit = {
    println(s"\n=====\n===== $name\n=====")
  }

  private def printTicker(label: String, duration: Long): Unit = {
    println(s"$label: $duration ms")
    timingSteps = timingSteps :+ (label, duration)
  }

  private def printTimeSummary(): Unit = {
    println(timingSteps.map(tuple => s"${tuple._1}: ${tuple._2}").mkString("\n"))
  }

  /* **************
     GEN3 HELPERS
   ************** */

  private def getHeaders(nodeType: String): String = {
    val record = getRecords(nodeType, 1).apply(0)
    val response = Gen3.export(record.projectId, record.id)
    // Need to remove first line.
    response.replaceAll("\n.*$","\n")

  }

  private def getCounts(): Map[String, BigInt] = {
    val countkeys = nodeList.map(countKeyword)
    val query = s"{${countkeys.mkString(" ")}}"
    val response = Gen3.graphql(query)

    // Response should be of form:
    // { "data" : { "_case_count":123, ... } }

    val json = parse(response)

    (json \ "data").values.asInstanceOf[Map[String, BigInt]]
  }

  /**
    *
    * @param record
    * @return Data from the Gen3 Export for that document
    */
  private def getExport(record: Record): String = {
    val response = Gen3.export(record.projectId, record.id)
    // Need to remove first line.
    response.replaceAll("^.*\n","")
  }
  /**
    * Given node type and the number of elements that need to be fetched, this will use the Gen3 GraphQL API to fetch
    *  a Seq of Records
    * @param nodeType
    * @param qty
    * @return Record object (nodeType, id, projectId)
    */
  private def getRecords(nodeType: String, qty: BigInt): Seq[Record] = {
    val jsonData = getNodeData(nodeType, qty)

    for {
      JObject(caseRecord) <- (jsonData \ "data" \ nodeType)
      JField("id", JString(recordId)) <- caseRecord
      JField("project_id", JString(recordProject)) <- caseRecord
    } yield Record(nodeType, recordProject, recordId )
  }

  private def getIdLists(nodeType: String, qty: BigInt): Seq[Record] = {
    val listSize: Int = 35 // Determined through trial/error what the server limit is. 40 worked, but to be safe reduced to 35
    val records = getRecords(nodeType, qty)

    // get all the project names
    val projectsList = records.map(record=>record.projectId).distinct

    // group all records by project name
    val sortedRecords = projectsList.map(project=>records.filter(record=>record.projectId.equals(project)))

    sortedRecords.flatMap(projectList=>{
      // Collect each project list into groups of max size
      val collected = collectRecords(projectList, listSize)
      collected.map(collection=>{
        val first = collection.apply(0)
        val idString = collection.foldLeft("")((x, y)=>{if(x.length > 0) x + "," + y.id else y.id})
        Record(first.nodeType, first.projectId, idString)
      })
    })

  }

  private def collectRecords(list: Seq[Extractor.Record], groupSize: Int): Seq[Seq[Extractor.Record]] = {
    val numGroups = math.ceil(list.length / groupSize).toInt
    (0 to numGroups-1).map(group => {
      (0 to groupSize-1).map(i=>{
        list.apply(group*groupSize+i)
      })
    })
  }

  private def getNodeData(key: String, qty: BigInt): JValue = {
    val filters = Seq("(first:",qty,")").mkString
    val properties = Seq(id, projectId).mkString(" ")
    val query = Seq("{",key,filters,"{",properties,"}}").mkString
    val response = Gen3.graphql(query)
    parse(response)
  }

  private def countKeyword(key: String): String = {
    Seq("_", key, "_count").mkString
  }

}
