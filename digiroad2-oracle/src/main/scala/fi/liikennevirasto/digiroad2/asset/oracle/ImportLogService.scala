package fi.liikennevirasto.digiroad2.asset.oracle

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation

import org.slf4j.LoggerFactory

import Queries.nextPrimaryKeyId
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds

object ImportLogService {
  val logger = LoggerFactory.getLogger(getClass)

  def nextPrimaryKeySeqValue = {
    nextPrimaryKeyId.as[Long].first
  }

  def save(content: String): Long = {
    Database.forDataSource(ds).withDynTransaction {
      val id = nextPrimaryKeySeqValue
      sqlu"""
        insert into import_log(id, content)
        values ($id, $content)
      """.execute
      id
    }
  }

  def save(id: Long, content: String): Long = {
    Database.forDataSource(ds).withDynTransaction {
      sqlu"""
        update import_log set content = $content
          where id = $id
      """.execute
      id
    }
  }

  def get(id: Long): Option[String] = {
    Database.forDataSource(ds).withDynTransaction {
      sql"select content from import_log where id = $id".as[String].firstOption
    }
  }

}
