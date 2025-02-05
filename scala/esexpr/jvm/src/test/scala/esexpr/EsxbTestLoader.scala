package esexpr

import java.nio.file.*
import zio.*
import zio.stream.*
import org.apache.commons.io.FilenameUtils

trait EsxbTestLoader {
  val testCaseDir: String

  def loadTestCases(): ZStream[Any, Nothing, EsxbTestCase] =
    ZStream.fromJavaStreamScoped(
        ZIO.fromAutoCloseable(ZIO.succeed { Files.list(Path.of("../../", testCaseDir)) })
    )
      .filter { path => FilenameUtils.getExtension(path.getFileName.toString) == "esxb" }
      .mapZIO { path =>
        ZIO.succeed {
          val bareFileName = FilenameUtils.removeExtension(path.getFileName.toString)

          val jsonText = Files.readString(path.resolveSibling(bareFileName + ".json"))
          val esxbContent = Files.readAllBytes(path)

          EsxbTestCase(bareFileName, jsonText, Chunk.fromArray(esxbContent))
        }
      }
      .orDie
}
