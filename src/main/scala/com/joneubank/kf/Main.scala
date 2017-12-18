package com.joneubank.kf

import com.joneubank.kf.gen3.Gen3Api

object Main extends App {


  override def main(args: Array[String]): Unit = {

    // Get all node lists from gen3

    // For each node type
    //   For each id
    //     fetch document
    //     append document to file

    // Save all files

    val gen3 = new Gen3Api(
      protocol="https",
      host="gen3.kids-first.io",
      accessId = "n2qUuqy3gKaQB6Klm6Bp",
      secretKey = "Gb5GVeWkdnRnrexxa02dPoNHxc1NUHJkADmPWWme"
    )

    val response = gen3.graphql("{program{id}}")
    println(response)

  }


}