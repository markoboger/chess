package chess.aview.richTui

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TuiShellSpec extends AnyWordSpec with Matchers:

  "TuiShell" should {
    "create an initial session when requested" in {
      val shell = TuiShell()

      shell.ensureInitialSessionForTest()

      shell.snapshot.activeGameId should not be empty
      shell.snapshot.statusMessage should include ("Created game")
    }

    "open menus from numeric main input" in {
      val shell = TuiShell()

      shell.processInputForTest("1")

      shell.snapshot.activeMenuTitle shouldBe Some("Game")
      shell.snapshot.statusMessage should include ("Opened game menu")
    }

    "close the active menu with 0" in {
      val shell = TuiShell()
      shell.processInputForTest("1")

      shell.processInputForTest("0")

      shell.snapshot.activeMenuTitle shouldBe None
      shell.snapshot.statusMessage should include ("Closed game menu")
    }

    "toggle board flip through the hotkey" in {
      val shell = TuiShell()

      shell.processInputForTest("l")

      shell.snapshot.flipped shouldBe true
      shell.snapshot.statusMessage shouldBe "Board flipped: true"
    }

    "open the sessions overlay with the hotkey" in {
      val shell = TuiShell()
      shell.ensureInitialSessionForTest()

      shell.processInputForTest("s")

      shell.snapshot.infoOverlayTitle shouldBe Some("Sessions")
      shell.snapshot.statusMessage should include ("Showing")
    }

    "open the help overlay through the help menu" in {
      val shell = TuiShell()

      shell.helpMenu("1") shouldBe true

      shell.snapshot.statusMessage should include ("Moves are direct")
    }

    "open the game mode prompt through the game menu" in {
      val shell = TuiShell()

      shell.gameMenu("1") shouldBe true

      shell.snapshot.pendingPromptTitle shouldBe Some("Game Mode")
      shell.snapshot.infoOverlayTitle shouldBe Some("New Game Mode")
    }

    "open the AI strategy prompt through the game menu" in {
      val shell = TuiShell()

      shell.gameMenu("5") shouldBe true

      shell.snapshot.pendingPromptTitle shouldBe Some("AI Strategy")
      shell.snapshot.infoOverlayTitle shouldBe Some("AI Strategies")
    }

    "switch JSON backend through the import/export menu" in {
      val shell = TuiShell()

      shell.importExportMenu("7") shouldBe true

      shell.snapshot.jsonFlavor shouldBe "upickle"
      shell.snapshot.statusMessage should include ("uPickle")
    }

    "reject unknown menu choices cleanly" in {
      val shell = TuiShell()

      shell.gameMenu("99") shouldBe false
      shell.moveMenu("99") shouldBe false
      shell.historyMenu("99") shouldBe false
      shell.importExportMenu("99") shouldBe false
      shell.sessionMenu("99") shouldBe false
      shell.helpMenu("99") shouldBe false
    }

    "create the configured game mode from the pending prompt" in {
      val shell = TuiShell()

      shell.gameMenu("1")
      shell.processInputForTest("1")

      shell.snapshot.pendingPromptTitle shouldBe None
      shell.snapshot.activeGameId should not be empty
      shell.snapshot.statusMessage should include ("Created game")
    }

    "join-session prompt should reject unknown sessions" in {
      val shell = TuiShell()

      shell.sessionMenu("2") shouldBe true
      shell.processInputForTest("missing-session")

      shell.snapshot.pendingPromptTitle shouldBe None
      shell.snapshot.statusMessage should include ("Unknown session")
    }

    "close overlays with :cancel" in {
      val shell = TuiShell()
      shell.ensureInitialSessionForTest()
      shell.processInputForTest("s")

      shell.processInputForTest(":cancel")

      shell.snapshot.infoOverlayTitle shouldBe None
      shell.snapshot.statusMessage shouldBe "Closed overlay."
    }
  }
