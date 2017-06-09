package sbt.internal.librarymanagement
package cross

object CrossVersionUtil {
  val trueString = "true"
  val falseString = "false"
  val fullString = "full"
  val noneString = "none"
  val disabledString = "disabled"
  val binaryString = "binary"
  val TransitionDottyVersion = "" // Dotty always respects binary compatibility
  val TransitionScalaVersion = "2.10" // ...but scalac doesn't until Scala 2.10
  val TransitionSbtVersion = "0.12"

  def isFull(s: String): Boolean = (s == trueString) || (s == fullString)

  def isDisabled(s: String): Boolean =
    (s == falseString) || (s == noneString) || (s == disabledString)

  def isBinary(s: String): Boolean = (s == binaryString)

  private val intPattern = """\d{1,10}"""
  private val basicVersion = raw"""($intPattern)\.($intPattern)\.($intPattern)"""
  private val ReleaseV = raw"""$basicVersion(-\d+)?""".r
  private val BinCompatV = raw"""$basicVersion-bin(-.*)?""".r
  private val CandidateV = raw"""$basicVersion(-RC\d+)""".r
  private val NonReleaseV_1 = raw"""$basicVersion([-\w+]*)""".r
  private val NonReleaseV_2 = raw"""$basicVersion(-\w+)""".r
  private[sbt] val PartialVersion = raw"""($intPattern)\.($intPattern)(?:\..+)?""".r

  private[sbt] def isSbtApiCompatible(v: String): Boolean = sbtApiVersion(v).isDefined

  /**
   * Returns sbt binary interface x.y API compatible with the given version string v.
   * RCs for x.y.0 are considered API compatible.
   * Compatible versions include 0.12.0-1 and 0.12.0-RC1 for Some(0, 12).
   */
  private[sbt] def sbtApiVersion(v: String): Option[(Int, Int)] = v match {
    case ReleaseV(x, y, _, _)                     => Some((x.toInt, y.toInt))
    case CandidateV(x, y, _, _)                   => Some((x.toInt, y.toInt))
    case NonReleaseV_1(x, y, z, _) if z.toInt > 0 => Some((x.toInt, y.toInt))
    case _                                        => None
  }

  private[sbt] def isScalaApiCompatible(v: String): Boolean = scalaApiVersion(v).isDefined

  /**
   * Returns Scala binary interface x.y API compatible with the given version string v.
   * Compatible versions include 2.10.0-1 and 2.10.1-M1 for Some(2, 10), but not 2.10.0-RC1.
   */
  private[sbt] def scalaApiVersion(v: String): Option[(Int, Int)] = v match {
    case ReleaseV(x, y, _, _)                     => Some((x.toInt, y.toInt))
    case BinCompatV(x, y, _, _)                   => Some((x.toInt, y.toInt))
    case NonReleaseV_2(x, y, z, _) if z.toInt > 0 => Some((x.toInt, y.toInt))
    case _                                        => None
  }

  private[sbt] def partialVersion(s: String): Option[(Int, Int)] =
    s match {
      case PartialVersion(major, minor) => Some((major.toInt, minor.toInt))
      case _                            => None
    }

  def binaryScalaVersion(full: String): String = {
    val cutoff = if (full.startsWith("0.")) TransitionDottyVersion else TransitionScalaVersion
    binaryVersionWithApi(full, cutoff)(scalaApiVersion)
  }

  def binarySbtVersion(full: String): String =
    binaryVersionWithApi(full, TransitionSbtVersion)(sbtApiVersion)

  private[this] def isNewer(major: Int, minor: Int, minMajor: Int, minMinor: Int): Boolean =
    major > minMajor || (major == minMajor && minor >= minMinor)

  private[this] def binaryVersionWithApi(full: String, cutoff: String)(
      apiVersion: String => Option[(Int, Int)]
  ): String = {
    (apiVersion(full), partialVersion(cutoff)) match {
      case (Some((major, minor)), None) => s"$major.$minor"
      case (Some((major, minor)), Some((minMajor, minMinor)))
          if isNewer(major, minor, minMajor, minMinor) =>
        s"$major.$minor"
      case _ => full
    }
  }
}
