package com.joneubank.kf

import com.joneubank.kf.gen3.Gen3

object Main extends App {


  override def main(args: Array[String]): Unit = {

    val response = Gen3.graphql("{program{id}}")
    println(response)

    // Get all node lists from gen3

    // For each node type
    //   For each id
    //     fetch document
    //     append document to file

    // Save all files

  }


}