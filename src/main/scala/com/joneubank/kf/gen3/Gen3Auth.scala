package com.joneubank.kf.gen3

import java.text.SimpleDateFormat
import java.util.{Calendar, TimeZone}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.http.HttpRequest
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.util.CharsetUtils


object Gen3Auth {

  /* ******************************
     SUPER HAPPY LITTLE CONSTANTS
   ****************************** */
  private val signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date"

  private val timeZoneUtc = TimeZone.getTimeZone("UTC")

  private val dateFormatShort = new SimpleDateFormat("YYYYMMdd")
  dateFormatShort.setTimeZone(timeZoneUtc)
  private val dateFormatLong = new SimpleDateFormat("YYYYMMdd'T'HHmmss'Z'")
  dateFormatLong.setTimeZone(timeZoneUtc)

  private val contentTypeJson = "application/json"
  private val encodingUtf8 = "UTF-8"
  private val charsetUtf8 = CharsetUtils.get(encodingUtf8)

  private val algorithmHMAC = "HmacSHA256"

  // Service probably shouldnt be a constant - but right now we only need
  //  requests to the submission service.
  //  So until refactors are required, let it be constant!
  private val service = "submission"


  /* ****************
     REQUEST METHOD
   **************** */

  /**
    * Prepares an HttpPOST Object that is Signed to be accepted by the Kids First Gen3 Submission API.
    * This does not send the HTTP request, just prepares the object.
    *
    * This process is slightly hardcoded as currently setup. It assumes the desired service is "submission", and that
    *  no headers are needed other than those created for signing. Modifications are required to support other services
    *  or additional headers.
    *
    * The content-type will be set to "application/json", modifications required if other payloads are needed.
    *
    * @param path URI from after the host. Start with "/" Example: "/api/v0/submission/graphql"
    * @param body String representation of JSON
    * @return HttpPost object ready to be sent, with headers set for Authorization to the submission service
    */
  def makeRequest(path: String, body: String): HttpPost = {

    /*
     * Process:
     * 1. Constants used to build request
     *     (formatted dates, url, body and body hash, scope)
     * 2. Set Signed Headers
     *     (content-type, host, x-amz-content-sha256, x-amz-date)
     * 3. Canonical Request
     *     used for generating signature
     * 4. Signature
     *     used for Authorization header, requires Canonical Request
     * 5. Authorization header value
     *     requires Signature. Added as header.
     * 6. RETURN!
     */

    // ===== 1. CONSTANTS ======
    // =========================
    val date = Calendar.getInstance().getTime
    val longDate = dateFormatLong.format(date)
    val shortDate = dateFormatShort.format(date)

    val url = Seq(Gen3Config.protocol, "://", Gen3Config.host, path).mkString

    val request = new HttpPost(url)

    request.setEntity(new ByteArrayEntity(
      body.getBytes(charsetUtf8)
    ))

    // bodyHash is used for content header and for canonicalRequest
    val bodyHash = sha256Hash(body)

    // scope is used for
    val scope = buildScope(shortDate)


    // ===== 2. HEADERS ======
    // =======================
    // Order is important (alphabetical)
    // ... unless we sort them in the canonicalRequest step - but that's not done now so just mind the order
    request.setHeader("content-type", contentTypeJson)
    request.setHeader("host", Gen3Config.host)
    request.setHeader("x-amz-content-sha256", bodyHash)
    request.setHeader("x-amz-date", longDate)


    // ===== 3. Canonical Request ======
    // =================================

    val canonicalRequest = buildCanonicalRequest(
      request=request,
      method="POST",
      uri=path,
      bodyHash=bodyHash
    )

    // ===== 4. Signature ======
    // =========================
    val signature = generateSignature(
      canonicalRequest=canonicalRequest,
      service="submission",
      scope=scope,
      shortDate=shortDate,
      longDate=longDate
    )


    // ===== 5. Authorization Header ======
    // ====================================
    val authValue = buildAuthHeader(
      scope=scope,
      signature=signature
    )
    request.setHeader("Authorization", authValue)

    // ===== 6. RETURN! ======
    // =======================
    request
  }


  /* ****************
     HELPER METHODS
   **************** */

  /**
    * Scope is a string used in the auth header and for calculating the signature.
    * No explanation for the "bionimbus_request" literal... that is taken from the gen3 signing code.
    *
    * Format: {shortDate}/{service}/bionimbus_request
    * Example: 20171218/submission/bionimbus_request
    * @param shortDate YYYYMMdd
    * @return Arbitrary String value of scope
    */
  private def buildScope(shortDate: String): String = {
    Seq(shortDate, service, "bionimbus_request").mkString("/")
  }

  /**
    * Constructs the value used for "Authorization" header, built from a bunch of constants plus pre calculated
    *  scope and signature values
    * Format: {Alogirthm} Credential={accessId}/{scope}, SignedHeaders={signedHeaders}, Signature={signature}
    * Example: HMAC-SHA256 Credential=htTEFx6D2CipaMTO17ZA/20171218/submission/bionimbus_request, SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, Signature=3d79df4084b6741997f3c21a838420f82c5fd130bef27cec1324d590d3730331
    * @param scope from buildScope() method
    * @param signature from generateSignature() method
    * @return Value to use for "Authorization" header
    */
  private def buildAuthHeader(scope: String, signature: String): String = {
    Seq(
      "HMAC-SHA256",
      " Credential=", Gen3Config.id, "/", scope,
      ", SignedHeaders=", signedHeaders,
      ", Signature=", signature
    ).mkString
  }

