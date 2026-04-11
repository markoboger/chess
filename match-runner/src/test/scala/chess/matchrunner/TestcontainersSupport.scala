package chess.matchrunner

object TestcontainersSupport:
  private val DockerDesktopPipe = "npipe:////./pipe/dockerDesktopLinuxEngine"
  private val NpipeStrategy = "org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy"

  def configureDockerDesktopIfNeeded(osName: String = System.getProperty("os.name")): Unit =
    if Option(osName).exists(_.toLowerCase.contains("windows")) then
      if sys.env.get("DOCKER_HOST").forall(_.trim.isEmpty) then
        System.setProperty("docker.host", DockerDesktopPipe)
      System.setProperty("docker.client.strategy", NpipeStrategy)
