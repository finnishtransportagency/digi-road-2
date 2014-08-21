package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.linearasset.Point
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitLink
import scala.slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

class OracleLinearAssetProvider extends LinearAssetProvider {
  private def toSpeedLimit(entity: (Long, Long, Int, Int, Seq[(Double, Double)])): SpeedLimitLink = {
    val (id, roadLinkId, sideCode, limit, points) = entity
    SpeedLimitLink(id, roadLinkId, sideCode, limit, points.map { case (x, y) => Point(x, y) })
  }

  override def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.getSpeedLimits(bounds).map(toSpeedLimit)
    }
  }

  def calculateSpeedLimitEndPoints(links: List[(Point, Point)]): Set[Point] = {
    def distancesFromPoint(point: Point, pointsToCompare: List[Point]): Map[Point, Double] = {
      pointsToCompare.foldLeft(Map.empty[Point, Double]) { (acc, x) =>
        acc + (x -> point.distanceTo(x))
      }
    }

    if (links.length == 1) Set(links.head._1, links.head._2)
    else {
      val distances: Map[Point, Map[Point, Double]] = links.foldLeft(Map.empty[Point, Map[Point, Double]]) { (acc, link) =>
        val remainingLinks = links.toSet - link
        val pointsToCompare = remainingLinks.foldLeft(List.empty[Point]) { (acc, link) =>
          val (firstPoint, secondPoint) = link
          firstPoint :: secondPoint :: acc
        }
        val distancesFromFirstPoint = distancesFromPoint(link._1, pointsToCompare)
        val distancesFromSecondPoint = distancesFromPoint(link._2, pointsToCompare)
        acc + (link._1 -> distancesFromFirstPoint) + (link._2 -> distancesFromSecondPoint)
      }

      val shortestDistances: Map[Point, Double] = distances.map { distancesFromPoint =>
        val (point, distances) = distancesFromPoint
        val shortestDistance = distances.toList.sortBy(_._2).head._2
        (point -> shortestDistance)
      }

      val sortedPoints: List[(Point, Double)] = shortestDistances.toList.sortWith { (point1, point2) =>
        point1._2 > point2._2
      }

      sortedPoints.take(2).map(_._1).toSet
    }
  }
}
