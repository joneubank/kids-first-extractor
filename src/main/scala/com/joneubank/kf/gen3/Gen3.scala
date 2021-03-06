package com.joneubank.kf.gen3

import javax.ws.rs.HttpMethod

import org.apache.http.impl.client.HttpClients

import scala.io.Source

/**
  *
  */
object Gen3 {

  private val apiBase = "/api/v" + Gen3Config.version
  private val graphqlPath = "/submission/graphql/"

  var exportFormat = "json"

  /**
    *
    * @param query
    * @return
    */
  def graphql(query: String): String = {
    val path = Seq(apiBase, graphqlPath).mkString
    val body = "{\"query\":\"" + query + "\"}"

    val request = Gen3Auth.makeRequest(method=HttpMethod.POST, path=path, query="", body=body)

    val response = HttpClients.createDefault().execute(request)

    Source.fromInputStream(response.getEntity().getContent()).getLines.mkString
  }

  /**
    *
    * @param project
    * @param program
    * @param id
    * @return
    */
  def export(program: String, project: String, id: String): String = {
    val path = Seq(apiBase, exportPath(program, project)).mkString
    val query = exportQueryString(id)

    val request = Gen3Auth.makeRequest(method=HttpMethod.GET, path=path, query=query, body="")

    val response = HttpClients.createDefault().execute(request)

    val output = Source.fromInputStream(response.getEntity().getContent()).getLines.mkString("\n")

    if(exportFormat.equals("json")) simplifyExportJson(output) else output
  }

  def simplifyExportJson(formattedJson: String): String = {
    formattedJson
      .replaceAll("\\n]","")
      .replaceAll("^\\[\\n","")
      .replaceAll("\\}, \\n  \\{","}!#%#!{")
      .replaceAll("\\s\\s+"," ")
      .replaceAll("!#%#!","\n")
  }

  /**
    * Useful method signature, pass in a composite project ID as they are returned by the GraphQl responses,
    *  this will break up the ID into program and project and pass those values to the export method
    * @param project_id
    * @param id
    * @return
    */
  def export(project_id: String, id: String): String = {
    val split = project_id.split("-")
    export(program=split.apply(0), project=split.apply(1), id=id)
  }

  /* ****************
     HELPER METHODS
   **************** */

  private def exportPath(program: String, project: String): String = {
    Seq("/submission", program, project, "export").mkString("/")
  }

  private def exportQueryString(id: String): String = {
    Seq(s"format=${exportFormat}&ids=", id).mkString
  }

}
