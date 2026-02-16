package esexpr

import zio.*
import zio.stream.*

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array
import dev.argon.util.async.TypedArrayUtil

import EsxbTestLoader.*

trait EsxbTestLoader {
  val testCaseDir: String

  def loadTestCases(): ZStream[Any, Nothing, EsxbTestCase] =
    ZStream.unwrap(
      ZIO.fromPromiseJS(FSPromises.readdir(testCaseDir))
        .map(ZStream.fromIterable(_))
    )
      .filter { path => Path.extname(path) == ".esxb" }
      .mapZIO { path =>
        val bareFileName = Path.basename(path, ".esxb")

        for
          jsonText <- ZIO.fromPromiseJS(FSPromises.readFile(Path.join(testCaseDir, bareFileName + ".json"), "utf-8"))
          esxbContent <- ZIO.fromPromiseJS(FSPromises.readFile(Path.join(testCaseDir, path)))
        yield EsxbTestCase(bareFileName, jsonText, TypedArrayUtil.toByteChunk(esxbContent))
      }
      .orDie
}

object EsxbTestLoader {
  @js.native
  @JSImport("node:fs/promises", JSImport.Namespace)
  object FSPromises extends js.Object {
    def readdir(path: String): js.Promise[js.Array[String]] = js.native
    def readFile(path: String, encoding: String): js.Promise[String] = js.native
    def readFile(path: String): js.Promise[Uint8Array] = js.native
  }

  @js.native
  @JSImport("node:path", JSImport.Namespace)
  object Path extends js.Object {
    def join(paths: String*): String = js.native
    def extname(path: String): String = js.native
    def basename(path: String, suffix: String): String = js.native
    def resolve(path: String): String = js.native
  }
}

