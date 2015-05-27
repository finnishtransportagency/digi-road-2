package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.TowardsLinkChain
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection.AgainstLinkChain
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, Queries}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.user.UserProvider
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass

// FIXME:
// - rename to speed limit service
// - move common asset functionality to asset service
class OracleLinearAssetProvider(eventbus: DigiroadEventBus, productionRoadLinkService: RoadLinkService = RoadLinkService) extends LinearAssetProvider {
  val dao: OracleLinearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = productionRoadLinkService
  }
  val logger = LoggerFactory.getLogger(getClass)

  private def toSpeedLimit(linkAndPositionNumber: (Long, Long, Int, Option[Int], Seq[Point], Int, GeometryDirection)): SpeedLimitLink = {
    val (id, roadLinkId, sideCode, limit, points, positionNumber, geometryDirection) = linkAndPositionNumber

    val towardsLinkChain = geometryDirection match {
      case TowardsLinkChain => true
      case AgainstLinkChain => false
    }

    SpeedLimitLink(id, roadLinkId, sideCode, limit, points, positionNumber, towardsLinkChain)
  }

  private def getLinkEndpoints(link: (Long, Long, Int, Option[Int], Seq[Point])): (Point, Point) = {
    val (_, _, _, _, points) = link
    GeometryUtils.geometryEndpoints(points)
  }

  private def getLinksWithPositions(links: Seq[(Long, Long, Int, Option[Int], Seq[Point])]): Seq[SpeedLimitLink] = {
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val (id, roadLinkId, sideCode, limit, points) = chainedLink.rawLink
      toSpeedLimit((id, roadLinkId, sideCode, limit, points, chainedLink.linkPosition, chainedLink.geometryDirection))
    }
  }

  override def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[SpeedLimitLink] = {
    Database.forDataSource(ds).withDynTransaction {
      val (speedLimits, linkGeometries) = dao.getSpeedLimitLinksByBoundingBox(bounds, municipalities)
      eventbus.publish("speedLimits:linkGeometriesRetrieved", linkGeometries)
      speedLimits.groupBy(_._1).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  override def getSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      loadSpeedLimit(speedLimitId)
    }
  }

  private def loadSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    val links = dao.getSpeedLimitLinksById(speedLimitId)
    if (links.isEmpty) None
    else {
      val linkEndpoints: List[(Point, Point)] = links.map(getLinkEndpoints).toList
      val limitEndpoints = LinearAsset.calculateEndPoints(linkEndpoints)
      val (modifiedBy, modifiedDateTime, createdBy, createdDateTime, limit) = dao.getSpeedLimitDetails(speedLimitId)
      Some(SpeedLimit(speedLimitId, limit, limitEndpoints,
        modifiedBy, modifiedDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        createdBy, createdDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        getLinksWithPositions(links)))
    }
  }

  override def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.updateSpeedLimitValue(id, value, username)
    }
  }

  override def updateSpeedLimitValues(ids: Seq[Long], value: Int, username: String): Seq[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      ids.map(OracleLinearAssetDao.updateSpeedLimitValue(_, value, username)).flatten
    }
  }

  override def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, limit: Int, username: String): Seq[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      val newId = OracleLinearAssetDao.splitSpeedLimit(id, roadLinkId, splitMeasure, limit, username)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  override def fillPartiallyFilledRoadLinks(linkGeometries: Map[Long, RoadLinkForSpeedLimit]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      logger.info("Filling partially filled road links, road link count in bounding box: " + linkGeometries.size)
      OracleLinearAssetDao.fillPartiallyFilledRoadLinks(linkGeometries)
      logger.info("...done with filling.")
    }
  }

}
