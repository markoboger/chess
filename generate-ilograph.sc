//> using scala "3.8.3"

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

final case class BuildMeta(organization: String, version: String, scalaVersion: String)

final case class SbtProject(
    id: String,
    dir: String,
    displayName: Option[String],
    dependsOn: List[String]
)

final case class MemberMeta(kind: String, decl: String, doc: Option[String], publicDefs: Vector[String])

final case class PackageGraph(
    packages: Set[String],
    edges: Map[String, Set[String]],
    members: Map[String, Map[String, MemberMeta]], // package -> (member name -> metadata)
    memberCalls: Map[String, Set[String]],         // memberId -> memberId (inferred from constructor param types)
    memberImplements: Map[String, Set[String]]     // parent memberId -> child memberId (inferred from extends/with)
    ,
    memberObserves: Map[String, Set[String]]       // observer memberId -> observed memberId (from extends Observer[T])
    ,
    memberCompanions: List[(String, String)]       // (type memberId -> companion object memberId)
)

object GenerateIlograph:
  private final case class ProjectScanData(
      packages: Set[String],
      edges: Map[String, Set[String]],
      members: Map[String, Map[String, MemberMeta]],
      inherits: Map[String, Set[String]], // memberId -> qualified/unqualified parent type tokens
      observes: Map[String, Set[String]], // memberId -> raw observed type strings (inside Observer[...])
      companions: List[(String, String)], // (type memberId -> companion object memberId)
      scalaFiles: List[Path]
  )
  private val TimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
  private val ProjectPalette: Vector[String] = Vector(
    "#1f77b4", // blue
    "#ff7f0e", // orange
    "#2ca02c", // green
    "#d62728", // red
    "#9467bd", // purple
    "#8c564b", // brown
    "#e377c2", // pink
    "#7f7f7f", // gray
    "#bcbd22", // yellow-green
    "#17becf"  // cyan
  )

  private val OrgRe: Regex = """(?m)^\s*ThisBuild\s*/\s*organization\s*:=\s*"([^"]+)"""".r
  private val VerRe: Regex = """(?m)^\s*ThisBuild\s*/\s*version\s*:=\s*"([^"]+)"""".r
  private val ScalaVerRe: Regex = """(?m)^\s*ThisBuild\s*/\s*scalaVersion\s*:=\s*"([^"]+)"""".r

  private val ProjectStartRe: Regex = """(?m)^\s*lazy\s+val\s+([A-Za-z0-9_]+)\s*=\s*project\b""".r
  private val InDirRe: Regex = """(?s)\.in\s*\(\s*file\("([^"]+)"\)\s*\)""".r
  private val NameSettingRe: Regex = """(?s)\bname\s*:=\s*"([^"]+)"""".r
  private val DependsOnRe: Regex = """(?s)\.dependsOn\s*\(\s*([^)]+?)\s*\)""".r
  private val PackageRe: Regex = """(?m)^\s*package\s+([\w\.]+)""".r
  private val ImportLineRe: Regex = """(?m)^\s*import\s+([^\n]+)""".r
  // Note: this intentionally matches *indented* definitions too (Scala 3 significant indentation),
  // so we can surface nested `class`/`object`/`enum`/`trait` members as their own resources.
  private val TopLevelDefRe: Regex =
    """(?m)^\s*((?:final\s+|sealed\s+|abstract\s+|case\s+|private\s+|protected\s+|open\s+|transparent\s+|inline\s+)*)\b(class|trait|object|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b""".r

  private val TopLevelDefWithCtorRe: Regex =
    """(?m)^\s*((?:final\s+|sealed\s+|abstract\s+|case\s+|private\s+|protected\s+|open\s+|transparent\s+|inline\s+)*)\b(class|trait|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(\([^=\n]*\))?""".r

  // Match constructor-bearing definitions up to the name (params are parsed separately, possibly multiline).
  private val CtorHeaderRe: Regex =
    """(?m)^\s*((?:final\s+|sealed\s+|abstract\s+|case\s+|private\s+|protected\s+|open\s+|transparent\s+|inline\s+)*)\b(class|trait|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b""".r

  // Icon taxonomy is strictly by artifact category:
  // project, package, class, trait, object, enum.
  // Temporary debugging: force a single icon everywhere to verify rendering.
  private val ProjectIcon = "Networking/database.svg"
  private val PackageIcon = "Networking/firewall.svg"
  private val ClassIcon = "Networking/database.svg"
  private val TraitIcon = "Networking/router.svg"
  private val ObjectIcon = "Networking/vpn.svg"
  private val EnumIcon = "Networking/database.svg"

  def main(args: Array[String]): Unit =
    val repoRoot = Paths.get(".").toAbsolutePath.normalize()
    val buildSbt = repoRoot.resolve("build.sbt")
    val docsDir = repoRoot.resolve("docs")

    require(Files.exists(buildSbt), s"Missing build.sbt at $buildSbt")
    require(Files.isDirectory(docsDir), s"Missing docs directory at $docsDir")

    val buildText = Files.readString(buildSbt, StandardCharsets.UTF_8)
    val meta = parseMeta(buildText)
    val projects = parseProjects(buildText)
    val scans = projects.map(p => p.id -> scanProjectSources(repoRoot.resolve(p.dir))).toMap

    // Build a global index of all discovered members across all projects so `calls` can resolve
    // cross-module dependencies (e.g. Data -> Core types).
    val globalMembersByPkg =
      scans.values.foldLeft(Map.empty[String, Map[String, MemberMeta]]) { (acc, scan) =>
        scan.members.foldLeft(acc) { case (acc2, (pkg, ms)) =>
          acc2.updated(pkg, acc2.getOrElse(pkg, Map.empty) ++ ms)
        }
      }

    val globalIndex = buildMemberIndex(globalMembersByPkg)

    val packageGraphs =
      scans.map { case (pid, scan) =>
        val memberCalls = scanMemberCalls(scan.scalaFiles, scan.packages, globalIndex)
        val memberImplements = resolveImplements(scan.inherits, globalIndex)
        val memberObserves = resolveObserves(scan.observes, globalIndex)
        pid -> PackageGraph(
          packages = scan.packages,
          edges = scan.edges,
          members = scan.members,
          memberCalls = memberCalls,
          memberImplements = memberImplements,
          memberObserves = memberObserves,
          memberCompanions = scan.companions
        )
      }
    val projectColors = projects.map(p => p.id -> colorForProject(p.id)).toMap

    val timestamp = LocalDateTime.now().format(TimeFmt)
    val outPath = docsDir.resolve(s"ilo_$timestamp.yaml")

    val yaml = renderYaml(meta, projects, packageGraphs, projectColors, timestamp)
    Files.writeString(outPath, yaml, StandardCharsets.UTF_8)
    println(s"Wrote ${repoRoot.relativize(outPath)}")

  private def parseMeta(buildText: String): BuildMeta =
    def required(re: Regex, label: String): String =
      re.findFirstMatchIn(buildText).map(_.group(1)).getOrElse {
        throw new RuntimeException(s"Could not find $label in build.sbt")
      }

    BuildMeta(
      organization = required(OrgRe, "ThisBuild / organization"),
      version = required(VerRe, "ThisBuild / version"),
      scalaVersion = required(ScalaVerRe, "ThisBuild / scalaVersion")
    )

  private def parseProjects(buildText: String): List[SbtProject] =
    val starts = ProjectStartRe.findAllMatchIn(buildText).toList
    val spans =
      starts.zipWithIndex.map { case (m, i) =>
        val start = m.start
        val end = if i + 1 < starts.length then starts(i + 1).start else buildText.length
        (m.group(1), buildText.substring(start, end))
      }

    spans.map { case (id, block) =>
      val dir = InDirRe.findFirstMatchIn(block).map(_.group(1)).getOrElse(".")
      val name = NameSettingRe.findFirstMatchIn(block).map(_.group(1))
      val depsRaw = DependsOnRe.findFirstMatchIn(block).map(_.group(1)).getOrElse("")
      val deps =
        depsRaw
          .split(',')
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(_.takeWhile(ch => ch.isLetterOrDigit || ch == '_' ))
          .filter(_.nonEmpty)
          .toList

      SbtProject(id = id, dir = dir, displayName = name, dependsOn = deps)
    }

  private def scanProjectSources(projectRoot: Path): ProjectScanData =
    // Exclude tests to keep diagrams readable.
    val scalaDirs = List(
      projectRoot.resolve("src").resolve("main").resolve("scala")
    ).filter(Files.isDirectory(_))

    val scalaFiles =
      scalaDirs.flatMap { dir =>
        Files
          .walk(dir)
          .iterator()
          .asScala
          .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".scala"))
          .toList
      }

    val filePkgs = scalaFiles.flatMap { p =>
      val text = normalizeSourceText(Files.readString(p, StandardCharsets.UTF_8))
      PackageRe.findFirstMatchIn(text).map(_.group(1))
    }
    val declaredPkgs = filePkgs.toSet

    val companions = scala.collection.mutable.ArrayBuffer.empty[(String, String)]

    val membersByPkg = scalaFiles.foldLeft(Map.empty[String, Map[String, MemberMeta]]) { (acc, p) =>
      val text = normalizeSourceText(Files.readString(p, StandardCharsets.UTF_8))
      val pkgOpt = PackageRe.findFirstMatchIn(text).map(_.group(1))
      pkgOpt match
        case None => acc
        case Some(pkg) =>
          val (defs, comps) = discoverMembersAndCompanions(text, pkg)
          companions ++= comps
          if defs.isEmpty then acc
          else acc.updated(pkg, acc.getOrElse(pkg, Map.empty) ++ defs)
    }

    val edges = scalaFiles.foldLeft(Map.empty[String, Set[String]]) { (acc, p) =>
      val text = normalizeSourceText(Files.readString(p, StandardCharsets.UTF_8))
      val srcPkgOpt = PackageRe.findFirstMatchIn(text).map(_.group(1))
      srcPkgOpt match
        case None => acc
        case Some(srcPkg) =>
          val importedPkgs = parseImportsToPackages(text).filter(declaredPkgs.contains)
          val merged = acc.getOrElse(srcPkg, Set.empty) ++ importedPkgs.filterNot(_ == srcPkg)
          acc.updated(srcPkg, merged)
    }

    val inheritsRaw =
      scalaFiles.foldLeft(Map.empty[String, Set[String]]) { (acc, p) =>
        val text = normalizeSourceText(Files.readString(p, StandardCharsets.UTF_8))
        val pkgOpt = PackageRe.findFirstMatchIn(text).map(_.group(1))
        pkgOpt match
          case None => acc
          case Some(pkg) =>
            val members = discoverInherits(text)
            // convert to memberId keyed map
            members.foldLeft(acc) { case (acc2, (name, parents)) =>
              val fromId = memberResourceId(pkg, name)
              if parents.isEmpty then acc2
              else acc2.updated(fromId, acc2.getOrElse(fromId, Set.empty) ++ parents)
            }
      }

    val observesRaw =
      scalaFiles.foldLeft(Map.empty[String, Set[String]]) { (acc, p) =>
        val text = normalizeSourceText(Files.readString(p, StandardCharsets.UTF_8))
        val pkgOpt = PackageRe.findFirstMatchIn(text).map(_.group(1))
        pkgOpt match
          case None => acc
          case Some(pkg) =>
            discoverObserverObservedTypes(text).foldLeft(acc) { case (acc2, (name, targets)) =>
              val fromId = memberResourceId(pkg, name)
              if targets.isEmpty then acc2
              else acc2.updated(fromId, acc2.getOrElse(fromId, Set.empty) ++ targets)
            }
      }

    ProjectScanData(
      packages = declaredPkgs,
      edges = edges,
      members = membersByPkg,
      inherits = inheritsRaw,
      observes = observesRaw,
      companions = companions.toList.distinct,
      scalaFiles = scalaFiles
    )

  private final case class MemberIndex(
      bySimpleName: Map[String, Set[String]], // Name -> memberIds
      byQualified: Map[String, String]        // chess.foo.Bar -> memberId
  )

  private def buildMemberIndex(membersByPkg: Map[String, Map[String, MemberMeta]]): MemberIndex =
    val qualifiedPairs =
      for
        (pkg, ms) <- membersByPkg.toList
        (name, _) <- ms
      yield (s"$pkg.$name", memberResourceId(pkg, name))

    val byQualified = qualifiedPairs.toMap

    val bySimpleName =
      qualifiedPairs
        .groupBy { case (q, _) => q.split('.').lastOption.getOrElse(q) }
        .view
        .mapValues(_.map(_._2).toSet)
        .toMap

    MemberIndex(bySimpleName = bySimpleName, byQualified = byQualified)

  /** Strip `//` line comments and `/* ... */` block comments (best-effort). */
  private def stripScalaComments(text: String): String =
    val noBlocks =
      text.replaceAll("""(?s)/\*.*?\*/""", "")
    noBlocks
      .linesIterator
      .map { line =>
        val idx = line.indexOf("//")
        if idx < 0 then line
        else line.substring(0, idx)
      }
      .mkString("\n")

  /** Normalize newlines so `(?m)^...$` behaves consistently on Windows checkouts (`\\r\\n`). */
  private def normalizeSourceText(text: String): String =
    text.replace("\r\n", "\n").replace("\r", "\n")

  private def wsIndentLen(line: String): Int =
    val trimmed = line.replace("\t", "    ")
    var i = 0
    while i < trimmed.length && trimmed.charAt(i) == ' ' do i += 1
    i

  /** Extract the first meaningful sentence from a ScalaDoc block body. */
  private def extractDocSummary(raw: String): String =
    val lines =
      raw
        .linesIterator
        .map(_.trim.stripPrefix("*").trim)
        .filter(l => l.nonEmpty && !l.startsWith("@"))
        .toList

    lines.headOption
      .map { first =>
        val idx = first.indexOf(". ")
        if idx >= 0 then first.substring(0, idx + 1) else first
      }
      .getOrElse("")

  private def stripLineComment(line: String): String =
    val idx = line.indexOf("//")
    if idx < 0 then line else line.substring(0, idx)

  private def normalizeDefSignature(line: String): Option[String] =
    // Produce a stable single-line signature:
    // - includes `def` and return type (if present)
    // - omits params
    // Examples:
    //   "def foo(x: Int): String = ..." -> "def foo: String"
    //   "override def bar: Int =" -> "def bar: Int"
    //   "def baz = ..." -> "def baz"
    val noComment = stripLineComment(line).trim
    if !noComment.contains("def") then None
    else
      val afterOverride = noComment.stripPrefix("override ").trim
      val idxDef = afterOverride.indexOf("def")
      val fromDef = if idxDef >= 0 then afterOverride.substring(idxDef).trim else afterOverride

      // Drop anything after '='
      val beforeEq = fromDef.takeWhile(_ != '=').trim

      // Remove first (...) block if present (balanced)
      val open = beforeEq.indexOf('(')
      val withoutParams =
        if open < 0 then beforeEq
        else
          var i = open + 1
          var depth = 1
          while i < beforeEq.length && depth > 0 do
            val c = beforeEq.charAt(i)
            if c == '(' then depth += 1
            else if c == ')' then depth -= 1
            i += 1
          val head = beforeEq.substring(0, open).trim
          val rest = if i <= beforeEq.length then beforeEq.substring(i).trim else ""
          s"$head $rest".trim

      val normalized = withoutParams.replaceAll("\\s+", " ").trim
      if normalized.isEmpty then None
      else
        // Ilograph uses `[...]` for perspective links; generic types like `Vector[String]` can be mis-parsed.
        // Fallback: render generics with parentheses in function list.
        Some(normalized.replace('[', '(').replace(']', ')'))

  // Capture whole def line; normalize it to "def name: ReturnType" (no params).
  private val PublicDefLineRe: Regex =
    """(?m)^\s*(?!private\b)(?!protected\b)(?:override\s+)?def\b[^\n]*""".r

  /** Best-effort discovery of top-level members in a compilation unit (name -> metadata). */
  private def discoverMembersAndCompanions(text: String, pkg: String): (Map[String, MemberMeta], List[(String, String)]) =
    val cleaned = text // keep comments for doc lookup

    val docRe = """(?s)/\*\*([^*]|\*(?!/))*\*/""".r
    val docs = docRe.findAllMatchIn(cleaned).toList
    val defs = TopLevelDefRe.findAllMatchIn(cleaned).toList

    def memberBodySlice(idx: Int): String =
      val start = defs(idx).end
      val end = if idx + 1 < defs.length then defs(idx + 1).start else cleaned.length
      cleaned.substring(start, end)

    def kindRank(k: String): Int =
      k match
        case "class"  => 1
        case "trait"  => 2
        case "enum"   => 3
        case "object" => 4
        case _         => 9

    val entries =
      defs.zipWithIndex.map { case (m, i) =>
        val modsRaw = Option(m.group(1)).getOrElse("")
        val mods = modsRaw.trim.replaceAll("\\s+", " ").trim
        val kind = m.group(2)
        val name = m.group(3)
        val defStart = m.start

        val decl =
          val prefix = if mods.isEmpty then "" else s"$mods "
          s"${prefix}${kind} ${name}"

        val doc =
          docs
            .filter(_.end <= defStart)
            .lastOption
            .filter { cm =>
              val between = cleaned.substring(cm.end, defStart)
              !between.contains("\n\n")
            }
            .map(cm => extractDocSummary(cm.group(0)))
            .filter(_.nonEmpty)

        val body = memberBodySlice(i)
        val pubDefs =
          PublicDefLineRe
            .findAllMatchIn(body)
            .map(_.matched)
            .flatMap(normalizeDefSignature)
            .toVector
            .distinct
            .take(20)

        name -> MemberMeta(kind = kind, decl = decl, doc = doc, publicDefs = pubDefs)
      }

    val grouped = entries.groupBy(_._1)
    val companions = scala.collection.mutable.ArrayBuffer.empty[(String, String)]

    val out =
      grouped.toList.foldLeft(Map.empty[String, MemberMeta]) { case (acc, (name, metas)) =>
        val ms = metas.map(_._2)
        val (objs, nonObjs) = ms.partition(_.kind == "object")

        // Keep one primary non-object kind under the original name.
        val primaryNonObj = nonObjs.sortBy(m => kindRank(m.kind)).headOption
        val acc1 = primaryNonObj.map(m => acc.updated(name, m)).getOrElse(acc)

        // If there is also an object with the same name AND a non-object type, rename the object
        // and add a companion edge. Otherwise keep the object under its original name.
        val acc2 =
          (primaryNonObj, objs.headOption) match
            case (Some(_), Some(objMeta)) =>
              val objName = s"O_$name"
              companions += ((memberResourceId(pkg, name), memberResourceId(pkg, objName)))
              acc1.updated(objName, objMeta)
            case _ => acc1

        // If there are no non-objects, keep one object under the original name.
        if primaryNonObj.isEmpty then
          objs.headOption.map(m => acc2.updated(name, m)).getOrElse(acc2)
        else acc2
      }

    (out, companions.toList)

  private val InheritsClauseRe: Regex =
    // Capture everything after `extends` on the same line (continuations handled separately)
    """(?m)^\s*(?:final\s+|sealed\s+|abstract\s+|case\s+|private\s+|protected\s+|open\s+|transparent\s+|inline\s+)*\b(?:class|trait|object|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b[^\n]*\bextends\s+([^\n]+)$""".r

  /** `(simpleMemberName, full extends/with clause)` for each type that has an `extends` clause. */
  private def extendsClauseEntries(text: String): List[(String, String)] =
    val cleaned = stripScalaComments(text)
    val lines = cleaned.linesIterator.toVector

    def collectClause(startLineIdx: Int, initial: String): String =
      // Pull in immediate continuation lines that start with whitespace and contain `with` segments,
      // stopping at a blank line or at a line that looks like a new top-level definition.
      val b = new StringBuilder(initial.trim)
      var i = startLineIdx + 1
      var done = false
      while i < lines.length && !done do
        val l = lines(i)
        val trimmed = l.trim
        if trimmed.isEmpty then done = true
        else if trimmed.startsWith("class ") || trimmed.startsWith("trait ") || trimmed.startsWith("object ") || trimmed.startsWith("enum ")
        then done = true
        else if l.startsWith(" ") || l.startsWith("\t") then
          // continuation line - include if it contributes to extends/with chain
          if trimmed.startsWith("with ") || trimmed.contains(" with ") then
            b.append(" ").append(trimmed)
          i += 1
        else done = true
      b.toString

    InheritsClauseRe
      .findAllMatchIn(cleaned)
      .map { m =>
        val name = m.group(1)
        val clauseLine = m.group(2)
        val lineIdx = cleaned.substring(0, m.start).count(_ == '\n')
        val clause = collectClause(lineIdx, clauseLine)
        (name, clause)
      }
      .toList

  /** Type argument strings inside `Observer[ ... ]` (supports nested brackets). */
  private def extractObserverTypeArgInners(clause: String): Set[String] =
    val buf = scala.collection.mutable.ArrayBuffer.empty[String]
    var i = 0
    while i < clause.length do
      val idx = clause.indexOf("Observer", i)
      if idx < 0 then i = clause.length
      else
        val boundaryBefore =
          idx == 0 || {
            val c = clause.charAt(idx - 1)
            !c.isLetterOrDigit && c != '_'
          }
        val opensBracket = idx + 8 < clause.length && clause.charAt(idx + 8) == '['
        if boundaryBefore && opensBracket then
          var j = idx + 9
          var depth = 1
          val startArg = j
          while j < clause.length && depth > 0 do
            clause.charAt(j) match
              case '[' => depth += 1; j += 1
              case ']' =>
                depth -= 1
                j += 1
              case _ => j += 1
          if depth == 0 then
            val inner = clause.substring(startArg, j - 1).trim
            if inner.nonEmpty then buf += inner
            i = j
          else i = idx + 1
        else i = idx + 1
    buf.toSet

  /** Tokens to drop from `extends` parent inference — they are type parameters, not super-types. */
  private def tokensStrippedForObserverArgs(observerInners: Set[String]): Set[String] =
    observerInners.foldLeft(Set.empty[String]) { (acc, inner) =>
      val noGenerics = !inner.contains('[')
      val exact = if noGenerics && inner.nonEmpty then acc + inner else acc
      val simple =
        if noGenerics && inner.contains('.') then exact + inner.split('.').lastOption.getOrElse("")
        else exact
      simple.filter(_.nonEmpty)
    }

  private def discoverInherits(text: String): Map[String, Set[String]] =
    val tokenRe = """[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*""".r

    extendsClauseEntries(text)
      .flatMap { case (name, clause) =>
        val observerInners = extractObserverTypeArgInners(clause)
        val stripped = tokensStrippedForObserverArgs(observerInners)

        val tokens = tokenRe.findAllIn(clause).toList
        val parents =
          tokens
            .map(_.trim)
            .filter(t => t.nonEmpty && !IgnoredTypeNames.contains(t))
            .filterNot(t => t == name)
            .filterNot(stripped.contains)
            .toSet

        if parents.isEmpty then None else Some(name -> parents)
      }
      .toMap

  /** Per member (simple name): raw type strings used as `Observer[ ... ]` type arguments (for `observes`). */
  private def discoverObserverObservedTypes(text: String): Map[String, Set[String]] =
    extendsClauseEntries(text)
      .flatMap { case (name, clause) =>
        val inners = extractObserverTypeArgInners(clause)
        if inners.isEmpty then None else Some(name -> inners)
      }
      .toMap

  private def resolveImplements(inheritsRaw: Map[String, Set[String]], idx: MemberIndex): Map[String, Set[String]] =
    // Flip direction: parent -> child
    // Input is childMemberId -> raw parent type tokens.
    val pairs =
      inheritsRaw.toList.flatMap { case (childId, toks) =>
        toks.flatMap(t => resolveTypeToMemberId(t, idx)).map(parentId => (parentId, childId))
      }

    pairs
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .toMap

  /** observer memberId -> observed memberId(s) */
  private def resolveObserves(observesRaw: Map[String, Set[String]], idx: MemberIndex): Map[String, Set[String]] =
    val pairs =
      observesRaw.toList.flatMap { case (observerId, rawTypes) =>
        rawTypes.toList
          .map(_.trim)
          .filter(_.nonEmpty)
          .flatMap(resolveTypeToMemberId(_, idx))
          .map(observedId => observerId -> observedId)
      }
    pairs
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .toMap

  private def scanMemberCalls(
      scalaFiles: List[Path],
      declaredPkgs: Set[String],
      idx: MemberIndex
  ): Map[String, Set[String]] =
    val raw =
      scalaFiles.foldLeft(Map.empty[String, Set[String]]) { (acc, p) =>
        val text = normalizeSourceText(Files.readString(p, StandardCharsets.UTF_8))
        val pkgOpt = PackageRe.findFirstMatchIn(text).map(_.group(1))
        pkgOpt match
          case None => acc
          case Some(pkg) if !declaredPkgs.contains(pkg) => acc
          case Some(pkg) =>
            val cleaned = stripScalaComments(text)
            CtorHeaderRe
              .findAllMatchIn(cleaned)
              .foldLeft(acc) { (acc2, m) =>
                val name = m.group(3)
                val fromId = memberResourceId(pkg, name)

                // Constructor params can span multiple lines; parse balanced (...) blocks after the header.
                val paramBlocks = extractCtorParamBlocks(cleaned, m.end)
                val typeNames = paramBlocks.flatMap(extractTypeNames).toSet
                val targets = typeNames.flatMap(resolveTypeToMemberId(_, idx)).filterNot(_ == fromId)
                if targets.isEmpty then acc2
                else acc2.updated(fromId, acc2.getOrElse(fromId, Set.empty) ++ targets)
              }
      }

    // If all constructor-resolved targets filter down to `self`, we can end up with an empty set.
    // Keep those keys out of the map: they are not real outgoing `calls` edges.
    raw.filter { case (_, tos) => tos.nonEmpty }

  private val IgnoredTypeNames: Set[String] = Set(
    "String",
    "Int",
    "Long",
    "Double",
    "Float",
    "Boolean",
    "Unit",
    "Any",
    "AnyRef",
    "Option",
    "Either",
    "List",
    "Vector",
    "Seq",
    "Set",
    "Map",
    "IO",
    "Future",
    "Try"
  )

  private def extractTypeNames(paramBlock: String): Set[String] =
    if paramBlock.isEmpty then Set.empty
    else
      // Parse `(... name: Type, name2: Option[Vector[Board]] ...)` and keep the full type,
      // respecting nested generic brackets so commas inside `Either[A, B]` don't truncate.
      val types = scala.collection.mutable.ArrayBuffer.empty[String]

      var i = 0
      while i < paramBlock.length do
        if paramBlock.charAt(i) == ':' then
          i += 1
          while i < paramBlock.length && paramBlock.charAt(i).isWhitespace do i += 1

          val start = i
          var squareDepth = 0
          var parenDepth = 0
          var done = false

          while i < paramBlock.length && !done do
            val c = paramBlock.charAt(i)
            c match
              case '[' => squareDepth += 1; i += 1
              case ']' => if squareDepth > 0 then squareDepth -= 1; i += 1
              case '(' => parenDepth += 1; i += 1
              case ')' =>
                if squareDepth == 0 && parenDepth == 0 then done = true
                else
                  if parenDepth > 0 then parenDepth -= 1
                  i += 1
              case ',' =>
                if squareDepth == 0 && parenDepth == 0 then done = true
                else i += 1
              case _ => i += 1

          val raw = paramBlock.substring(start, i).trim
          if raw.nonEmpty then types += raw
        else i += 1

      val tokenRe = """[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*""".r
      val tokens = types.toList.flatMap(t => tokenRe.findAllIn(t).toList)
      tokens.map(_.trim).filter(t => t.nonEmpty && !IgnoredTypeNames.contains(t)).toSet

  private def extractCtorParamBlocks(text: String, startIdx: Int): List[String] =
    // Starting just after the header match, find any immediately following `( ... )` blocks.
    // Supports multiline and multiple parameter lists.
    var i = startIdx
    val blocks = scala.collection.mutable.ArrayBuffer.empty[String]

    def skipWs(): Unit =
      while i < text.length && text.charAt(i).isWhitespace do i += 1

    skipWs()
    while i < text.length && text.charAt(i) == '(' do
      val open = i
      i += 1
      var depth = 1
      while i < text.length && depth > 0 do
        val c = text.charAt(i)
        if c == '(' then depth += 1
        else if c == ')' then depth -= 1
        i += 1
      val close = i
      if close > open + 1 then blocks += text.substring(open, close)
      skipWs()

    blocks.toList

  private def resolveTypeToMemberId(typeName: String, idx: MemberIndex): Option[String] =
    idx.byQualified.get(typeName).orElse {
      val simple = typeName.split('.').lastOption.getOrElse(typeName)
      idx.bySimpleName.get(simple).flatMap { ids =>
        if ids.size == 1 then ids.headOption else None
      }
    }

  private def parseImportsToPackages(scalaFileText: String): Set[String] =
    val lines = ImportLineRe.findAllMatchIn(scalaFileText).map(_.group(1)).toList

    def normalizeOne(raw: String): List[String] =
      val noComment = raw.split("//", 1).headOption.getOrElse(raw).trim
      if noComment.isEmpty then Nil
      else
        val withoutGiven = noComment.replace("given ", "").trim
        val parts = withoutGiven.split(',').toList.map(_.trim).filter(_.nonEmpty)
        parts.flatMap { p =>
          val dropBraces = p.replace("._", "").replace(".*", "").trim
          val base = dropBraces.replaceAll("""\.\{\s*[^}]+\s*\}\s*$""", "")
          val noAlias = base.split(" as ", 1).headOption.getOrElse(base).trim
          if noAlias.isEmpty then Nil
          else
            val segs = noAlias.split('.').toList
            // Reduce to package portion (assume last segment is a type/object)
            if segs.length >= 3 then List(segs.dropRight(1).mkString("."))
            else List(noAlias)
        }

    lines.flatMap(normalizeOne).toSet

  private def renderYaml(
      meta: BuildMeta,
      projects: List[SbtProject],
      packageGraphs: Map[String, PackageGraph],
      projectColors: Map[String, String],
      timestamp: String
  ): String =
    def q(s: String): String =
      val safe = s.replace("\n", "\\n").replace("\"", "'")
      s""""$safe""""
    def indent(level: Int)(line: String): String = ("  " * level) + line

    val header = List(
      "ilograph: 1",
      "",
      "meta:",
      indent(1)(s"title: ${q("EmBe Chess")}"),
      indent(1)(s"generated_at: ${q(timestamp)}"),
      indent(1)(s"organization: ${q(meta.organization)}"),
      indent(1)(s"version: ${q(meta.version)}"),
      indent(1)(s"language: ${q("Scala")}"),
      indent(1)(s"scala_version: ${q(meta.scalaVersion)}"),
      ""
    )

    val resources = List(
      "resources:",
      indent(1)("- name: Build"),
      indent(2)(s"subtitle: ${q("Derived from build.sbt")}"),
      indent(2)(s"icon: ${q(ProjectIcon)}"),
      indent(2)("children:"),
      indent(3)("- name: SBT projects"),
      indent(4)(s"icon: ${q(ProjectIcon)}"),
      indent(4)("children:")
    )

    val projectResources =
      projects.sortBy(_.id.toLowerCase).flatMap { p =>
        val color = projectColors.getOrElse(p.id, "#7f7f7f")
        List(
          indent(5)(s"- name: ${p.id}"),
          indent(6)(s"subtitle: ${q(p.displayName.getOrElse(p.id))}"),
          indent(6)(s"description: ${q(s"[Packages ${p.id}]")}"),
          indent(6)(s"dir: ${q(p.dir)}"),
          indent(6)(s"icon: ${q(ProjectIcon)}"),
          indent(6)(s"color: ${q("Black")}"),
          indent(6)(s"backgroundColor: ${q(color)}")
        )
      }

    val packageResourcesHeader = List(
      indent(1)("- name: Scala packages"),
      indent(2)(s"subtitle: ${q("Packages discovered from Scala sources per SBT project")}"),
      indent(2)(s"icon: ${q(PackageIcon)}"),
      indent(2)("children:")
    )

    val packageResources =
      projects.sortBy(_.id.toLowerCase).flatMap { p =>
        val graph = packageGraphs.getOrElse(
          p.id,
          PackageGraph(Set.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Nil)
        )
        val pkgs = graph.packages.toList.sorted
        val full = projectColors.getOrElse(p.id, "#7f7f7f")
        val shell = lightenHex(full, 0.82)
        val projectNode =
          List(
            indent(3)(s"- name: ${p.id}"),
            indent(4)(s"subtitle: ${q(p.dir)}"),
            indent(4)(s"description: ${q(s"[Packages ${p.id}]")}"),
            indent(4)(s"icon: ${q(ProjectIcon)}"),
            indent(4)(s"color: ${q("Black")}"),
            indent(4)(s"backgroundColor: ${q(shell)}"),
            indent(4)("children:")
          )

        val tree = buildPackageTree(pkgs.toSet)
        val topLevel = pkgs.filter(pkg => tree.parentOf.get(pkg).isEmpty)

        val maxDepth = maxTreeDepth(tree, topLevel)

        def bgForDepth(depth: Int): String =
          // Outer = lighter, inner = full color.
          // Depth increases as we drill down. Deepest nodes should be full color (amount = 0).
          val stepsFromDeepest = Math.max(0, maxDepth - depth)
          val amount = Math.min(0.6, stepsFromDeepest * 0.12)
          lightenHex(full, amount)

        def renderPkg(pkg: String, level: Int, depth: Int): List[String] =
          val base = List(
            indent(level)(s"- name: $pkg"),
            indent(level + 1)(s"icon: ${q(PackageIcon)}"),
            indent(level + 1)(s"color: ${q("Black")}"),
            indent(level + 1)(s"backgroundColor: ${q(bgForDepth(depth))}")
          )
          val kids = tree.childrenOf.getOrElse(pkg, Nil)
          val members = graph.members.getOrElse(pkg, Map.empty).toList.sortBy(_._1)

          val childBlocks =
            kids.flatMap(child => renderPkg(child, level + 2, depth + 1)) ++
              members.flatMap { case (m, meta) =>
                val memberBg = darkenHex(bgForDepth(depth), 0.14)
                val memberId = memberResourceId(pkg, m)
                val baseLines = List(
                  indent(level + 2)(s"- name: $m"),
                  indent(level + 3)(s"id: ${q(memberId)}"),
                  indent(level + 3)(s"subtitle: ${q(meta.decl)}"),
                  indent(level + 3)(s"icon: ${q(iconForMemberKind(meta.kind))}")
                )

                val descLines =
                  meta.doc.map(_.trim).filter(_.nonEmpty).toVector ++
                  meta.publicDefs.take(20)

                val withDescription =
                  if descLines.isEmpty then baseLines
                  else baseLines :+ indent(level + 3)(s"description: ${q(descLines.mkString("\n"))}")

                withDescription ++ List(
                  indent(level + 3)(s"color: ${q("Black")}"),
                  indent(level + 3)(s"backgroundColor: ${q(memberBg)}")
                )
              }

          if childBlocks.isEmpty then base
          else base ++ List(indent(level + 1)("children:")) ++ childBlocks

        val pkgNodes = topLevel.flatMap(pkg => renderPkg(pkg, level = 5, depth = 0))

        projectNode ++ pkgNodes
      }

    val perspectivesHeader = List("", "perspectives:")
    val projectsPerspectiveHeader = List(
      indent(1)("- name: Projects"),
      indent(2)(s"color: ${q("Black")}"),
      indent(2)("relations:")
    )

    val projectRelations =
      projects
        .flatMap(p => p.dependsOn.map(d => (p.id, d)))
        .sortBy { case (a, b) => (a.toLowerCase, b.toLowerCase) }
        .map { case (from, to) =>
          List(
            indent(3)(s"- from: $from"),
            indent(4)(s"to: $to"),
            indent(4)(s"label: ${q("dependsOn")}")
          ).mkString("\n")
        }

    val packagesPerspectives =
      projects.sortBy(_.id.toLowerCase).flatMap { p =>
        val graph = packageGraphs.getOrElse(
          p.id,
          PackageGraph(Set.empty, Map.empty, Map.empty, Map.empty, Map.empty, Map.empty, Nil)
        )
        val tree = buildPackageTree(graph.packages)
        val projColor = projectColors.getOrElse(p.id, "#7f7f7f")
        val edges =
          graph.edges.toList
            .flatMap { case (src, dsts) => dsts.toList.map(dst => (src, dst)) }
            .filterNot { case (src, dst) => isNestedPackages(tree, src, dst) }
            .sortBy { case (a, b) => (a, b) }

        val maximizePkgs =
          tree.childrenOf.keySet.toList.sorted

        val perspectiveHeader = List(
          indent(1)(s"- name: Packages ${p.id}"),
          indent(2)(s"color: ${q(projColor)}"),
          // Start perspectives in a lower-detail state. Users can increase detail
          // with the UI slider to progressively reveal nested package/member nodes.
          indent(2)("options:"),
          indent(3)("initialDetailLevel: 0.32"),
          indent(2)("overrides:")
        )

        val overrides =
          if maximizePkgs.isEmpty then Nil
          else
            maximizePkgs.flatMap { pkg =>
              List(
                indent(3)(s"- resourceId: $pkg"),
                indent(4)("detail: maximize")
              )
            }

        val relationsHeader = List(indent(2)("relations:"))

        val declareEdges =
          graph.members.toList
            .flatMap { case (pkg, ms) =>
              ms.keys.toList.sorted
                .map(m => (pkg, memberResourceId(pkg, m)))
                // Always declare package membership for every member resource.
                //
                // Relation-style perspectives won't reliably show member resources unless they're
                // referenced by a relation edge; `calls` only covers some members, so `declares`
                // must be emitted for *all* members (including those with no inferred outgoing calls).
            }
            .sortBy { case (a, b) => (a, b) }

        val declareRels =
          declareEdges.map { case (fromPkg, toMemberId) =>
            List(
              indent(3)(s"- from: $fromPkg"),
              indent(4)(s"to: ${q(toMemberId)}"),
              indent(4)(s"label: ${q("declares")}"),
              indent(4)("secondary: true")
            ).mkString("\n")
          }

        val callEdges =
          graph.memberCalls.toList
            .flatMap { case (from, tos) => tos.toList.map(to => (from, to)) }
            .sortBy { case (a, b) => (a, b) }

        val callRels =
          callEdges.map { case (from, to) =>
            List(
              indent(3)(s"- from: ${q(from)}"),
              indent(4)(s"to: ${q(to)}"),
              indent(4)(s"label: ${q("calls")}")
            ).mkString("\n")
          }

        val implementEdges =
          graph.memberImplements.toList
            .flatMap { case (from, tos) => tos.toList.map(to => (from, to)) }
            .sortBy { case (a, b) => (a, b) }

        val implementRels =
          implementEdges.map { case (from, to) =>
            List(
              indent(3)(s"- from: ${q(from)}"),
              indent(4)(s"to: ${q(to)}"),
              indent(4)(s"label: ${q("implemented by")}")
            ).mkString("\n")
          }

        val observeEdges =
          graph.memberObserves.toList
            .flatMap { case (from, tos) => tos.toList.map(to => (from, to)) }
            .sortBy { case (a, b) => (a, b) }

        val observeRels =
          observeEdges.map { case (from, to) =>
            List(
              indent(3)(s"- from: ${q(from)}"),
              indent(4)(s"to: ${q(to)}"),
              indent(4)(s"label: ${q("observes")}")
            ).mkString("\n")
          }

        val companionEdges =
          graph.memberCompanions
            .sortBy { case (a, b) => (a, b) }

        val companionRels =
          companionEdges.map { case (from, to) =>
            List(
              indent(3)(s"- from: ${q(from)}"),
              indent(4)(s"to: ${q(to)}"),
              indent(4)(s"label: ${q("companion")}")
            ).mkString("\n")
          }

        val rels =
          edges.map { case (from, to) =>
            List(
              indent(3)(s"- from: $from"),
              indent(4)(s"to: $to"),
              indent(4)(s"label: ${q("imports")}")
            ).mkString("\n")
          }

        perspectiveHeader ++ overrides ++ relationsHeader ++ declareRels ++ implementRels ++ observeRels ++ companionRels ++ callRels ++ rels
      }

    (header ++ resources ++ projectResources ++ packageResourcesHeader ++ packageResources ++ perspectivesHeader ++ projectsPerspectiveHeader ++ projectRelations ++ packagesPerspectives)
      .mkString("\n") + "\n"

  private def colorForProject(projectId: String): String =
    val idx = Math.floorMod(projectId.toLowerCase.hashCode, ProjectPalette.size)
    ProjectPalette(idx)

  private def iconForMemberKind(kind: String): String =
    kind match
      case "class"  => ClassIcon
      case "trait"  => TraitIcon
      case "object" => ObjectIcon
      case "enum"   => EnumIcon
      case _         => ClassIcon

  /** Mix a hex color with white by `amount` (0..1). Higher means lighter. */
  private def lightenHex(hex: String, amount: Double): String =
    def clamp01(x: Double): Double = Math.max(0.0, Math.min(1.0, x))
    val a = clamp01(amount)

    val clean = hex.trim.stripPrefix("#")
    if clean.length != 6 then hex
    else
      val r = Integer.parseInt(clean.substring(0, 2), 16)
      val g = Integer.parseInt(clean.substring(2, 4), 16)
      val b = Integer.parseInt(clean.substring(4, 6), 16)

      def mix(c: Int): Int =
        val mixed = c + ((255 - c) * a)
        Math.round(mixed).toInt.max(0).min(255)

      f"#${mix(r)}%02x${mix(g)}%02x${mix(b)}%02x"

  /** Mix a hex color with black by `amount` (0..1). Higher means darker. */
  private def darkenHex(hex: String, amount: Double): String =
    def clamp01(x: Double): Double = Math.max(0.0, Math.min(1.0, x))
    val a = clamp01(amount)

    val clean = hex.trim.stripPrefix("#")
    if clean.length != 6 then hex
    else
      val r = Integer.parseInt(clean.substring(0, 2), 16)
      val g = Integer.parseInt(clean.substring(2, 4), 16)
      val b = Integer.parseInt(clean.substring(4, 6), 16)

      def mix(c: Int): Int =
        val mixed = c * (1.0 - a)
        Math.round(mixed).toInt.max(0).min(255)

      f"#${mix(r)}%02x${mix(g)}%02x${mix(b)}%02x"

  private final case class PackageTree(parentOf: Map[String, String], childrenOf: Map[String, List[String]])

  /** Build a nesting tree when package prefixes are also present.
    *
    * Example: if both `chess` and `chess.aview` exist, then `chess.aview` nests under `chess`.
    * Names remain the full package string so relations can still refer to `chess.aview` directly.
    */
  private def buildPackageTree(packages: Set[String]): PackageTree =
    val sorted = packages.toList.sortBy(p => (p.length, p))

    def parentCandidate(pkg: String): Option[String] =
      val parts = pkg.split('.').toList
      // check progressively shorter prefixes, longest first
      val prefixes =
        parts.inits
          .toList
          .drop(1) // drop full pkg itself
          .map(_.mkString("."))
          .filter(_.nonEmpty)
      prefixes.find(packages.contains)

    val parentOf =
      sorted.flatMap { pkg =>
        parentCandidate(pkg).map(parent => pkg -> parent)
      }.toMap

    val childrenOf =
      parentOf.toList
        .groupBy(_._2)
        .view
        .mapValues(_.map(_._1).sorted)
        .toMap

    PackageTree(parentOf = parentOf, childrenOf = childrenOf)

  private def maxTreeDepth(tree: PackageTree, roots: List[String]): Int =
    def depthFrom(node: String, depth: Int): Int =
      val kids = tree.childrenOf.getOrElse(node, Nil)
      if kids.isEmpty then depth
      else kids.map(k => depthFrom(k, depth + 1)).max

    if roots.isEmpty then 0 else roots.map(r => depthFrom(r, 0)).max

  private def isNestedPackages(tree: PackageTree, a: String, b: String): Boolean =
    // If one package is (transitively) nested under the other, treat as containment not a dependency edge.
    isAncestor(tree, a, b) || isAncestor(tree, b, a)

  private def isAncestor(tree: PackageTree, ancestor: String, node: String): Boolean =
    @annotation.tailrec
    def loop(cur: String): Boolean =
      tree.parentOf.get(cur) match
        case None         => false
        case Some(parent) => parent == ancestor || loop(parent)
    loop(node)

  private def memberResourceId(pkg: String, member: String): String =
    // Unique, stable id for a member within a package. Avoid restricted chars: / ^ * [ ] ,
    s"${pkg}~${member}"

GenerateIlograph.main(args)

