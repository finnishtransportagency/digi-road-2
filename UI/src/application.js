(function(application) {
  var assetType = {
    totalWeightLimit: 30,
    trailerTruckWeightLimit: 40,
    axleWeightLimit: 50,
    bogieWeightLimit: 60,
    heightLimit: 70,
    lengthLimit: 80,
    widthLimit: 90,
    litRoad: 100,
    pavedRoad: 110,
    width: 120,
    damagedByThaw: 130,
    numberOfLanes: 140,
    congestionTendency: 150,
    massTransitLane: 160,
    trafficVolume: 170,
    winterSpeedLimit: 180,
    prohibition: 190,
    pedestrianCrossings: 200,
    hazardousMaterialTransportProhibition: 210,
    obstacles: 220,
    railwayCrossings: 230,
    directionalTrafficSigns: 240
  };

  var linearAssetSpecs = [
    {
      typeId: assetType.totalWeightLimit,
      singleElementEventCategory: 'totalWeightLimit',
      multiElementEventCategory: 'totalWeightLimits',
      layerName: 'totalWeightLimit',
      title: 'Suurin sallittu massa',
      newTitle: 'Uusi suurin sallittu massa',
      className: 'total-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: assetType.trailerTruckWeightLimit,
      singleElementEventCategory: 'trailerTruckWeightLimit',
      multiElementEventCategory: 'trailerTruckWeightLimits',
      layerName: 'trailerTruckWeightLimit',
      title: 'Yhdistelmän suurin sallittu massa',
      newTitle: 'Uusi yhdistelmän suurin sallittu massa',
      className: 'trailer-truck-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: assetType.axleWeightLimit,
      singleElementEventCategory: 'axleWeightLimit',
      multiElementEventCategory: 'axleWeightLimits',
      layerName: 'axleWeightLimit',
      title: 'Suurin sallittu akselimassa',
      newTitle: 'Uusi suurin sallittu akselimassa',
      className: 'axle-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: assetType.bogieWeightLimit,
      singleElementEventCategory: 'bogieWeightLimit',
      multiElementEventCategory: 'bogieWeightlLimits',
      layerName: 'bogieWeightLimit',
      title: 'Suurin sallittu telimassa',
      newTitle: 'Uusi suurin sallittu telimassa',
      className: 'bogie-weight-limit',
      unit: 'kg',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: assetType.heightLimit,
      singleElementEventCategory: 'heightLimit',
      multiElementEventCategory: 'heightLimits',
      layerName: 'heightLimit',
      title: 'Suurin sallittu korkeus',
      newTitle: 'Uusi suurin sallittu korkeus',
      className: 'height-limit',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: assetType.lengthLimit,
      singleElementEventCategory: 'lengthLimit',
      multiElementEventCategory: 'lengthLimits',
      layerName: 'lengthLimit',
      title: 'Suurin sallittu pituus',
      newTitle: 'Uusi pituusrajoitus',
      className: 'length-limit',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: assetType.widthLimit,
      singleElementEventCategory: 'widthLimit',
      multiElementEventCategory: 'widthLimits',
      layerName: 'widthLimit',
      title: 'Suurin sallittu leveys',
      newTitle: 'Uusi suurin sallittu leveys',
      className: 'width-limit',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: { title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta' }
    },
    {
      typeId: assetType.litRoad,
      defaultValue: 1,
      singleElementEventCategory: 'litRoad',
      multiElementEventCategory: 'litRoads',
      layerName: 'litRoad',
      title: 'Valaistus',
      newTitle: 'Uusi valaistus',
      className: 'lit-road',
      isSeparable: false,
      editControlLabels: {
        title: 'Valaistus',
        enabled: 'Valaistus',
        disabled: 'Ei valaistusta'
      }
    },
    {
      typeId: assetType.damagedByThaw,
      defaultValue: 1,
      singleElementEventCategory: 'roadDamagedByThaw',
      multiElementEventCategory: 'roadsDamagedByThaw',
      layerName: 'roadDamagedByThaw',
      title: 'Kelirikko',
      newTitle: 'Uusi kelirikko',
      className: 'road-damaged-by-thaw',
      isSeparable: false,
      editControlLabels: {
        title: 'Kelirikko',
        enabled: 'Kelirikko',
        disabled: 'Ei kelirikkoa'
      }
    },
    {
      typeId: assetType.width,
      singleElementEventCategory: 'roadWidth',
      multiElementEventCategory: 'roadWidth',
      layerName: 'roadWidth',
      title: 'Leveys',
      newTitle: 'Uusi leveys',
      className: 'road-width',
      unit: 'cm',
      isSeparable: false,
      editControlLabels: {
        title: 'Leveys',
        enabled: 'Leveys tiedossa',
        disabled: 'Leveys ei tiedossa'
      }
    },
    {
      typeId: assetType.congestionTendency,
      defaultValue: 1,
      singleElementEventCategory: 'congestionTendency',
      multiElementEventCategory: 'congestionTendencies',
      layerName: 'congestionTendency',
      title: 'Ruuhkaantumisherkkyys',
      newTitle: 'Uusi ruuhkautumisherkkä tie',
      className: 'congestion-tendency',
      isSeparable: false,
      editControlLabels: {
        title: 'Herkkyys',
        enabled: 'Ruuhkaantumisherkkä',
        disabled: 'Ei ruuhkaantumisherkkä'
      }
    },
    {
      typeId: assetType.pavedRoad,
      defaultValue: 1,
      singleElementEventCategory: 'pavedRoad',
      multiElementEventCategory: 'pavedRoads',
      layerName: 'pavedRoad',
      title: 'Päällyste',
      newTitle: 'Uusi päällyste',
      className: 'paved-road',
      isSeparable: false,
      editControlLabels: {
        title: 'Päällyste',
        enabled: 'Päällyste',
        disabled: 'Ei päällystettä'
      }
    },
    {
      typeId: assetType.trafficVolume,
      singleElementEventCategory: 'trafficVolume',
      multiElementEventCategory: 'trafficVolumes',
      layerName: 'trafficVolume',
      title: 'Liikennemäärä',
      newTitle: 'Uusi liikennemäärä',
      className: 'traffic-volume',
      unit: 'ajoneuvoa/vuorokausi',
      isSeparable: false,
      editControlLabels: {
        title: '',
        enabled: 'Liikennemäärä',
        disabled: 'Ei tiedossa'
      }
    },
    {
      typeId: assetType.massTransitLane,
      defaultValue: 1,
      singleElementEventCategory: 'massTransitLane',
      multiElementEventCategory: 'massTransitLanes',
      layerName: 'massTransitLanes',
      title: 'Joukkoliikennekaista',
      newTitle: 'Uusi joukkoliikennekaista',
      className: 'mass-transit-lane',
      isSeparable: true,
      editControlLabels: {
        title: 'Kaista',
        enabled: 'Joukkoliikennekaista',
        disabled: 'Ei joukkoliikennekaistaa'
      }
    },
    {
      typeId: assetType.winterSpeedLimit,
      singleElementEventCategory: 'winterSpeedLimit',
      multiElementEventCategory: 'winterSpeedLimits',
      layerName: 'winterSpeedLimits',
      title: 'Talvinopeusrajoitus',
      newTitle: 'Uusi talvinopeusrajoitus',
      className: 'winter-speed-limits',
      unit: 'km/h',
      isSeparable: true,
      editControlLabels: {
        title: 'Rajoitus',
        enabled: 'Talvinopeusrajoitus',
        disabled: 'Ei talvinopeusrajoitusta'
      },
      possibleValues: [100, 80, 70, 60]
    },
    {
      typeId: assetType.prohibition,
      singleElementEventCategory: 'prohibition',
      multiElementEventCategory: 'prohibitions',
      layerName: 'prohibition',
      title: 'Ajoneuvokohtaiset rajoitukset',
      newTitle: 'Uusi ajoneuvokohtainen rajoitus',
      className: 'prohibition',
      isSeparable: true,
      editControlLabels: {
        title: 'Rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta'
      }
    },
    {
      typeId: assetType.hazardousMaterialTransportProhibition,
      singleElementEventCategory: 'hazardousMaterialTransportProhibition',
      multiElementEventCategory: 'hazardousMaterialTransportProhibitions',
      layerName: 'hazardousMaterialTransportProhibition',
      title: 'VAK-rajoitus',
      newTitle: 'Uusi VAK-rajoitus',
      className: 'hazardousMaterialTransportProhibition',
      isSeparable: true,
      editControlLabels: {
        title: 'VAK-rajoitus',
        enabled: 'Rajoitus',
        disabled: 'Ei rajoitusta'
      }
    }
  ];

  var experimentalLinearAssetSpecs = [
    {
      typeId: assetType.numberOfLanes,
      singleElementEventCategory: 'laneCount',
      multiElementEventCategory: 'laneCounts',
      layerName: 'numberOfLanes',
      title: 'Kaistojen lukumäärä',
      newTitle: 'Uusi kaistojen lukumäärä',
      className: 'lane-count',
      unit: 'kpl / suunta',
      isSeparable: true,
      editControlLabels: {
        title: 'Lukumäärä',
        enabled: 'Kaistojen lukumäärä / suunta',
        disabled: 'Linkin mukainen tyypillinen kaistamäärä'
      }
    }
  ];

  var pointAssetSpecs = [
    {
      typeId: 200,
      layerName: 'pedestrianCrossings',
      title: 'Suojatie',
      newAsset: {  },
      legendValues: [
        {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Suojatie'},
        {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
      ],
      formLabels: {
        singleFloatingAssetLabel: 'suojatien',
        manyFloatingAssetsLabel: 'suojatiet',
        newAssetLabel: 'suojatie'
      }
    },
    {
      typeId: 220,
      layerName: 'obstacles',
      title: 'Esterakennelma',
      newAsset: { obstacleType: 1 },
      legendValues: [
        {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Suljettu yhteys'},
        {symbolUrl: 'images/point-assets/point_green.svg', label: 'Avattava puomi'},
        {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
      ],
      formLabels: {
        singleFloatingAssetLabel: 'esterakennelman',
        manyFloatingAssetsLabel: 'esterakennelmat',
        newAssetLabel: 'esterakennelma'
      }
    },
    {
      typeId: 230,
      layerName: 'railwayCrossings',
      title: 'Rautatien tasoristeys',
      newAsset: { safetyEquipment: 1 },
      legendValues: [
        {symbolUrl: 'images/point-assets/point_blue.svg', label: 'Rautatien tasoristeys'},
        {symbolUrl: 'images/point-assets/point_red.svg', label: 'Geometrian ulkopuolella'}
      ],
      formLabels: {
        singleFloatingAssetLabel: 'tasoristeyksen',
        manyFloatingAssetsLabel: 'tasoristeykset',
        newAssetLabel: 'tasoristeys'
      }
    },
    {
      typeId: 240,
      layerName: 'directionalTrafficSigns',
      title: 'Opastustaulu',
      newAsset: { validityDirection: 2 },
      legendValues: [
        {symbolUrl: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-directional-traffic-sign.svg', label: 'Opastustaulu'},
        {symbolUrl: 'src/resources/digiroad2/bundle/assetlayer/images/direction-arrow-warning-directional-traffic-sign.svg', label: 'Geometrian ulkopuolella'}
      ],
      formLabels: {
        singleFloatingAssetLabel: 'opastustaulun',
        manyFloatingAssetsLabel: 'opastustaulut',
        newAssetLabel: 'opastustaulu'
      }
    }
  ];

  var localizedStrings;
  var assetUpdateFailedMessage = 'Tallennus epäonnistui. Yritä hetken kuluttua uudestaan.';

  var indicatorOverlay = function() {
    jQuery('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
  };

  var bindEvents = function(linearAssetSpecs, pointAssetSpecs) {
    var singleElementEventNames = _.pluck(linearAssetSpecs, 'singleElementEventCategory');
    var multiElementEventNames = _.pluck(linearAssetSpecs, 'multiElementEventCategory');
    var linearAssetSavingEvents = _.map(singleElementEventNames, function(name) { return name + ':saving'; }).join(' ');
    var pointAssetSavingEvents = _.map(pointAssetSpecs, function (spec) { return spec.layerName + ':saving'; }).join(' ');
    eventbus.on('asset:saving asset:creating speedLimit:saving linkProperties:saving manoeuvres:saving ' + linearAssetSavingEvents + ' ' + pointAssetSavingEvents, function() {
      indicatorOverlay();
    });

    var fetchedEventNames = _.map(multiElementEventNames, function(name) { return name + ':fetched'; }).join(' ');
    eventbus.on('asset:fetched asset:created speedLimits:fetched linkProperties:available manoeuvres:fetched pointAssets:fetched ' + fetchedEventNames, function() {
      jQuery('.spinner-overlay').remove();
    });

    var pointAssetSaved = _.map(pointAssetSpecs, function (spec) { return spec.layerName + ':saved'; }).join(' ');
    var linearAssetsSaved = _.map(singleElementEventNames, function(name) { return name + ':saved'; }).join(' ');

    eventbus.on('asset:saved speedLimit:saved linkProperties:saved manoeuvres:saved ' + linearAssetsSaved + ' ' + pointAssetSaved, function() {
      jQuery('.spinner-overlay').remove();
    });
    var massUpdateFailedEventNames = _.map(multiElementEventNames, function(name) { return name + ':massUpdateFailed'; }).join(' ');
    eventbus.on('asset:updateFailed asset:creationFailed linkProperties:updateFailed speedLimits:massUpdateFailed ' + massUpdateFailedEventNames, function() {
      jQuery('.spinner-overlay').remove();
      alert(assetUpdateFailedMessage);
    });

    eventbus.on('confirm:show', function() { new Confirm(); });
  };

  var createOpenLayersMap = function(startupParameters) {
    var map = new OpenLayers.Map({
      controls: [],
      units: 'm',
      maxExtent: new OpenLayers.Bounds(-548576, 6291456, 1548576, 8388608),
      resolutions: [2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25, 0.125, 0.0625],
      projection: 'EPSG:3067',
      isBaseLayer: true,
      center: new OpenLayers.LonLat(startupParameters.lon, startupParameters.lat),
      fallThrough: true,
      theme: null,
      zoomMethod: null
    });
    var base = new OpenLayers.Layer("BaseLayer", {
      layerId: 0,
      isBaseLayer: true,
      displayInLayerSwitcher: false
    });
    map.addLayer(base);
    map.render('mapdiv');
    map.zoomTo(startupParameters.zoom);
    return map;
  };

  var setupMap = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters) {
    var map = createOpenLayersMap(startupParameters);

    var NavigationControl = OpenLayers.Class(OpenLayers.Control.Navigation, {
      wheelDown: function(evt, delta) {
        if (applicationModel.canZoomOut()) {
          return OpenLayers.Control.Navigation.prototype.wheelDown.apply(this,arguments);
        } else {
          new Confirm();
        }
      }
    });

    map.addControl(new NavigationControl());

    var mapOverlay = new MapOverlay($('.container'));

    if (withTileMaps) { new TileMapCollection(map); }
    var roadLayer = new RoadLayer(map, models.roadCollection);

    new LinkPropertyForm(models.selectedLinkProperty);
    new ManoeuvreForm(models.selectedManoeuvreSource);
    _.forEach(linearAssets, function(linearAsset) {
      LinearAssetForm.initialize(
        linearAsset.selectedLinearAsset,
        linearAsset.singleElementEventCategory,
        AssetFormElementsFactory.construct(linearAsset),
        linearAsset.newTitle,
        linearAsset.title);
    });

    _.forEach(pointAssets, function(pointAsset) {
      PointAssetForm.initialize(pointAsset.selectedPointAsset, pointAsset.layerName, pointAsset.formLabels);
    });

    var linearAssetLayers = _.reduce(linearAssets, function(acc, asset) {
      acc[asset.layerName] = new LinearAssetLayer({
        map: map,
        application: applicationModel,
        collection: asset.collection,
        selectedLinearAsset: asset.selectedLinearAsset,
        roadCollection: models.roadCollection,
        geometryUtils: new GeometryUtils(),
        roadLayer: roadLayer,
        layerName: asset.layerName,
        multiElementEventCategory: asset.multiElementEventCategory,
        singleElementEventCategory: asset.singleElementEventCategory,
        style: PiecewiseLinearAssetStyle(applicationModel),
        formElements: AssetFormElementsFactory.construct(asset)
      });
      return acc;
    }, {});

    var pointAssetLayers = _.reduce(pointAssets, function(acc, asset) {
      acc[asset.layerName] = new PointAssetLayer({
        roadLayer: roadLayer,
        roadCollection: models.roadCollection,
        collection: asset.collection,
        map: map,
        selectedAsset: asset.selectedPointAsset,
        style: PointAssetStyle(asset.layerName),
        mapOverlay: mapOverlay,
        layerName: asset.layerName,
        newAsset: asset.newAsset
      });
      return acc;
    }, {});

    var layers = _.merge({
      road: roadLayer,
      linkProperty: new LinkPropertyLayer(map, roadLayer, new GeometryUtils(), models.selectedLinkProperty, models.roadCollection, models.linkPropertiesModel, applicationModel),
      massTransitStop: new AssetLayer(map, models.roadCollection, mapOverlay, new AssetGrouping(applicationModel), roadLayer),
      speedLimit: new SpeedLimitLayer({
        map: map,
        application: applicationModel,
        collection: models.speedLimitsCollection,
        selectedSpeedLimit: models.selectedSpeedLimit,
        geometryUtils: new GeometryUtils(),
        linearAsset: LinearAsset(),
        backend: backend,
        roadLayer: roadLayer
      }),
      manoeuvre: new ManoeuvreLayer(applicationModel, map, roadLayer, new GeometryUtils(), models.selectedManoeuvreSource, models.manoeuvresCollection, models.roadCollection)
    }, linearAssetLayers, pointAssetLayers);

    var mapPluginsContainer = $('#map-plugins');
    new ScaleBar(map, mapPluginsContainer);
    new TileMapSelector(mapPluginsContainer);
    new ZoomBox(map, mapPluginsContainer);
    new CoordinatesDisplay(map, mapPluginsContainer);

    new MapView(map, layers, new InstructionsPopup($('.digiroad2')));

    applicationModel.moveMap(map.getZoom(), map.getExtent());

    return map;
  };

  var setupProjections = function() {
    proj4.defs('EPSG:3067', '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs');
  };

  var startApplication = function(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters) {
    if (localizedStrings) {
      setupProjections();
      var map = setupMap(backend, models, linearAssets, pointAssets, withTileMaps, startupParameters);
      var selectedPedestrianCrossing = getSelectedPointAsset(pointAssets, 'pedestrianCrossings');
      var selectedObstacle = getSelectedPointAsset(pointAssets, 'obstacles');
      var selectedRailwayCrossing =  getSelectedPointAsset(pointAssets, 'railwayCrossings');
      var selectedDirectionalTrafficSign = getSelectedPointAsset(pointAssets, 'directionalTrafficSigns');
      new URLRouter(map, backend, _.merge({}, models,
          { selectedPedestrianCrossing: selectedPedestrianCrossing },
          { selectedObstacle: selectedObstacle },
          { selectedRailwayCrossing: selectedRailwayCrossing },
          { selectedDirectionalTrafficSign: selectedDirectionalTrafficSign  }
      ));
      eventbus.trigger('application:initialized');
    }
  };

  function getSelectedPointAsset(pointAssets, layerName) {
    return _(pointAssets).find({ layerName: layerName }).selectedPointAsset;
  }

  application.start = function(customBackend, withTileMaps, isExperimental) {
    var backend = customBackend || new Backend();
    var tileMaps = _.isUndefined(withTileMaps) ?  true : withTileMaps;
    var roadCollection = new RoadCollection(backend);
    var speedLimitsCollection = new SpeedLimitsCollection(backend);
    var selectedSpeedLimit = new SelectedSpeedLimit(backend, speedLimitsCollection);
    var selectedLinkProperty = new SelectedLinkProperty(backend, roadCollection);
    var linkPropertiesModel = new LinkPropertiesModel();
    var manoeuvresCollection = new ManoeuvresCollection(backend, roadCollection);
    var selectedManoeuvreSource = new SelectedManoeuvreSource(manoeuvresCollection);
    var instructionsPopup = new InstructionsPopup($('.digiroad2'));
    var enabledExperimentalAssets = isExperimental ? experimentalLinearAssetSpecs : [];
    var enabledLinearAssetSpecs = linearAssetSpecs.concat(enabledExperimentalAssets);
    var linearAssets = _.map(enabledLinearAssetSpecs, function(spec) {
      var collection = new LinearAssetsCollection(backend, spec.typeId, spec.singleElementEventCategory, spec.multiElementEventCategory);
      var selectedLinearAsset = SelectedLinearAssetFactory.construct(backend, collection, spec);
      return _.merge({}, spec, {
        collection: collection,
        selectedLinearAsset: selectedLinearAsset
      });
    });
    var pointAssets = _.map(pointAssetSpecs, function(spec) {
      var collection = new PointAssetsCollection(backend, spec.layerName);
      var selectedPointAsset = new SelectedPointAsset(backend, spec.layerName);
      return _.merge({}, spec, {
        collection: collection,
        selectedPointAsset: selectedPointAsset
      });
    });

    var selectedMassTransitStopModel = SelectedAssetModel.initialize(backend);
    var models = {
      roadCollection: roadCollection,
      speedLimitsCollection: speedLimitsCollection,
      selectedSpeedLimit: selectedSpeedLimit,
      selectedLinkProperty: selectedLinkProperty,
      selectedManoeuvreSource: selectedManoeuvreSource,
      selectedMassTransitStopModel: selectedMassTransitStopModel,
      linkPropertiesModel: linkPropertiesModel,
      manoeuvresCollection: manoeuvresCollection
    };

    bindEvents(enabledLinearAssetSpecs, pointAssetSpecs);
    window.assetsModel = new AssetsModel(backend);
    window.selectedAssetModel = selectedMassTransitStopModel;
    var selectedLinearAssetModels = _.pluck(linearAssets, "selectedLinearAsset");
    var selectedPointAssetModels = _.pluck(pointAssets, "selectedPointAsset");
    window.applicationModel = new ApplicationModel([
      selectedAssetModel,
      selectedSpeedLimit,
      selectedLinkProperty,
      selectedManoeuvreSource]
        .concat(selectedLinearAssetModels)
        .concat(selectedPointAssetModels));

    EditModeDisclaimer.initialize(instructionsPopup);

    var assetGroups = groupAssets(linearAssets,
                                  pointAssets,
                                  linkPropertiesModel,
                                  selectedSpeedLimit,
                                  selectedMassTransitStopModel);

    var assetSelectionMenu = AssetSelectionMenu(assetGroups, {
      onSelect: function(layerName) {
        window.location.hash = layerName;
      }
    });

    eventbus.on('layer:selected', function(layer) {
      assetSelectionMenu.select(layer);
    });

    NavigationPanel.initialize(
      $('#map-tools'),
      new SearchBox(
        instructionsPopup,
        new LocationSearch(backend, window.applicationModel, new GeometryUtils())
      ),
      new LayerSelectBox(assetSelectionMenu),
      assetGroups
    );

    AssetForm.initialize(backend);
    SpeedLimitForm.initialize(selectedSpeedLimit);
    WorkListView.initialize(backend);
    backend.getUserRoles();
    backend.getStartupParametersWithCallback(function(startupParameters) {
      backend.getAssetPropertyNamesWithCallback(function(assetPropertyNames) {
        localizedStrings = assetPropertyNames;
        window.localizedStrings = assetPropertyNames;
        startApplication(backend, models, linearAssets, pointAssets, tileMaps, startupParameters);
      });
    });
  };

  function groupAssets(linearAssets,
                       pointAssets,
                       linkPropertiesModel,
                       selectedSpeedLimit,
                       selectedMassTransitStopModel) {
    var roadLinkBox = new RoadLinkBox(linkPropertiesModel);
    var massTransitBox = new ActionPanelBoxes.AssetBox(selectedMassTransitStopModel);
    var speedLimitBox = new ActionPanelBoxes.SpeedLimitBox(selectedSpeedLimit);
    var manoeuvreBox = new ManoeuvreBox();

    return [
      [roadLinkBox],
      [].concat(getLinearAsset(assetType.litRoad))
        .concat(getLinearAsset(assetType.pavedRoad))
        .concat(getLinearAsset(assetType.width))
        .concat(getLinearAsset(assetType.numberOfLanes))
        .concat(getLinearAsset(assetType.massTransitLane)),
      [speedLimitBox]
        .concat(getLinearAsset(assetType.winterSpeedLimit)),
      [massTransitBox]
        .concat(getPointAsset(assetType.obstacles))
        .concat(getPointAsset(assetType.railwayCrossings))
        .concat(getPointAsset(assetType.directionalTrafficSigns))
        .concat(getPointAsset(assetType.pedestrianCrossings)),
      [].concat(getLinearAsset(assetType.trafficVolume))
        .concat(getLinearAsset(assetType.congestionTendency))
        .concat(getLinearAsset(assetType.damagedByThaw)),
      [manoeuvreBox]
        .concat(getLinearAsset(assetType.prohibition))
        .concat(getLinearAsset(assetType.hazardousMaterialTransportProhibition))
        .concat(getLinearAsset(assetType.totalWeightLimit))
        .concat(getLinearAsset(assetType.trailerTruckWeightLimit))
        .concat(getLinearAsset(assetType.axleWeightLimit))
        .concat(getLinearAsset(assetType.bogieWeightLimit))
        .concat(getLinearAsset(assetType.heightLimit))
        .concat(getLinearAsset(assetType.lengthLimit))
        .concat(getLinearAsset(assetType.widthLimit))
    ];

    function getLinearAsset(typeId) {
      var asset = _.find(linearAssets, {typeId: typeId});
      if (asset) {
        var legendValues = [asset.editControlLabels.disabled, asset.editControlLabels.enabled];
        return [new LinearAssetBox(asset.selectedLinearAsset, asset.layerName, asset.title, asset.className, legendValues)];
      }
      return [];
    }

    function getPointAsset(typeId) {
      var asset = _.find(pointAssets, {typeId: typeId});
      if (asset) {
        return [PointAssetBox(asset.selectedPointAsset, asset.title, asset.layerName, asset.legendValues)];
      }
      return [];
    }
  }

  application.restart = function(backend, withTileMaps) {
    localizedStrings = undefined;
    this.start(backend, withTileMaps);
  };

}(window.Application = window.Application || {}));
