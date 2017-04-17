package parser

import org.apache.poi.ss.usermodel.{Workbook, WorkbookFactory}

object io {

  def load(fileName: String): Workbook =
    WorkbookFactory.create(getClass.getClassLoader.getResourceAsStream(fileName))

}
