package com.horizen.fixtures

import akka.http.javadsl.model.headers.{BasicHttpCredentials, HttpCredentials}
import at.favre.lib.crypto.bcrypt.BCrypt


trait BasicAuthenticationFixture {

  def getBasicAuthCredentials(username: String = "username", password: String = "password"): BasicHttpCredentials = {
    HttpCredentials.createBasicHttpCredentials(username, password)
  }

  def getBasicAuthApiKeyHash(password: String, bcryptCostAlgorithm: Int = 8): String = {
    //Algorithm cost, higher is the number, higher is the round in the algorithm and the time to hash/verify the password
    BCrypt.`with`(BCrypt.Version.VERSION_2Y).hashToString(bcryptCostAlgorithm, password.toCharArray)
  }
}
