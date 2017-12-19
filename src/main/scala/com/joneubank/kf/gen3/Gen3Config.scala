package com.joneubank.kf.gen3

import java.io.FileReader

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

object Gen3Config {

  private val reader = new FileReader("resources/gen3.yml")
  private val mapper = new ObjectMapper(new YAMLFactory())
  private val config: ConfigFile = mapper.readValue(reader, classOf[ConfigFile])

  val id = config.id
  val secret = config.secret
  val host = config.host
  val protocol = config.protocol
  val version = config.version

  class ConfigFile(
                      @JsonProperty("id") _id: String,
                      @JsonProperty("secret") _secret: String,
                      @JsonProperty("host") _host: String,
                      @JsonProperty("protocol") _protocol: String,
                      @JsonProperty("version") _version: String) {
    val id = _id;
    val secret = _secret;
    val host = _host;
    val protocol = _protocol;
    val version = _version;
  }
}
