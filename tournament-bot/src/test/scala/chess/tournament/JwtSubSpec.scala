package chess.tournament

import org.scalatest.funsuite.AnyFunSuite

class JwtSubSpec extends AnyFunSuite:

  test("subject decodes sub from JWT payload") {
    // header.payload.sig — payload is {"sub":"bot_abc123","isBot":true}
    val payload = "eyJzdWIiOiJib3RfYWJjMTIzIiwiaXNCb3QiOnRydWV9"
    val token = s"aaa.$payload.bbb"
    assert(JwtSub.subject(token) == Some("bot_abc123"))
  }

  test("subject returns None for malformed token") {
    assert(JwtSub.subject("not-a-jwt") == None)
  }

end JwtSubSpec
