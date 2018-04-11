package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.{CycleOrPedestrianPath, Property, PropertyTypes, PropertyValue}
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.TractorRoad
import fi.liikennevirasto.digiroad2.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class ChangedRoadAddress(roadAddress : RoadAddress, link: RoadLink)

class RoadAddressesService(val eventbus: DigiroadEventBus, roadLinkServiceImplementation: RoadLinkService) {

  private val roadNumberPublicId = "tie"          // Tienumero
  private val roadPartNumberPublicId = "osa"      // Tieosanumero
  private val startMeasurePublicId = "aet"        // Etaisyys
  private val trackCodePublicId = "ajr"           // Ajorata
  private val sideCodePublicId = "puoli"

  val roadAddressDAO = new RoadAddressDAO()
  val logger = LoggerFactory.getLogger(getClass)
  val geometryTransform = new GeometryTransform

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedRoadAddress] = {

    val roadAddresses =
      withDynTransaction {
        roadAddressDAO.getRoadAddress(roadAddressDAO.withBetweenDates(sinceDate, untilDate))
      }

    val roadLinks = roadLinkServiceImplementation.getRoadLinksAndComplementariesFromVVH(roadAddresses.map(_.linkId).toSet)
    val roadLinksWithoutWalkways = roadLinks.filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)

    roadAddresses.flatMap { roadAddress =>
      roadLinksWithoutWalkways.find(_.linkId == roadAddress.linkId).map { roadLink =>
        ChangedRoadAddress(
          roadAddress = RoadAddress(
            id = roadAddress.id,
            roadNumber = roadAddress.roadNumber,
            roadPartNumber = roadAddress.roadPartNumber,
            track = roadAddress.track,
            discontinuity = roadAddress.discontinuity,
            startAddrMValue = roadAddress.startAddrMValue,
            endAddrMValue = roadAddress.endAddrMValue,
            startDate = roadAddress.startDate,
            endDate = roadAddress.endDate,
            lrmPositionId = roadAddress.lrmPositionId,
            linkId = roadAddress.linkId,
            startMValue = roadAddress.startMValue,
            endMValue = roadAddress.endMValue,
            sideCode = roadAddress.sideCode,
            floating = roadAddress.floating,
            geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue),
            expired = roadAddress.expired,
            createdBy = roadAddress.createdBy,
            createdDate = roadAddress.createdDate,
            modifiedDate = roadAddress.modifiedDate
          ),
          link = roadLink
        )
      }
    }
  }

  def getRoadAddressPropertiesByLinkId(persistedStop: PersistedMassTransitStop, roadLink: RoadLinkLike, oldProperties: Seq[Property]): Seq[Property] = {
    val (address, roadSide) = geometryTransform.resolveAddressAndLocation(Point(persistedStop.lon, persistedStop.lat), persistedStop.bearing.get, persistedStop.mValue, persistedStop.linkId, persistedStop.validityDirection.get)

    val newRoadAddressProperties =
      Seq(
        Property(0, roadNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.road.toString, Some(address.road.toString)))),
        Property(0, roadPartNumberPublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.roadPart.toString, Some(address.roadPart.toString)))),
        Property(0, startMeasurePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.addrM.toString, Some(address.addrM.toString)))),
        Property(0, trackCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(address.track.value.toString, Some(address.track.value.toString)))),
        Property(0, sideCodePublicId, PropertyTypes.ReadOnlyNumber, values = Seq(PropertyValue(roadSide.value.toString, Some(roadSide.value.toString))))
      )

    oldProperties.filterNot(op => newRoadAddressProperties.map(_.publicId).contains(op.publicId)) ++ newRoadAddressProperties
  }
}
