package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.AssetService
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.AwsDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, NumericValue, RoadLink, Value}
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingObstacle, ObstacleService, PavedRoadService, PedestrianCrossingService, RailwayCrossingService, TrafficLightService, HeightLimit => _, WidthLimit => _}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

case class Dataset(datasetId: String, geoJson: Map[String, Any], roadlinks: List[List[BigInt]])

class AwsService(vvhClient: VVHClient,
                 val onOffLinearAssetService: OnOffLinearAssetService,
                 val roadLinkService: RoadLinkService,
                 val linearAssetService: LinearAssetService,
                 val speedLimitService: SpeedLimitService,
                 val pavedRoadService: PavedRoadService,
                 val roadWidthService: RoadWidthService,
                 val manoeuvreService: ManoeuvreService,
                 val assetService: AssetService,
                 val obstacleService: ObstacleService,
                 val pedestrianCrossingService: PedestrianCrossingService,
                 val railwayCrossingService: RailwayCrossingService,
                 val trafficLightService: TrafficLightService,
                 val massTransitLaneService: MassTransitLaneService,
                 val numberOfLanesService: NumberOfLanesService
                ) {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def awsDao: AwsDao = new AwsDao

  implicit val formats = Serialization.formats(NoTypeHints)

  final val AwsUser = "AwsUpdater"

  val allDatasetStatus = Map(
    0 -> "Inserted successfuly",
    1 -> "Amount of features and roadlinks do not match",
    2 -> "Errors in the features",
    3 -> "Processed successfuly",
    4 -> "Errors while processing"
  )

  val allFeatureStatus = Map(
    0 -> "Inserted successfuly",
    1 -> "Errors while validating",
    2 -> "Processed successfuly",
    3 -> "SpeedLimit with invalid speed",
    4 -> "PavementClass with invalid pavement class",
    5 -> "Obstacle with invalid class",
    6 -> "Invalid sideCode",
    7 -> "Geometry type not found",
    8 -> "Roadlink with no type in properties",
    9 -> "Errors while updating",
    10 -> "Wrong roadlinks"
  )

  private def updateDatasetAndFeature(datasetId: String, featureId: Long, datasetStatus: Int, featureStatus: String): Unit = {
    awsDao.updateFeatureStatus(featureId, featureStatus)
    awsDao.updateDatasetStatus(datasetId, datasetStatus)
  }

  private def linkIdValidation(datasetId: String, featureId: Long, linkIds: Set[Long]): Unit = {
    val roadLinks = roadLinkService.getRoadsLinksFromVVH(linkIds, false)
    if(!(roadLinks.count(road => road.administrativeClass != State) == linkIds.size))
    {
      updateDatasetAndFeature(datasetId, featureId, 2, "10")
    }
  }

  private def validatePoint(datasetId: String, featureId: Long, properties: Map[String, Any], assetType: String): Unit = {
    assetType match {
      case "obstacle" =>
        properties.get("class") match {
          case Some(value) =>
            if (!Set(1, 2).contains(value.asInstanceOf[BigInt].intValue())) {
              updateDatasetAndFeature(datasetId, featureId, 2, "5")
            }
          case None =>
            updateDatasetAndFeature(datasetId, featureId, 2, "5")
        }
    }
  }

  private def validateRoadlink(datasetId: String, featureId: Long, properties: Map[String, Any]) = {
    val speedLimit = properties.get("speedLimit")
    val pavementClass = properties.get("pavementClass")
    val sideCode = properties.get("sideCode")

    speedLimit match {
      case Some(value) =>
        if (!Set("20", "30", "40", "50", "60", "70", "80", "90", "100", "120").contains(value.asInstanceOf[String])) {
          updateDatasetAndFeature(datasetId, featureId, 2, "3")
        }
      case None =>
    }

    pavementClass match {
      case Some(value) =>
        if (!Seq("1", "2", "10", "20", "30", "40", "50").contains(value.asInstanceOf[String])) {
          updateDatasetAndFeature(datasetId, featureId, 2, "4")
        }
      case None =>
    }

    sideCode match {
      case Some(value) =>
        if (!(value.asInstanceOf[BigInt] == 1)) {
          updateDatasetAndFeature(datasetId, featureId, 2, "6")
        }
      case None =>
        updateDatasetAndFeature(datasetId, featureId, 2, "6")
    }
  }

  private def updatePoint(properties: Map[String, Any], link: RoadLink, assetType: String, assetCoordinates: List[Double]): Unit = {
    assetType match {
      case "obstacle" =>
        val obstacleType = properties("class").asInstanceOf[BigInt]
        val newObstacle = IncomingObstacle(assetCoordinates.head, assetCoordinates(1), link.linkId, obstacleType.intValue())

        obstacleService.checkDuplicates(newObstacle) match {
          case Some(value) =>
            obstacleService.expireWithoutTransaction(value.id, AwsUser)
            obstacleService.create(newObstacle, AwsUser, link, false)
          case None =>
            obstacleService.create(newObstacle, AwsUser, link, false)
        }
    }
  }

  private def updateRoadlink(properties: Map[String, Any], links: Seq[RoadLink]) = {
    val speedLimit = properties.get("speedLimit")
    val pavementClass = properties.get("pavementClass")

    links.foreach { link =>
      speedLimit match {
        case Some(value) =>
          val speedLimitValue = value.asInstanceOf[String]
          val speedLimitsOnRoadLink = speedLimitService.getExistingAssetByRoadLink(link, false)

          if (speedLimitsOnRoadLink.isEmpty) {
            val newSpeedLimitAsset = NewLinearAsset(link.linkId, 0, link.length, NumericValue(speedLimitValue.toInt), properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
            speedLimitService.createMultiple(Seq(newSpeedLimitAsset), speedLimitValue.toInt, AwsUser, vvhClient.roadLinkData.createVVHTimeStamp(), (_, _) => Unit)
          } else
            speedLimitsOnRoadLink.foreach { sl =>
              val newSpeedLimitAsset = NewLinearAsset(link.linkId, sl.startMeasure, sl.endMeasure, NumericValue(speedLimitValue.toInt), properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
              speedLimitService.update(sl.id, Seq(newSpeedLimitAsset), AwsUser)
            }
        case None =>
      }

      pavementClass match {
        case Some(value) =>
          val pavementClassValue = DynamicValue(DynamicAssetValue(Seq(DynamicProperty("paallysteluokka", "single_choice", required = false, Seq(DynamicPropertyValue(value.asInstanceOf[String].toInt))))))

          val duplicate = pavedRoadService.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq(link.linkId))
          duplicate.isEmpty match {
            case false =>
              pavedRoadService.update(Seq(duplicate.head.id), pavementClassValue, AwsUser)
            case true =>
              val newPavementClassAsset = NewLinearAsset(link.linkId, 0, link.length, pavementClassValue, properties("sideCode").asInstanceOf[BigInt].intValue(), vvhClient.roadLinkData.createVVHTimeStamp(), None)
              pavedRoadService.create(Seq(newPavementClassAsset), PavedRoad.typeId, AwsUser)
          }
        case None =>
      }
    }
  }

  def validateAndInsertDataset(dataset: Dataset): Int = {
    var featuresWithoutIds = 0

    val assets = dataset.geoJson("features").asInstanceOf[List[Map[String, Any]]]
    val roadlinks = dataset.roadlinks

    if (assets.length != dataset.roadlinks.length) {
      awsDao.insertDataset(dataset.datasetId, write(dataset.geoJson), write(dataset.roadlinks), 1)
    }
    else{
      awsDao.insertDataset(dataset.datasetId, write(dataset.geoJson), write(dataset.roadlinks), 0)
    }

    if (awsDao.getDatasetStatus(dataset.datasetId) != 1) {

      (roadlinks, assets).zipped.foreach((featureRoadlinks, feature) => {
        feature.get("properties") match {
          case Some(prop) =>
            val properties = prop.asInstanceOf[Map[String, Any]]
            properties.get("id") match {
              case Some(id) =>
                val featureId = id.asInstanceOf[BigInt].longValue()
                try {
                  awsDao.insertFeature(featureId, dataset.datasetId, 0)

                  linkIdValidation(dataset.datasetId, featureId, featureRoadlinks.map(number => number.longValue()).toSet)

                  val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type")
                  assetTypeGeometry match {
                    case "LineString" =>
                      properties("type").asInstanceOf[String] match {
                        case "Roadlink" => validateRoadlink(dataset.datasetId, featureId, properties)
                        case _ =>
                          updateDatasetAndFeature(dataset.datasetId, featureId, 2, "8")
                      }
                    case "Point" => validatePoint(dataset.datasetId, featureId, properties, properties("type").asInstanceOf[String])
                    case _ =>
                      updateDatasetAndFeature(dataset.datasetId, featureId, 2, "7")
                  }
                } catch {
                  case _: Throwable =>
                    updateDatasetAndFeature(dataset.datasetId, featureId, 2, "1")
                }
              case None =>
                featuresWithoutIds += 1
                awsDao.updateDatasetStatus(dataset.datasetId, 2)
            }
          case None =>
            featuresWithoutIds += 1
            awsDao.updateDatasetStatus(dataset.datasetId, 2)
        }
      }
      )
    }
    featuresWithoutIds
  }

  def updateDataset(dataset: Dataset) = {
    if (awsDao.getDatasetStatus(dataset.datasetId) != 1) {
      val assets = dataset.geoJson("features").asInstanceOf[List[Map[String, Any]]]
      val roadlinks = dataset.roadlinks

      var roadlinksCount = 0
      assets.foreach(feature => {
        val featureRoadlinks = roadlinks(roadlinksCount)
        roadlinksCount += 1

        val properties = feature("properties").asInstanceOf[Map[String, Any]]

        properties.get("id") match {
          case Some(id) =>
            val featureId = id.asInstanceOf[BigInt].longValue()
            val status = awsDao.getFeatureStatus(featureId, dataset.datasetId)
            try {
              if (status == "0") {

                val assetTypeGeometry = feature("geometry").asInstanceOf[Map[String, Any]]("type").asInstanceOf[String]
                val assetType = properties("type").asInstanceOf[String]

                assetTypeGeometry match {
                  case "LineString" => assetType match {
                    case "Roadlink" =>
                      val links = roadLinkService.getRoadLinksAndComplementariesFromVVH(featureRoadlinks.map(_.longValue()).toSet, false)
                        .filter(link => link.functionalClass != 99 && link.functionalClass != 7 && link.functionalClass != 8)

                      updateRoadlink(properties, links)
                  }
                    awsDao.updateFeatureStatus(featureId, "2")

                  case "Point" =>
                    val assetCoordinates = feature("geometry").asInstanceOf[Map[String, Any]]("coordinates").asInstanceOf[List[List[Double]]].head
                    val link = roadLinkService.getRoadLinkAndComplementaryFromVVH(featureRoadlinks.head.longValue(), false).get
                    updatePoint(properties, link, assetType, assetCoordinates)
                    awsDao.updateFeatureStatus(featureId, "2")
                }
              }
            } catch {
              case _: Throwable =>
                awsDao.updateFeatureStatus(featureId, "9")
            }
          case None =>
        }
      }
      )

      val errors = awsDao.getProcessedDatasetFeaturesForErrors(dataset.datasetId)
      if (errors == 0) {
        awsDao.updateDatasetStatus(dataset.datasetId, 3)
      } else {
        awsDao.updateDatasetStatus(dataset.datasetId, 4)
      }
    }
  }

  def getDatasetStatusById(datasetId: String): String = {
    allDatasetStatus(awsDao.getDatasetStatus(datasetId))
  }

  def getFeatureErrorsByDatasetId(datasetId: String, datasetFeaturesWithoutIds: Int): Any = {
    val featuresStatusCode = awsDao.getAllFeatureIdAndStatusByDataset(datasetId).filter { case (_, status) => status != "0,2" }

    var featuresStatusMap = featuresStatusCode.map(tuple =>
      Map(
        "FeatureId" -> tuple._1.toString,
        "Message" -> tuple._2.split(",").tail.map(message => allFeatureStatus(message.toInt))
      )
    )

    if (datasetFeaturesWithoutIds != 0) {
      featuresStatusMap = featuresStatusMap :+ Map("Features without ids" -> datasetFeaturesWithoutIds.toString)
    }

    featuresStatusMap
  }
}