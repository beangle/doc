package org.beangle.doc.pdf

import org.beangle.doc.core.PrintOptions

import java.io.File
import java.net.URI

trait PdfMaker {
  def convert(uri: URI, pdf: File, options: PrintOptions): Boolean

  def close(): Unit
}
