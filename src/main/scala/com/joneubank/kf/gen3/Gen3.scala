package com.joneubank.kf.gen3

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

    val request = Gen3Auth.makeRequest(path, body)

    val response = HttpClients.createDefault().execute(request)

    Source.fromInputStream(response.getEntity().getContent()).getLines.mkString
  }

}
