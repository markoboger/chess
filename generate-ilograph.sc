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

final case class MemberMeta(kind: String, doc: Option[String])

final case class PackageGraph(
    packages: Set[String],
    edges: Map[String, Set[String]],
    members: Map[String, Map[String, MemberMeta]], // package -> (member name -> metadata)
    memberCalls: Map[String, Set[String]]          // memberId -> memberId (inferred from constructor param types)
)

object GenerateIlograph:
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
    """(?m)^\s*(?:final\s+|sealed\s+|abstract\s+|case\s+|private\s+|protected\s+|open\s+|transparent\s+|inline\s+)*\b(class|trait|object|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b""".r

  private val TopLevelDefWithCtorRe: Regex =
    """(?m)^\s*(?:final\s+|sealed\s+|abstract\s+|case\s+|private\s+|protected\s+|open\s+|transparent\s+|inline\s+)*\b(class|trait|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(\([^=\n]*\))?""".r

  // Icon taxonomy is strictly by artifact category:
  // project, package, class, trait, object, enum.
  // Temporary debugging: force a single icon everywhere to verify rendering.
  private val ProjectIcon = "Networking/database.svg"
  private val PackageIcon = "Networking/database.svg"
  private val ClassIcon = "Networking/database.svg"
  private val TraitIcon = "Networking/database.svg"
  private val ObjectIcon = "Networking/database.svg"
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
    val packageGraphs = projects.map(p => p.id -> scanProjectPackages(repoRoot.resolve(p.dir))).toMap
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

  private def scanProjectPackages(projectRoot: Path): PackageGraph =
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

    val membersByPkg = scalaFiles.foldLeft(Map.empty[String, Map[String, MemberMeta]]) { (acc, p) =>
      val text = normalizeSourceText(Files.readString(p, StandardCharsets.UTF_8))
      val pkgOpt = PackageRe.findFirstMatchIn(text).map(_.group(1))
      pkgOpt match
        case None => acc
        case Some(pkg) =>
          val defs = discoverMembers(text)
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

    val memberIndex = buildMemberIndex(membersByPkg)
    val memberCalls = scanMemberCalls(scalaFiles, declaredPkgs, memberIndex)

    PackageGraph(packages = declaredPkgs, edges = edges, members = membersByPkg, memberCalls = memberCalls)

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

  /** Best-effort discovery of top-level members in a compilation unit (name -> metadata). */
  private def discoverMembers(text: String): Map[String, MemberMeta] =
    val cleaned = text // keep comments for doc lookup

    val docRe = """(?s)/\*\*([^*]|\*(?!/))*\*/""".r
    val docs = docRe.findAllMatchIn(cleaned).toList

    TopLevelDefRe
      .findAllMatchIn(cleaned)
      .map { m =>
        val kind = m.group(1)
        val name = m.group(2)
        val defStart = m.start

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

        name -> MemberMeta(kind = kind, doc = doc)
      }
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
            TopLevelDefWithCtorRe
              .findAllMatchIn(cleaned)
              .foldLeft(acc) { (acc2, m) =>
                val name = m.group(2)

                val params = Option(m.group(3)).getOrElse("")
                val fromId = memberResourceId(pkg, name)
                val typeNames = extractTypeNames(params)
                val targets =
                  typeNames.flatMap(resolveTypeToMemberId(_, idx)).filterNot(_ == fromId)
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
      val afterColons = paramBlock.split(':').drop(1).toList
      val candidates =
        afterColons.flatMap { tail =>
          val chunk = tail.takeWhile(c => c != ',' && c != ')')
          val re = """[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*""".r
          re.findAllIn(chunk).toList
        }
      candidates.map(_.trim).filter(t => t.nonEmpty && !IgnoredTypeNames.contains(t)).toSet

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
    def q(s: String): String = s""""${s.replace("\"", "'")}""""
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
        val graph = packageGraphs.getOrElse(p.id, PackageGraph(Set.empty, Map.empty, Map.empty, Map.empty))
        val pkgs = graph.packages.toList.sorted
        val full = projectColors.getOrElse(p.id, "#7f7f7f")
        val shell = lightenHex(full, 0.82)
        val projectNode =
          List(
            indent(3)(s"- name: ${p.id}"),
            indent(4)(s"subtitle: ${q(p.dir)}"),
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
                val kindLabel = meta.kind match
                  case "class"  => "class"
                  case "trait"  => "trait"
                  case "object" => "object"
                  case "enum"   => "enum"
                  case other     => other

                val baseLines = List(
                  indent(level + 2)(s"- name: $m"),
                  indent(level + 3)(s"id: ${q(memberId)}"),
                  indent(level + 3)(s"subtitle: ${q(kindLabel)}"),
                  indent(level + 3)(s"icon: ${q(iconForMemberKind(meta.kind))}")
                )

                val withDescription =
                  meta.doc match
                    case Some(d) => baseLines :+ indent(level + 3)(s"description: ${q(d)}")
                    case None    => baseLines

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
        val graph = packageGraphs.getOrElse(p.id, PackageGraph(Set.empty, Map.empty, Map.empty, Map.empty))
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

        val rels =
          edges.map { case (from, to) =>
            List(
              indent(3)(s"- from: $from"),
              indent(4)(s"to: $to"),
              indent(4)(s"label: ${q("imports")}")
            ).mkString("\n")
          }

        perspectiveHeader ++ overrides ++ relationsHeader ++ declareRels ++ callRels ++ rels
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

