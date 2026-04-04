package chess.microservices.gateway

import cats.effect.IO
import org.http4s.*
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`

/** Service proxy for forwarding requests to backend services
  */
class ServiceProxy(client: Client[IO]):

  /** Proxy a request to a backend service
    *
    * @param request
    *   The incoming request
    * @param targetUri
    *   The target backend service URI
    * @return
    *   The backend response
    */
  def proxy(request: Request[IO], targetUri: Uri): IO[Response[IO]] =
    val targetRequest = request.withUri(targetUri)

    client.run(targetRequest).use { response =>
      // Copy the response from the backend service
      IO.pure(
        Response[IO](
          status = response.status,
          headers = response.headers,
          body = response.body
        )
      )
    }

  /** Build target URI for proxying
    *
    * @param baseUri
    *   Base URI of the target service
    * @param path
    *   Request path
    * @param queryString
    *   Optional query string
    * @return
    *   Complete target URI
    */
  def buildTargetUri(baseUri: Uri, path: String, queryString: Option[String] = None): Uri =
    val uri = baseUri.withPath(Uri.Path.unsafeFromString(path))
    queryString.fold(uri)(qs => uri.copy(query = Query.unsafeFromString(qs)))
