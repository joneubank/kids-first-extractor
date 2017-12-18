package com.joneubank.kf.gen3

import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.http.{HttpRequest, HttpResponse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.CharsetUtils
import sun.misc.HexDumpEncoder

import scala.io.Source
import scala.tools.asm.ByteVector

class Gen3Api(protocol: String, host: String, accessId: String, secretKey: String, apiVersion: String = "0") {

  private val apiBase = "/api/v" + apiVersion
  private val graphqlPath = "/submission/graphql/"
  private val signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date"

  private val DATE_FORMAT_SHORT = new SimpleDateFormat("YYYYMMdd")
  private val DATE_FORMAT_LONG = new SimpleDateFormat("YYYYMMdd'T'HHmmss'Z'")
  DATE_FORMAT_SHORT.setTimeZone(TimeZone.getTimeZone("UTC"))
  DATE_FORMAT_LONG.setTimeZone(TimeZone.getTimeZone("UTC"))

  private val CONTENT_TYPE_JSON = "application/json"


  def graphql(query:String): String = {
    val date = Calendar.getInstance().getTime()
    val longDate = DATE_FORMAT_LONG.format(date)
    val shortDate = DATE_FORMAT_SHORT.format(date)

    val service = "submission"

    val url = Seq(protocol, "://", host, apiBase, graphqlPath).mkString
    val body = "{\"query\":\"" + query + "\"}"

    // Create HMAC Signed Request:
    val request = new HttpPost(url)


    // 1. Set DATE header

    // 2. Encode body
    //   a. Set Entity with UTF-8 encoded byte stream of the body
    //   b. Add content-type header
    //   c. get hash of body, add hashed content header
    request.setEntity(new ByteArrayEntity(
      body.getBytes(CharsetUtils.get("UTF-8"))
    ))
    request.setHeader("content-type", CONTENT_TYPE_JSON)
    request.setHeader("host", host)
    val bodyHash = sha256Hash(body)
    request.setHeader("x-amz-content-sha256", bodyHash)
    request.setHeader("x-amz-date", longDate)

    // 3. scope
    val scope = shortDate +  "/" + service + "/" + "bionimbus_request"

    // 4. Build Authorization Header
    // Auth header has the format:
    //  HMAC-SHA256 Credential=htTEFx6D2CipaMTO17ZA/20171218/submission/bionimbus_request, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, Signature=3d79df4084b6741997f3c21a838420f82c5fd130bef27cec1324d590d3730331
    //  {Alogirthm} Credential={accessId}/{scope}, SignedHeaders={signedHeaders}, Signature={signature}

    // Need Canonincal Request string to help generate Signature
    //  Canonical request is a description of the request (Method, URL, Headers, Body hash):
    /*
      POST
      /api/v0/submission/graphql/

      content-type:application/json
      host:gen3.kids-first.io
      x-amz-content-sha256:865a75b3794ab50e7565b72e338ef0c8ea74fa564cdeba7797eacd78b2ebd317
      x-amz-date:20171218T180847Z

      content-type;host;x-amz-content-sha256;x-amz-date
      865a75b3794ab50e7565b72e338ef0c8ea74fa564cdeba7797eacd78b2ebd317
     */
    val canonicalRequest = buildCanonicalRequest(
      request=request,
      method="POST",
      url=Seq(apiBase, graphqlPath).mkString,
      bodyHash=bodyHash
    )

    // Signature is last part of the Auth header
    //
    val signature = generateSignature(
      canonicalRequest=canonicalRequest,
      service="submission",
      scope=scope,
      shortDate=shortDate,
      longDate=longDate
    )

    val authValue = buildAuthHeader(
      scope=scope,
      signature=signature
    )
    // Add Authorization Header:
    request.setHeader("Authorization", authValue)

    val response = HttpClients.createDefault().execute(request)

    Source.fromInputStream(response.getEntity().getContent()).getLines.mkString
//    "harddcoded response"
  }

  private def buildAuthHeader(scope: String, signature: String): String = {
    "HMAC-SHA256 Credential="+accessId+"/"+scope+", SignedHeaders="+signedHeaders+", Signature="+signature
  }


  private def buildCanonicalRequest(request: HttpRequest, method: String, url: String, bodyHash: String): String = {
    val queryString = "" //Not used in our simple case, put here in case needed later.
    val canonicalHeaders = buildCanonicalHeaders(request)
    Seq(method.toUpperCase, url,queryString, canonicalHeaders, signedHeaders, bodyHash).mkString("\n")
  }

  private def buildCanonicalHeaders(request: HttpRequest): String = {
    request.getAllHeaders.
      map( header => Seq(header.getName,header.getValue).mkString(":") +"\n" ).
      reduceLeft(_ + _)
  }

  private def generateSignature(canonicalRequest: String, service: String, scope: String, shortDate: String, longDate: String): String = {
    val canonicalRequestHash = sha256Hash(canonicalRequest)
    val requestSignature = Seq("HMAC-SHA256", longDate, scope, canonicalRequestHash).mkString("\n")
    val authKey = getAuthKey(shortDate, service)
    hexDigest(hmacHash(authKey, requestSignature))
  }


  private def getAuthKey(date: String, service: String) : Array[Byte] = {

    // The hashing process to generate the required Authorization token is build to mimic
    //  the python hmac auth lib from University of Chicago's Center for Data Intensive Science
    //  which is in turn based on the AWS implementation
    // Link to the specific reference:
    //  https://github.com/uc-cdis/cdis-python-utils/blob/master/cdispyutils/hmac4/hmac4_signing_key.py
    val dateKey = hmacHash(("HMAC4"+secretKey).getBytes, date)
    val serviceKey = hmacHash(dateKey, service)
    hmacHash(serviceKey, "hmac4_request")
  }


  private def hmacHash(key: Array[Byte], msg: String): Array[Byte] = {
    val keySpec = new SecretKeySpec(key, "HmacSHA256")

    val mac = Mac.getInstance("HmacSHA256")
    mac.init(keySpec)

    mac.doFinal(msg.getBytes)
  }

  private def sha256Hash(text: String): String = {

    val hash = java.security.MessageDigest.getInstance("SHA-256").digest( text.getBytes("UTF-8") )
    hexDigest(hash)
  }

  private def hexDigest(bytes: Array[Byte]): String = {
    // Hex Digest equivalent, thanks to https://stackoverflow.com/questions/2756166/what-is-are-the-scala-ways-to-implement-this-java-byte-to-hex-class
    bytes.map("%02x" format _).mkString
  }

}
