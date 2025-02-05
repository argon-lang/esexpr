package esexpr

import zio.Chunk

final case class EsxbTestCase(name: String, jsonContent: String, esxbData: Chunk[Byte])
