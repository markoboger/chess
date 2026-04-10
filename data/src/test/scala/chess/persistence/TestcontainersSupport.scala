package chess.persistence

object TestcontainersSupport:
  private val DockerDesktopPipe = "npipe:////./pipe/dockerDesktopLinuxEngine"
  private val NpipeStrategy = "org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy"

  def configureDockerDesktopIfNeeded(osName: String = System.getProperty("os.name")): Unit =
    if isWindows(osName) then
      if sys.env.get("DOCKER_HOST").forall(_.trim.isEmpty) then
        System.setProperty("docker.host", DockerDesktopPipe)
      System.setProperty("docker.client.strategy", NpipeStrategy)

  private def isWindows(osName: String): Boolean =
    Option(osName).exists(_.toLowerCase.contains("windows"))
