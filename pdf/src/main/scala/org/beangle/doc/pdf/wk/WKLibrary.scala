/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.doc.pdf.wk

import com.sun.jna.ptr.PointerByReference
import com.sun.jna.{Callback, Memory, Pointer}
import org.beangle.doc.core.ProgressPhase
import org.beangle.doc.core.util.{NativeLibrary, NativeLoader}

import java.util.concurrent.{Callable, ExecutionException, ExecutorService, Executors}

object WKLibrary {

  private val executorService = buildExecutor()
  val instance: WKLibrary = loadLibrary()

  private def buildExecutor(): ExecutorService = Executors.newFixedThreadPool(1, (r: Runnable) => {
    val thread = Executors.defaultThreadFactory().newThread(r)
    thread.setDaemon(true)
    thread
  })

  private def loadLibrary(): WKLibrary = {
    executorService.submit(new Callable[WKLibrary]() {
      override def call(): WKLibrary = {
        new NativeLoader("wkhtmltopdf", "libwkhtmltox")
          .load("C:\\Program Files\\wkhtmltopdf\\bin", "latest", classOf[WKLibrary])
      }
    }).get()
  }

  def withInstance[T](fn: WKLibrary => T): T = {
    try {
      executorService.submit(new Callable[T] {
        override def call(): T = fn(instance)
      }).get()
    } catch {
      case e: ExecutionException =>
        e.getCause match {
          case r: RuntimeException => throw r
          case _ => throw new IllegalStateException(e)
        }
      case e: InterruptedException =>
        throw new IllegalStateException("interrupted", e)
    }
  }
}

/** wkhtmltopdf的C接口
 *
 * @see C:\Program Files\wkhtmltopdf\include\wkhtmltox\pdf.h
 * @see https://wkhtmltopdf.org/libwkhtmltox/pagesettings.html
 */
trait WKLibrary extends NativeLibrary {

  trait wkhtmltopdf_str_callback extends Callback {
    def callback(converter: Pointer, str: String): Unit
  }

  trait wkhtmltopdf_void_callback extends Callback {
    def callback(converter: Pointer): Unit
  }

  trait wkhtmltopdf_int_callback extends Callback {
    def callback(converter: Pointer, i: Int): Unit
  }

  override def version: String = {
    this.wkhtmltopdf_version()
  }

  override def init(): Unit = {
    this.wkhtmltopdf_init(0)
  }

  override def destroy(): Unit = {
    this.wkhtmltopdf_deinit()
  }

  def convert(converter: Pointer): Int = {
    this.wkhtmltopdf_convert(converter)
  }

  def createGlobalSettings(): Pointer = {
    this.wkhtmltopdf_create_global_settings()
  }

  def createObjectSettings(): Pointer = {
    this.wkhtmltopdf_create_object_settings()
  }

  def createConverter(globalSettings: Pointer): Pointer = {
    this.wkhtmltopdf_create_converter(globalSettings)
  }

  def setGlobal(globalSettings: Pointer, name: String, value: String): Int = {
    this.wkhtmltopdf_set_global_setting(globalSettings, name, value)
  }

  def setObject(objectSettings: Pointer, name: String, value: String): Int = {
    this.wkhtmltopdf_set_object_setting(objectSettings, name, value)
  }

  def addObject(converter: Pointer, objectSettings: Pointer, data: String): Unit = {
    this.wkhtmltopdf_add_object(converter, objectSettings, data)
  }

  def currentPhase(converter: Pointer, percent: Int): ProgressPhase = {
    val phase = this.wkhtmltopdf_current_phase(converter)
    val totalPhases = this.wkhtmltopdf_phase_count(converter)
    val phaseDesc = this.wkhtmltopdf_phase_description(converter, phase)
    ProgressPhase(phase, phaseDesc, totalPhases, percent)
  }

  def destroy(globalSetting: Pointer, objectSettingsList: Iterable[Pointer], converter: Pointer): Unit = {
    this.wkhtmltopdf_destroy_global_settings(globalSetting)
    objectSettingsList foreach { objectSettings => this.wkhtmltopdf_destroy_object_settings(objectSettings) }
    this.wkhtmltopdf_destroy_converter(converter)
  }

  def setWarningCallback(converter: Pointer, cb: wkhtmltopdf_str_callback): Unit = {
    this.wkhtmltopdf_set_warning_callback(converter, cb)
  }

  def setErrorCallback(converter: Pointer, cb: wkhtmltopdf_str_callback): Unit = {
    this.wkhtmltopdf_set_error_callback(converter, cb)
  }

  def setPhaseChangedCallback(converter: Pointer, cb: wkhtmltopdf_void_callback): Unit = {
    this.wkhtmltopdf_set_phase_changed_callback(converter, cb)
  }

  def setProgressChangedCallback(converter: Pointer, cb: wkhtmltopdf_int_callback): Unit = {
    this.wkhtmltopdf_set_progress_changed_callback(converter, cb)
  }

  def setFinishedCallback(converter: Pointer, cb: wkhtmltopdf_int_callback): Unit = {
    this.wkhtmltopdf_set_finished_callback(converter, cb)
  }

  def wkhtmltopdf_init(useGraphics: Int): Int

  def wkhtmltopdf_deinit(): Int

  def wkhtmltopdf_extended_qt(): Int

  def wkhtmltopdf_version(): String

  def wkhtmltopdf_create_global_settings(): Pointer

  def wkhtmltopdf_set_global_setting(globalSettings: Pointer, name: String, value: String): Int

  def wkhtmltopdf_get_global_setting(globalSettings: Pointer, name: String, memory: Memory, memorySize: Int): Int

  def wkhtmltopdf_destroy_global_settings(pointer: Pointer): Unit

  def wkhtmltopdf_create_object_settings(): Pointer

  def wkhtmltopdf_set_object_setting(objectSettings: Pointer, name: String, value: String): Int

  def wkhtmltopdf_get_object_setting(objectSettings: Pointer, name: String, memory: Memory, memorySize: Int): Int

  def wkhtmltopdf_destroy_object_settings(pointer: Pointer): Unit

  def wkhtmltopdf_create_converter(globalSettings: Pointer): Pointer

  def wkhtmltopdf_set_warning_callback(converter: Pointer, cb: wkhtmltopdf_str_callback): Unit

  def wkhtmltopdf_set_error_callback(converter: Pointer, cb: wkhtmltopdf_str_callback): Unit

  def wkhtmltopdf_set_phase_changed_callback(converter: Pointer, cb: wkhtmltopdf_void_callback): Unit

  def wkhtmltopdf_set_progress_changed_callback(converter: Pointer, cb: wkhtmltopdf_int_callback): Unit

  def wkhtmltopdf_set_finished_callback(converter: Pointer, cb: wkhtmltopdf_int_callback): Unit

  def wkhtmltopdf_add_object(converter: Pointer, objectSettings: Pointer, data: String): Unit

  def wkhtmltopdf_current_phase(converter: Pointer): Int

  def wkhtmltopdf_phase_count(converter: Pointer): Int

  def wkhtmltopdf_phase_description(converter: Pointer, phase: Int): String

  def wkhtmltopdf_progress_string(converter: Pointer): String

  def wkhtmltopdf_http_error_code(converter: Pointer): Int

  def wkhtmltopdf_convert(converter: Pointer): Int

  def wkhtmltopdf_get_output(converter: Pointer, out: PointerByReference): Long

  def wkhtmltopdf_destroy_converter(converter: Pointer): Unit

}
