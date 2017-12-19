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

    Source.fromInputStream(response.getEntity().getContent()).getLines.mkString
  }

  /* ****************
     HELPER METHODS
   **************** */

  private def exportPath(program: String, project: String): String = {
    Seq("/submission", program, project, "export").mkString("/")
  }

  private def exportQueryString(id: String): String = {
    Seq("format=tsv&ids=", id).mkString
  }

}
