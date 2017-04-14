package parser

import org.apache.poi.xssf.usermodel.XSSFWorkbook

object io {

  def load(fileName: String): XSSFWorkbook =
    new XSSFWorkbook(getClass.getClassLoader.getResourceAsStream(fileName))

}