  /**
    * Make summary of the request, will be used generating signature
    *
    * Example:
      POST
      /api/v0/submission/graphql/

      content-type:application/json
      host:gen3.kids-first.io
      x-amz-content-sha256:865a75b3794ab50e7565b72e338ef0c8ea74fa564cdeba7797eacd78b2ebd317
      x-amz-date:20171218T180847Z

      content-type;host;x-amz-content-sha256;x-amz-date
      865a75b3794ab50e7565b72e338ef0c8ea74fa564cdeba7797eacd78b2ebd317
    *
    * @param request Request object, will take headers from here
    * @param method "POST" or "GET" or similar
    * @param uri path only, not protocol/host
    * @param bodyHash sha256 hash of body content
    * @return Long string which summarizes the HTTPRequest as needed for signing
    */
  private def buildCanonicalRequest(request: HttpRequest, method: String, uri: String, bodyHash: String): String = {
    val queryString = "" // Not used in our simple case, put here in case needed later.
    val canonicalHeaders = buildCanonicalHeaders(request)
    Seq(
      method.toUpperCase,
      uri,
      queryString, // <- this is the first blank line in the example
      canonicalHeaders, // <- Each of these ends with a \n, then we seperate with another \n (second blank line in example)
      signedHeaders,
      bodyHash
    ).mkString("\n")
  }

  /**
    * String of all headers in request and their values, seperated by new lines
    * Example:
      content-type:application/json
      host:gen3.kids-first.io
      x-amz-content-sha256:865a75b3794ab50e7565b72e338ef0c8ea74fa564cdeba7797eacd78b2ebd317
      x-amz-date:20171218T180847Z
    *
    * @param request Request object, all headers will be taken from here
    * @return Canonical Headers String
    */
  private def buildCanonicalHeaders(request: HttpRequest): String = {
    request.getAllHeaders.
      map( header => Seq(header.getName,header.getValue).mkString(":") +"\n" ).
      reduceLeft(_ + _)
  }

  /**
    * Signature is the final variable in the Authorization header value.
    * It is generated from a HMAC hash of the requestSignature and the authKey, both of which are generated here.
    *  - requestSignature is a long string combining algorithm name, longDate, scope,
    *     and a SHA hash of the canonicalRequest
    *  - authKey is calutated from the secretKey, shortDate, service name, and some String literals, using a repeated
    *     HMAC hashing process. See getAuthKey(...)
    * @param canonicalRequest from buildCanonicalRequest() method
    * @param service String name of the service being requested - "submission" for Kids First Gen3
    * @param scope from buildScope() method
    * @param shortDate Date as YYYYMMdd
    * @param longDate Date as YYYYMMdd'T'HHmmss'Z'
    * @return Hexadecimal String of the request Signature - for use in Authorization header
    */
  private def generateSignature(canonicalRequest: String, service: String, scope: String, shortDate: String, longDate: String): String = {
    val canonicalRequestHash = sha256Hash(canonicalRequest)
    val requestSignature = Seq("HMAC-SHA256", longDate, scope, canonicalRequestHash).mkString("\n")
    val authKey = getAuthKey(shortDate, service)
    hexDigest(hmacHash(authKey, requestSignature))
  }

  /**
    * Generates the authKey - Used for calculating the Signature value
    *
    * Generation process is repeated HMAC hashing of values, most importantly the secret key.
    * @param shortDate YYYYMMdd formatted date
    * @param service Service name being requested - for Gen3 extract = "submission"
    * @return authKey as Array[Byte] (usable in the hmacHash method)
    */
  private def getAuthKey(shortDate: String, service: String) : Array[Byte] = {

    // The hashing process to generate the required Authorization token is build to mimic
    //  the python hmac auth lib from University of Chicago's Center for Data Intensive Science
    //  which is in turn based on the AWS implementation
    // Link to the specific reference:
    //  https://github.com/uc-cdis/cdis-python-utils/blob/master/cdispyutils/hmac4/hmac4_signing_key.py
    val dateKey = hmacHash(("HMAC4"+Gen3Config.secret).getBytes, shortDate)
    val serviceKey = hmacHash(dateKey, service)
    hmacHash(serviceKey, "hmac4_request")
  }

  /* ************
     HASH STUFF
   ************ */

  /**
    * Get hashed message using HMAC scheme
    * @param key Signing key, as Array[Byte]
    * @param msg Message to apply hash algorithm to
    * @return Byte Array of hashed message
    */
  private def hmacHash(key: Array[Byte], msg: String): Array[Byte] = {
    val keySpec = new SecretKeySpec(key, algorithmHMAC)

    val mac = Mac.getInstance(algorithmHMAC)
    mac.init(keySpec)

    mac.doFinal(msg.getBytes)
  }


  /**
    * Return SHA-256 hash of String. For convenience, result is returned as hexadecimal String
    *  using hexDigest
    * @param text Text to hash
    * @return hexadecimal String representation of the SHA-256 hash
    */
  private def sha256Hash(text: String): String = {
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest( text.getBytes(encodingUtf8) )
    hexDigest(hash)
  }

  /**
    * Convert Array[Byte] to String of hexadecimal values
    * - useful for transforming hashes to values for headers
    * @param bytes Byte array to convert to String
    * @return hexadecimal String representation of Bytes Array
    */
  private def hexDigest(bytes: Array[Byte]): String = {
    // Hex Digest equivalent, thanks to https://stackoverflow.com/questions/2756166/what-is-are-the-scala-ways-to-implement-this-java-byte-to-hex-class
    bytes.map("%02x" format _).mkString
  }

}
