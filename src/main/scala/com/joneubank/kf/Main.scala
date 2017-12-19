package com.joneubank.kf

import com.joneubank.kf.gen3.Gen3

object Main extends App {


  override def main(args: Array[String]): Unit = {

    val response = Gen3.graphql("{program{id}}")
    println(response)

    val export = Gen3.export(program="GMKF", project="PCGC2016", id="1a9c0fd0-5a5b-4c70-b367-2a42f6c6df44")
    println(export)

    // Get all node lists from gen3

    // For each node type
    //   For each id
    //     fetch document
    //     append document to file

    // Save all files

  }


}