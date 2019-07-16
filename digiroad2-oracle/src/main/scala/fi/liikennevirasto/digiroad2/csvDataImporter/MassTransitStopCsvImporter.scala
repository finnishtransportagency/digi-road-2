package fi.liikennevirasto.digiroad2.csvDataImporter

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, DigiroadEventBus, ExcludedRow, FloatingReason, GeometryUtils, IncompleteRow, MalformedRow, Point, Status}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, FloatingAsset, Position, PropertyValue, SimpleProperty, Unknown}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, NewMassTransitStop, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.apache.commons.lang3.StringUtils.isBlank

import scala.util.Try

class MassTransitStopCsvOperation(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  lazy val propertyUpdater = new Updater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus)
  lazy val positionUpdater = new PositionUpdater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus)
  lazy val creator = new Creator(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus)

  private def getStrategies(): Seq[CsvOperations] = {
    Seq(propertyUpdater, creator, positionUpdater)
  }

  private def getStrategy(csvRowWithHeaders: Map[String, String]): CsvOperations = {
    val strategies = getStrategies()
    strategies.find(strategy => strategy.is(csvRowWithHeaders)).getOrElse(throw new UnsupportedOperationException(s"Please check the combination between Koordinaatti and  Valtakunnallinen ID"))
  }

  def importAssets(inputStream: InputStream, fileName: String, user: User, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
    val streamReader = new InputStreamReader(inputStream, "UTF-8")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    val csvRead = csvReader.allWithHeaders()
    val strategy = getStrategy(csvRead.head)

    strategy.importAssets(csvRead: List[Map[String, String]], fileName, user, roadTypeLimitations)
  }
}


trait MassTransitStopCsvImporter extends PointAssetCsvImporter {
  def roadLinkService: RoadLinkService
  def eventBus: DigiroadEventBus

  class AssetNotFoundException(externalId: Long) extends RuntimeException
  case class CsvAssetRow(externalId: Option[Long], properties: Seq[AssetProperty])

  type ExcludedRoadLinkTypes = List[AdministrativeClass]

  override val logInfo: String = "bus stop import"
  lazy val massTransitStopService: MassTransitStopService = {
    class MassTransitStopServiceWithDynTransaction(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService) extends MassTransitStopService {
      override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

      override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

      override val tierekisteriClient: TierekisteriMassTransitStopClient = tierekisteriMassTransitStopClient
      override val massTransitStopDao: MassTransitStopDao = new MassTransitStopDao
      override val municipalityDao: MunicipalityDao = new MunicipalityDao
      override val geometryTransform: GeometryTransform = new GeometryTransform(roadAddressService)
    }
    new MassTransitStopServiceWithDynTransaction(eventBus, roadLinkService, roadAddressService)
  }


  private val isValidTypeEnumeration = Set(1, 2, 3, 4, 5, 99)
  private val singleChoiceValueMappings = Set(1, 2, 99).map(_.toString)
  private val stopAdministratorProperty = "tietojen_yllapitaja"
  private val stopAdministratorValueMappings = Set(1, 2, 3, 99).map(_.toString)

  private val textFieldMappings = Map(
    "Pysäkin nimi" -> "nimi_suomeksi",
    "Ylläpitäjän tunnus" -> "yllapitajan_tunnus",
    "Matkustajatunnus" -> "matkustajatunnus",
    "Pysäkin nimi ruotsiksi" -> "nimi_ruotsiksi",
    "Liikennöintisuunta" -> "liikennointisuunta",
    "Lisätiedot" -> "lisatiedot",
    "Vyöhyketieto" -> "vyohyketieto"
  )

  private val multipleChoiceFieldMappings = Map(
    "Pysäkin tyyppi" -> "pysakin_tyyppi"
  )

  private val singleChoiceFieldMappings = Map(
    "Aikataulu" -> "aikataulu",
    "Katos" -> "katos",
    "Mainoskatos" -> "mainoskatos",
    "Penkki" -> "penkki",
    "Pyöräteline" -> "pyorateline",
    "Sähköinen aikataulunäyttö" -> "sahkoinen_aikataulunaytto",
    "Valaistus" -> "valaistus",
    "Saattomahdollisuus henkilöautolla" -> "saattomahdollisuus_henkiloautolla",
    "Korotettu" -> "korotettu",
    "Roska-astia" -> "roska_astia",
    "Tietojen ylläpitäjä" -> stopAdministratorProperty
  )

