package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.RoadLinkAssociatedPointAsset
import fi.liikennevirasto.digiroad2.asset.oracle.Queries
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import slick.jdbc.StaticQuery.interpolation

case class PersistedPedestrianCrossing(id: Long, mmlId: Long,
                                       lon: Double, lat: Double,
                                       mValue: Double, floating: Boolean,
                                       municipalityCode: Int,
                                       createdBy: Option[String] = None,
                                       createdDateTime: Option[DateTime] = None,
                                       modifiedBy: Option[String] = None,
                                       modifiedDateTime: Option[DateTime] = None) extends RoadLinkAssociatedPointAsset

object OraclePedestrianCrossingDao {
  def expire(id: Long, username: String) {
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    sqlu"update asset set valid_to = sysdate where id = $id".first
  }

  def fetchByFilter(queryFilter: String => String): Seq[PersistedPedestrianCrossing] = {
    val query =
      """
        select a.id, pos.mml_id, a.geometry, pos.start_measure, a.floating, a.municipality_code, a.created_by, a.created_date, a.modified_by, a.modified_date
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null)"
    StaticQuery.queryNA[PersistedPedestrianCrossing](queryWithFilter).iterator.toSeq
  }

  implicit val getPointAsset = new GetResult[PersistedPedestrianCrossing] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val mmlId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val municipalityCode = r.nextInt()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      PersistedPedestrianCrossing(id, mmlId, point.x, point.y, mValue, floating, municipalityCode, createdBy, createdDateTime, modifiedBy, modifiedDateTime)
    }
  }
}
