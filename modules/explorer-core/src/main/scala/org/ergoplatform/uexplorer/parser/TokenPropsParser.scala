package org.ergoplatform.uexplorer.parser

import org.ergoplatform.uexplorer.RegisterId
import org.ergoplatform.uexplorer.node.{ExpandedRegister, TokenProps}

import java.util.regex.Pattern
import scala.util.Try

object TokenPropsParser {

  private val StringCharset = "UTF-8"
  private val MaxStringLen  = 1000

  def parse(registers: Map[RegisterId, ExpandedRegister]): Option[TokenProps] =
    for {
      name <- registers.get(RegisterId.R4).flatMap(_.regValue.map(_.value))
      description = registers.get(RegisterId.R5).flatMap(_.regValue.map(_.value)).getOrElse("")
      decimals    = registers.get(RegisterId.R6).flatMap(_.regValue.map(_.value)).flatMap(_.toIntOption).getOrElse(0)
    } yield TokenProps(name, description, decimals)

  private def looksLikeUTF8(utf8: Array[Byte]): Boolean = {
    val p = Pattern.compile(
      "\\A(\n" +
        "  [\\x09\\x0A\\x0D\\x20-\\x7E]             # ASCII\\n" +
        "| [\\xC2-\\xDF][\\x80-\\xBF]               # non-overlong 2-byte\n" +
        "|  \\xE0[\\xA0-\\xBF][\\x80-\\xBF]         # excluding overlongs\n" +
        "| [\\xE1-\\xEC\\xEE\\xEF][\\x80-\\xBF]{2}  # straight 3-byte\n" +
        "|  \\xED[\\x80-\\x9F][\\x80-\\xBF]         # excluding surrogates\n" +
        "|  \\xF0[\\x90-\\xBF][\\x80-\\xBF]{2}      # planes 1-3\n" +
        "| [\\xF1-\\xF3][\\x80-\\xBF]{3}            # planes 4-15\n" +
        "|  \\xF4[\\x80-\\x8F][\\x80-\\xBF]{2}      # plane 16\n" +
        ")*\\z",
      Pattern.COMMENTS
    )
    val phonyString = new String(utf8, "ISO-8859-1")
    p.matcher(phonyString).matches
  }

  private def toUtf8String(raw: Array[Byte]): Option[String] =
    if (raw.length <= MaxStringLen && looksLikeUTF8(raw))
      Try(new String(raw, StringCharset)).toOption
    else None
}