   protected val externalIdMapping = Map("Valtakunnallinen ID" -> "external_id")

  override val intValueFieldsMapping = externalIdMapping

  val optionalMappings = externalIdMapping ++ coordinateMappings

  val mappings: Map[String, String] = textFieldMappings ++ multipleChoiceFieldMappings ++ singleChoiceFieldMappings

  private def municipalityValidation(municipality: Int)(user: User): Unit = {
    if (!user.isAuthorizedToWrite(municipality)) {
      throw new IllegalArgumentException("User does not have write access to municipality")
    }
  }
  override def createAsset(pointAssetAttributes: Seq[CsvAssetRowAndRoadLink], user: User, result: ImportResultData): ImportResultData =
    throw new UnsupportedOperationException("Not Supported Method")

  def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] =
    throw new UnsupportedOperationException("Not Supported Method")

  private def updateAssetByExternalId(externalId: Long, optPosition: Option[Position],properties: Seq[AssetProperty], user: User): MassTransitStopWithProperties = {
    val optionalAsset = massTransitStopService.getMassTransitStopByNationalId(externalId, municipalityValidation)
    optionalAsset match {
      case Some(asset) =>
        massTransitStopService.updateExistingById(asset.id, optPosition, properties.map(prop => SimpleProperty(prop.columnName, prop.value.asInstanceOf[Seq[PropertyValue]])).toSet, user.username, (_, _) => Unit)
      case None => throw new AssetNotFoundException(externalId)
    }
  }

  case class CsvImportMassTransitStop(id: Long, floating: Boolean, roadLinkType: AdministrativeClass) extends FloatingAsset {}

  private def updateAssetByExternalIdLimitedByRoadType(externalId: Long, properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], username: String): Either[AdministrativeClass, MassTransitStopWithProperties] = {
    def massTransitStopTransformation(stop: PersistedMassTransitStop): (CsvImportMassTransitStop, Option[FloatingReason]) = {
      val roadLink = vvhClient.roadLinkData.fetchByLinkId(stop.linkId)
      val (floating, floatingReason) = massTransitStopService.isFloating(stop, roadLink)
      (CsvImportMassTransitStop(stop.id, floating, roadLink.map(_.administrativeClass).getOrElse(Unknown)), floatingReason)
    }

    val optionalAsset = massTransitStopService.getByNationalId(externalId, municipalityValidation, massTransitStopTransformation)
    optionalAsset match {
      case Some(asset) =>
        val roadLinkType = asset.roadLinkType
        if (roadTypeLimitations(roadLinkType)) Right(massTransitStopService.updateExistingById(asset.id, None, properties.map(prop => SimpleProperty(prop.columnName, prop.value.asInstanceOf[Seq[PropertyValue]])).toSet, username, (_, _) => Unit))
        else Left(roadLinkType)
      case None => throw new AssetNotFoundException(externalId)
    }
  }

  def updateAsset(externalId: Long, optPosition: Option[Position], properties: Seq[AssetProperty], roadTypeLimitations: Set[AdministrativeClass], user: User): ExcludedRoadLinkTypes = {
    // Remove livi-id from properties, we don't want to change is with CSV
    val propertiesWithoutLiviId = properties.filterNot(_.columnName == "yllapitajan_koodi")
    if (roadTypeLimitations.nonEmpty) {
      val result: Either[AdministrativeClass, MassTransitStopWithProperties] = updateAssetByExternalIdLimitedByRoadType(externalId, propertiesWithoutLiviId, roadTypeLimitations, user.username)
      result match {
        case Left(roadLinkType) => List(roadLinkType)
        case _ => Nil
      }
    } else {
      updateAssetByExternalId(externalId,  optPosition, propertiesWithoutLiviId, user)
      Nil
    }
  }

  private def resultWithType(result: (MalformedParameters, List[AssetProperty]), assetType: Int): ParsedRow = {
    result.copy(_2 = result._2 match {
      case List(AssetProperty("pysakin_tyyppi", xs)) => List(AssetProperty("pysakin_tyyppi", PropertyValue(assetType.toString) :: xs.asInstanceOf[Seq[PropertyValue]].toList))
      case _ => List(AssetProperty("pysakin_tyyppi", Seq(PropertyValue(assetType.toString))))
    })
  }

  private def maybeInt(string: String): Option[Int] = {
    Try(string.toInt).toOption
  }

  private def assetTypeToProperty(assetTypes: String): ParsedRow = {
    val invalidAssetType = (List("Pysäkin tyyppi"), Nil)
    val types = assetTypes.split(',')
    if (types.isEmpty) invalidAssetType
    else {
      types.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, assetType) =>
        maybeInt(assetType.trim)
          .filter(isValidTypeEnumeration)
          .map(typeEnumeration => resultWithType(result, typeEnumeration))
          .getOrElse(invalidAssetType)
      }
    }
  }

  override def assetRowToProperties(csvRowWithHeaders: Map[String, String]): ParsedRow = {
    csvRowWithHeaders.foldLeft((Nil: MalformedParameters, Nil: ParsedProperties)) { (result, parameter) =>
      val (key, value) = parameter
      if (isBlank(value)) {
        result
      } else {
        if (longValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyDoubleType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if(intValueFieldsMapping.contains(key)) {
          val (malformedParameters, properties) = verifyIntType(key, value.toString)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (textFieldMappings.contains(key)) {
          result.copy(_2 = AssetProperty(columnName = textFieldMappings(key), value = Seq(PropertyValue(value))) :: result._2)
        } else if (multipleChoiceFieldMappings.contains(key)) {
          val (malformedParameters, properties) = assetTypeToProperty(value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else if (singleChoiceFieldMappings.contains(key)) {
          val (malformedParameters, properties) = assetSingleChoiceToProperty(key, value)
          result.copy(_1 = malformedParameters ::: result._1, _2 = properties ::: result._2)
        } else {
          result
        }
      }
    }
  }

  private def assetSingleChoiceToProperty(parameterName: String, assetSingleChoice: String): ParsedRow = {
    // less than ideal design but the simplest solution. DO NOT REPEAT IF MORE FIELDS REQUIRE CUSTOM VALUE VALIDATION
    val isValidStopAdminstratorValue = singleChoiceFieldMappings(parameterName) == stopAdministratorProperty && stopAdministratorValueMappings(assetSingleChoice)

    if (singleChoiceValueMappings(assetSingleChoice) || isValidStopAdminstratorValue) {
      (Nil, List(AssetProperty(singleChoiceFieldMappings(parameterName), List(PropertyValue(assetSingleChoice)))))
    } else {
      (List(parameterName), Nil)
    }
  }

  def verifyData(lon: Long, lat: Long, user: User, roadTypeLimitations: Set[AdministrativeClass]): Seq[VVHRoadlink] = {
    val closestRoadLinks = roadLinkService.getClosestRoadlinkForCarTrafficFromVVH(user, Point(lon.toLong, lat.toLong)).
      filterNot(road => roadTypeLimitations.contains(road.administrativeClass))
    if(closestRoadLinks.nonEmpty)
      Seq(closestRoadLinks.minBy(r => GeometryUtils.minimumDistance(Point(lon.toLong, lat.toLong), r.geometry)))
    else
      Seq()
  }

  private def fork(f: => Unit): Unit = {
    new Thread(new Runnable() {
      override def run(): Unit = {
        f
      }
    }).start()
  }

  def importAssets(csvReader: List[Map[String, String]], fileName: String, user: User, roadTypeLimitations: Set[AdministrativeClass]): Unit = {

    val logId = create(user.username, logInfo, fileName)
    fork {
      try {
        val result = processing(csvReader, user, roadTypeLimitations)
        result match {
          case ImportResultPointAsset(Nil, Nil, Nil, Nil, Nil) => update(logId, Status.OK)
          case _ =>
            val content = mappingContent(result)
              update(logId, Status.NotOK, Some(content))
        }
      } catch {
        case e: Exception =>
          update(logId, Status.Abend, Some("Latauksessa tapahtui odottamaton virhe: " + e.toString))
      }
    }
  }

  def processing(csvReader: List[Map[String, String]], user: User, roadTypeLimitations: Set[AdministrativeClass] = Set()): ImportResultPointAsset = {
    withDynTransaction {
      csvReader.foldLeft(ImportResultPointAsset()) {
        (result, row) =>
          val missingParameters = findMissingParameters(row)
          val (malformedParameters, properties) = assetRowToProperties(row)
          if (missingParameters.isEmpty && malformedParameters.isEmpty) {
            try {
              val excludedRows = createOrUpdate(row, roadTypeLimitations, user, properties)
              result.copy(excludedRows = excludedRows ::: result.excludedRows)
            } catch {
              case e: AssetNotFoundException => result.copy(notImportedData = NotImportedData(reason = s"Asset not found ${row("Valtakunnallinen ID").toString}", csvRow = rowToString(row)) :: result.notImportedData)
              case ex: Exception => result.copy(notImportedData = NotImportedData(reason = ex.getMessage, csvRow = rowToString(row)) :: result.notImportedData)
            }
          } else {
            result.copy(
              incompleteRows = missingParameters match {
                case Nil => result.incompleteRows
                case parameters => IncompleteRow(missingParameters = parameters, csvRow = rowToString(row)) :: result.incompleteRows
              },
              malformedRows = malformedParameters match {
                case Nil => result.malformedRows
                case parameters => MalformedRow(malformedParameters = parameters, csvRow = rowToString(row)) :: result.malformedRows
              }
            )
          }
      }
    }
  }
}

sealed trait ActionType

object ActionType {
  case object Create extends ActionType
  case object Update extends ActionType
  case object UpdatePosition extends ActionType
}

trait CsvOperations extends MassTransitStopCsvImporter {
  def is(csvRowWithHeaders: Map[String, String]): Boolean = {
    val missingOptionals = optionalMappings.keySet.diff(csvRowWithHeaders.keys.toSet).toList
    val actionParameters = optionalMappings.keySet.toList.diff(missingOptionals)
    mandatoryFieldsMapping.keys.toSeq.sorted.equals(actionParameters.sorted)
  }
}

class Updater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override def mandatoryFieldsMapping: Map[String, String] = Map(
    "Valtakunnallinen ID" -> "external_id"
  )

  override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
    val parsedRow = CsvAssetRow(externalId = Some(row("Valtakunnallinen ID").toLong), properties = properties)
    updateAsset(parsedRow.externalId.get, None, properties, roadTypeLimitations, user)
      .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
  }
}

class Creator(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override def mandatoryFieldsMapping: Map[String, String] = coordinateMappings

  override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
    val lon = row("lon").asInstanceOf[BigDecimal].toLong
    val lat = row("lat").asInstanceOf[BigDecimal].toLong

    val prop = properties.map(prop => SimpleProperty(prop.columnName, prop.value.asInstanceOf[Seq[PropertyValue]])).toSet
    val roadLink = roadLinkService.enrichRoadLinksFromVVH(verifyData(lon, lat, user, roadTypeLimitations))

    if(roadLink.isEmpty)
      List(ExcludedRow(affectedRows = "roadLink no longer available", csvRow = rowToString(row)))
    else {
      val asset = NewMassTransitStop(lon, lat, roadLink.head.linkId, 0, prop.toSeq)
      massTransitStopService.createWithUpdateFloating(asset, user.username, roadLink.head)
      List()
    }
  }
}

class PositionUpdater (roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends CsvOperations {
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def eventBus: DigiroadEventBus = eventBusImpl

  override def mandatoryFieldsMapping: Map[String, String] = coordinateMappings ++ externalIdMapping

  override def createOrUpdate(row: Map[String, String], roadTypeLimitations: Set[AdministrativeClass], user: User, properties: ParsedProperties): List[ExcludedRow] = {
    val parsedRow = CsvAssetRow(externalId = Some(row("Valtakunnallinen ID").toLong), properties = properties)

    val lon = row("lon").asInstanceOf[BigDecimal].toLong
    val lat = row("lat").asInstanceOf[BigDecimal].toLong

    val roadLink = roadLinkService.enrichRoadLinksFromVVH(verifyData(lon, lat, user, roadTypeLimitations))

    if (roadLink.isEmpty)
      List(ExcludedRow(affectedRows = "roadLink no longer available", csvRow = rowToString(row)))
    else {
      val position = Some(Position(lon, lat, roadLink.head.linkId, None))
      updateAsset(parsedRow.externalId.get, position, properties, roadTypeLimitations, user)
        .map(excludedRoadLinkType => ExcludedRow(affectedRows = excludedRoadLinkType.toString, csvRow = rowToString(row)))
    }
  }
}