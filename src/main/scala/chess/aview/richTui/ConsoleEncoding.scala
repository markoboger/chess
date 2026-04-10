package chess.aview.richTui

import java.io.{FileDescriptor, FileOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

private[chess] object ConsoleEncoding:
  private val Utf8 = StandardCharsets.UTF_8

  def enableUtf8IfNeeded(osName: String = System.getProperty("os.name")): Unit =
    if isWindows(osName) then
      setUtf8SystemProperties()
      switchWindowsCodePage()
      rebindStandardStreams()

  def preferUnicodeConsolePieces(osName: String = System.getProperty("os.name")): Boolean =
    !isWindows(osName)

  private[chess] def isWindows(osName: String): Boolean =
    Option(osName).exists(_.toLowerCase.contains("windows"))

  private def setUtf8SystemProperties(): Unit =
    System.setProperty("file.encoding", Utf8.name())
    System.setProperty("sun.stdout.encoding", Utf8.name())
    System.setProperty("sun.stderr.encoding", Utf8.name())
    System.setProperty("stdout.encoding", Utf8.name())
    System.setProperty("stderr.encoding", Utf8.name())

  private def switchWindowsCodePage(): Unit =
    try
      new ProcessBuilder("cmd", "/c", "chcp 65001>nul").inheritIO().start().waitFor()
    catch
      case _: Throwable => ()

  private def rebindStandardStreams(): Unit =
    try
      System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, Utf8))
      System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, Utf8))
    catch
      case _: Throwable => ()
